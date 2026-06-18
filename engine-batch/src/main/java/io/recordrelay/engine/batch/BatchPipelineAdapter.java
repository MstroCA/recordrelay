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
package io.recordrelay.engine.batch;

import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.TransferError;
import io.recordrelay.core.domain.TransferJob;
import io.recordrelay.core.domain.TransferMode;
import io.recordrelay.core.domain.TransferResult;
import io.recordrelay.core.domain.TransferStatus;
import io.recordrelay.core.port.out.RecordReader;
import io.recordrelay.core.port.out.RecordTransformer;
import io.recordrelay.core.port.out.RecordWriter;
import io.recordrelay.core.port.out.TransferPipeline;
import io.recordrelay.core.port.out.TransferProgressListener;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.support.JdbcTransactionManager;

/**
 * {@link TransferPipeline} implementation that delegates to Spring Batch.
 *
 * <p>Uses an embedded H2 database as the {@link JobRepository}, enabling job restart when the same
 * adapter instance is reused across calls with the same job ID. For true persistent restarts across
 * process restarts, replace the H2 datasource with a file-based or external DB.
 *
 * <p>This class is {@link AutoCloseable}: the embedded H2 database is shut down when {@link
 * #close()} is called.
 */
public final class BatchPipelineAdapter implements TransferPipeline, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(BatchPipelineAdapter.class);

  private final DataSource metaDb;
  private final JobRepository jobRepository;
  private final JdbcTransactionManager txManager;
  private final JobLauncher jobLauncher;

  /**
   * Creates the adapter with a fresh, anonymous in-memory H2 job repository.
   *
   * @throws Exception if the job repository cannot be initialised
   */
  public BatchPipelineAdapter() throws Exception {
    this(
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .generateUniqueName(true)
            .addScript("classpath:org/springframework/batch/core/schema-h2.sql")
            .build());
  }

  /**
   * Creates the adapter with an externally supplied datasource as the job repository.
   *
   * <p>Use this constructor to provide a file-based H2 or a shared RDBMS for persistent restart
   * support across JVM restarts.
   *
   * @param metaDataSource the datasource that hosts Spring Batch meta-tables
   * @throws Exception if the job repository cannot be initialised
   */
  public BatchPipelineAdapter(DataSource metaDataSource) throws Exception {
    this.metaDb = Objects.requireNonNull(metaDataSource, "metaDataSource");
    this.txManager = new JdbcTransactionManager(metaDb);

    var repoFactory = new JobRepositoryFactoryBean();
    repoFactory.setDataSource(metaDb);
    repoFactory.setTransactionManager(txManager);
    repoFactory.afterPropertiesSet();
    this.jobRepository = repoFactory.getObject();

    var launcher = new TaskExecutorJobLauncher();
    launcher.setJobRepository(jobRepository);
    launcher.afterPropertiesSet();
    this.jobLauncher = launcher;

    LOG.debug("BatchPipelineAdapter initialised");
  }

  @Override
  public boolean supports(TransferMode mode) {
    return mode == TransferMode.BATCH;
  }

  @Override
  public TransferResult execute(
      TransferJob job,
      RecordReader reader,
      RecordTransformer transformer,
      RecordWriter writer,
      TransferProgressListener listener) {

    var start = Instant.now();
    listener.onStart(job);
    var batchJob = buildJob(job, reader, transformer, writer);
    var params = new JobParametersBuilder().addString("jobId", job.id()).toJobParameters();

    try {
      var execution = jobLauncher.run(batchJob, params);
      return interpretExecution(job, execution, start, listener);
    } catch (Exception e) {
      var result = buildErrorResult(job, start, e);
      listener.onError(job, e instanceof RuntimeException re ? re : new RuntimeException(e));
      LOG.error("Batch job '{}' threw exception: {}", job.id(), e.getMessage(), e);
      return result;
    }
  }

  private Job buildJob(
      TransferJob job, RecordReader reader, RecordTransformer transformer, RecordWriter writer) {
    var step =
        new StepBuilder("transfer-step", jobRepository)
            .<DataRecord, DataRecord>chunk(job.batchSize(), txManager)
            .reader(new RecordRelayItemReader(reader))
            .processor(new RecordRelayItemProcessor(transformer))
            .writer(new RecordRelayItemWriter(writer))
            .build();
    return new JobBuilder("recordrelay-" + job.id(), jobRepository).start(step).build();
  }

  private TransferResult interpretExecution(
      TransferJob job,
      org.springframework.batch.core.JobExecution execution,
      Instant start,
      TransferProgressListener listener) {
    var duration = Duration.between(start, Instant.now());
    long written = execution.getStepExecutions().stream().mapToLong(se -> se.getWriteCount()).sum();
    if (execution.getStatus() == BatchStatus.COMPLETED) {
      var result = TransferResult.success(job.id(), written, duration);
      listener.onComplete(result);
      LOG.info("Batch job '{}' completed: {} records written", job.id(), written);
      return result;
    }
    var errors =
        execution.getAllFailureExceptions().stream()
            .map(t -> TransferError.batchError(t.getMessage(), job.mapping().target().tableName()))
            .collect(Collectors.toList());
    long failed =
        execution.getStepExecutions().stream()
            .mapToLong(se -> se.getProcessSkipCount() + se.getWriteSkipCount())
            .sum();
    var result =
        new TransferResult(job.id(), TransferStatus.FAILED, written, failed, duration, errors);
    listener.onError(job, new RuntimeException("Batch job failed: " + execution.getStatus()));
    return result;
  }

  private TransferResult buildErrorResult(TransferJob job, Instant start, Exception e) {
    return TransferResult.failed(
        job.id(),
        Duration.between(start, Instant.now()),
        List.of(TransferError.batchError(e.getMessage(), job.mapping().target().tableName())));
  }

  @Override
  public void close() {
    if (metaDb instanceof org.springframework.jdbc.datasource.embedded.EmbeddedDatabase edb) {
      edb.shutdown();
      LOG.debug("BatchPipelineAdapter meta-database shut down");
    }
  }
}
