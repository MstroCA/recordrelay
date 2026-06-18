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

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.TransferError;
import io.recordrelay.core.domain.TransferJob;
import io.recordrelay.core.domain.TransferMode;
import io.recordrelay.core.domain.TransferResult;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.in.TransferUseCase;
import io.recordrelay.core.port.out.DataSourceConnector;
import io.recordrelay.core.port.out.TransferPipeline;
import io.recordrelay.core.port.out.TransferProgressListener;
import io.recordrelay.core.spi.ConnectorRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production implementation of {@link TransferUseCase}.
 *
 * <p>Resolves source and target connectors from {@link ConnectorRegistry}, opens reader and writer,
 * then delegates execution to the appropriate {@link TransferPipeline} based on the job's {@link
 * TransferMode}.
 *
 * <p>A {@code SyncPipeline} must always be provided. The {@code batchPipeline} parameter is
 * optional (may be {@code null}); if {@link TransferMode#BATCH} is requested without a batch
 * pipeline, the engine falls back to the sync pipeline.
 */
public final class DefaultTransferEngine implements TransferUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultTransferEngine.class);

  private final Function<ConnectionProfile, DataSourceConnector> connectorLookup;
  private final TransferPipeline syncPipeline;
  private final TransferPipeline batchPipeline;

  /**
   * Creates the engine with both pipeline implementations.
   *
   * @param syncPipeline handles SYNC and ASYNC modes (required)
   * @param batchPipeline handles BATCH mode; may be {@code null} to fall back to sync
   */
  public DefaultTransferEngine(TransferPipeline syncPipeline, TransferPipeline batchPipeline) {
    this(ConnectorRegistry::findConnector, syncPipeline, batchPipeline);
  }

  /** Convenience constructor when only sync transfers are needed. */
  public DefaultTransferEngine(TransferPipeline syncPipeline) {
    this(syncPipeline, null);
  }

  /** Package-private constructor for unit testing — allows injecting a custom connector lookup. */
  DefaultTransferEngine(
      Function<ConnectionProfile, DataSourceConnector> connectorLookup,
      TransferPipeline syncPipeline,
      TransferPipeline batchPipeline) {
    this.connectorLookup = Objects.requireNonNull(connectorLookup, "connectorLookup");
    this.syncPipeline = Objects.requireNonNull(syncPipeline, "syncPipeline");
    this.batchPipeline = batchPipeline;
  }

  @Override
  public TransferResult transfer(TransferJob job) {
    return transfer(job, TransferProgressListener.noop());
  }

  @Override
  public TransferResult transfer(TransferJob job, TransferProgressListener listener) {
    Objects.requireNonNull(job, "job");
    Objects.requireNonNull(listener, "listener");

    var start = Instant.now();
    LOG.info(
        "Starting transfer job '{}' (mode={}, source={}, target={})",
        job.id(),
        job.mode(),
        job.source().type(),
        job.target().type());

    var sourceConnector = connectorLookup.apply(job.source());
    var targetConnector = connectorLookup.apply(job.target());

    try (var reader = sourceConnector.createReader();
        var writer = targetConnector.createWriter()) {

      reader.open(job.source(), job.mapping().source(), job.mapping());
      writer.open(job.target(), job.mapping().target(), job.mapping());

      var transformer = new MappingTransformer(job.mapping(), job.options());
      var pipeline = choosePipeline(job.mode());

      return pipeline.execute(job, reader, transformer, writer, listener);

    } catch (ConnectorException e) {
      var duration = Duration.between(start, Instant.now());
      var result =
          TransferResult.failed(
              job.id(),
              duration,
              List.of(
                  TransferError.batchError(e.getMessage(), job.mapping().target().tableName())));
      listener.onError(job, e);
      LOG.error("Transfer job '{}' failed: {}", job.id(), e.getMessage(), e);
      return result;
    }
  }

  private TransferPipeline choosePipeline(TransferMode mode) {
    if (mode == TransferMode.BATCH && batchPipeline != null && batchPipeline.supports(mode)) {
      return batchPipeline;
    }
    return syncPipeline;
  }
}
