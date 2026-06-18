/*
 * Copyright 2026 the RecordRelay authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.recordrelay.core.engine;

import io.recordrelay.core.domain.TransferError;
import io.recordrelay.core.domain.TransferJob;
import io.recordrelay.core.domain.TransferMode;
import io.recordrelay.core.domain.TransferOptions;
import io.recordrelay.core.domain.TransferResult;
import io.recordrelay.core.domain.TransferStatus;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordReader;
import io.recordrelay.core.port.out.RecordTransformer;
import io.recordrelay.core.port.out.RecordWriter;
import io.recordrelay.core.port.out.TransferPipeline;
import io.recordrelay.core.port.out.TransferProgressListener;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-process, single-threaded read→transform→write loop.
 *
 * <p>Supports {@link TransferMode#SYNC} and {@link TransferMode#ASYNC}. For chunk-oriented
 * processing with restart capability use the {@code BatchPipelineAdapter} from {@code
 * engine-batch}.
 */
public final class SyncPipeline implements TransferPipeline {

  private static final Logger LOG = LoggerFactory.getLogger(SyncPipeline.class);
  private static final long PROGRESS_INTERVAL = 1_000L;

  @Override
  public boolean supports(TransferMode mode) {
    return mode == TransferMode.SYNC || mode == TransferMode.ASYNC;
  }

  @Override
  public TransferResult execute(
      TransferJob job,
      RecordReader reader,
      RecordTransformer transformer,
      RecordWriter writer,
      TransferProgressListener listener) {

    var targetTable = job.mapping().target().tableName();
    var start = Instant.now();
    var errors = new ArrayList<TransferError>();
    listener.onStart(job);

    try {
      var transferred =
          processRows(job, reader, transformer, writer, listener, errors, targetTable);
      writer.flush();
      var result = buildSuccessResult(job, transferred, errors, start);
      listener.onComplete(result);
      LOG.info(
          "SyncPipeline completed: {} transferred, {} skipped for job '{}'",
          transferred,
          errors.size(),
          job.id());
      return result;
    } catch (ConnectorException e) {
      var result = buildFailureResult(job, errors, targetTable, start, e);
      listener.onError(job, e);
      LOG.error("SyncPipeline failed for job '{}': {}", job.id(), e.getMessage(), e);
      return result;
    }
  }

  private long processRows(
      TransferJob job,
      RecordReader reader,
      RecordTransformer transformer,
      RecordWriter writer,
      TransferProgressListener listener,
      List<TransferError> errors,
      String targetTable)
      throws ConnectorException {
    var options = job.options();
    var deadLetter = new DeadLetterCollector(options.deadLetterPath());
    long transferred = 0L;
    long rowNum = 0L;
    while (reader.hasMore()) {
      var maybeSource = reader.readNext();
      if (maybeSource.isEmpty()) {
        break;
      }
      rowNum++;
      try {
        writer.write(transformer.transform(maybeSource.get()));
        transferred++;
        if (transferred % PROGRESS_INTERVAL == 0) {
          listener.onProgress(job, transferred, -1L);
        }
      } catch (SkipRowException e) {
        handleSkip(maybeSource.get(), e, deadLetter, errors, targetTable, rowNum, options);
      }
    }
    return transferred;
  }

  private void handleSkip(
      io.recordrelay.core.domain.DataRecord source,
      SkipRowException e,
      DeadLetterCollector deadLetter,
      List<TransferError> errors,
      String targetTable,
      long rowNum,
      TransferOptions options)
      throws ConnectorException {
    deadLetter.collect(source, e.getMessage(), targetTable, rowNum);
    errors.add(new TransferError(e.getMessage(), targetTable, rowNum));
    LOG.debug("Skipped row {} in '{}': {}", rowNum, targetTable, e.getMessage());
    if (options.skipLimit() > 0 && deadLetter.size() > options.skipLimit()) {
      throw new ConnectorException(
          "Skip limit exceeded (" + options.skipLimit() + " rows). Last error: " + e.getMessage());
    }
  }

  private TransferResult buildSuccessResult(
      TransferJob job, long transferred, List<TransferError> errors, Instant start) {
    var duration = Duration.between(start, Instant.now());
    if (errors.isEmpty()) {
      return TransferResult.success(job.id(), transferred, duration);
    }
    return new TransferResult(
        job.id(), TransferStatus.PARTIAL, transferred, errors.size(), duration, errors);
  }

  private TransferResult buildFailureResult(
      TransferJob job,
      List<TransferError> errors,
      String targetTable,
      Instant start,
      ConnectorException e) {
    errors.add(TransferError.batchError(e.getMessage(), targetTable));
    return TransferResult.failed(
        job.id(), Duration.between(start, Instant.now()), List.copyOf(errors));
  }
}
