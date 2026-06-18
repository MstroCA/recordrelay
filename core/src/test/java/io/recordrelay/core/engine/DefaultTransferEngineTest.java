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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.recordrelay.core.domain.ColumnMapping;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.domain.TransferJob;
import io.recordrelay.core.domain.TransferMode;
import io.recordrelay.core.domain.TransferStatus;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.DataSourceConnector;
import io.recordrelay.core.port.out.RecordReader;
import io.recordrelay.core.port.out.RecordWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultTransferEngineTest {

  @Mock private DataSourceConnector connector;
  @Mock private RecordReader reader;
  @Mock private RecordWriter writer;

  private TransferJob job;

  @BeforeEach
  void setUp() {
    var src = new TableRef(new DatabaseRef("db", DatabaseType.POSTGRESQL), "public", "src");
    var tgt = new TableRef(new DatabaseRef("db", DatabaseType.POSTGRESQL), "public", "tgt");
    var mapping =
        new MappingDefinition("m1", src, tgt, List.of(new ColumnMapping("id", "id")), null, null);
    var profile =
        new ConnectionProfile(
            "p",
            "p",
            "test",
            DatabaseType.POSTGRESQL,
            "localhost",
            5432,
            "db",
            new Credentials("u", "pw"),
            Map.of());
    job =
        new TransferJob("j1", "test", profile, profile, mapping, TransferMode.SYNC, 10, null, null);
  }

  @Test
  void successfulTransferWithSyncPipeline() throws Exception {
    when(connector.createReader()).thenReturn(reader);
    when(connector.createWriter()).thenReturn(writer);
    when(reader.hasMore()).thenReturn(true, false);
    when(reader.readNext()).thenReturn(Optional.of(DataRecord.of(Map.of("id", 1))));

    var engine = new DefaultTransferEngine(profile -> connector, new SyncPipeline(), null);
    var result = engine.transfer(job);

    assertThat(result.status()).isEqualTo(TransferStatus.SUCCESS);
    assertThat(result.transferredCount()).isEqualTo(1L);
  }

  @Test
  void connectorExceptionProducesFailedResult() throws Exception {
    when(connector.createReader()).thenThrow(new ConnectorException("cannot connect"));

    var engine = new DefaultTransferEngine(profile -> connector, new SyncPipeline(), null);
    var result = engine.transfer(job);

    assertThat(result.status()).isEqualTo(TransferStatus.FAILED);
    assertThat(result.errors()).isNotEmpty();
  }

  @Test
  void transferWithNoopListenerSucceeds() throws Exception {
    when(connector.createReader()).thenReturn(reader);
    when(connector.createWriter()).thenReturn(writer);
    when(reader.hasMore()).thenReturn(false);

    var engine = new DefaultTransferEngine(profile -> connector, new SyncPipeline(), null);
    var result = engine.transfer(job);

    assertThat(result.status()).isEqualTo(TransferStatus.SUCCESS);
    assertThat(result.transferredCount()).isEqualTo(0L);
  }

  @Test
  void batchModeWithNoBatchPipelineFallsBackToSync() throws Exception {
    var batchJob =
        new TransferJob(
            "j2",
            "batch",
            job.source(),
            job.target(),
            job.mapping(),
            TransferMode.BATCH,
            10,
            null,
            null);
    when(connector.createReader()).thenReturn(reader);
    when(connector.createWriter()).thenReturn(writer);
    when(reader.hasMore()).thenReturn(false);

    var engine = new DefaultTransferEngine(profile -> connector, new SyncPipeline(), null);
    var result = engine.transfer(batchJob);

    assertThat(result.status()).isEqualTo(TransferStatus.SUCCESS);
  }

  @Test
  void choosesProvidedBatchPipelineForBatchMode() throws Exception {
    var batchJob =
        new TransferJob(
            "j3",
            "batch",
            job.source(),
            job.target(),
            job.mapping(),
            TransferMode.BATCH,
            10,
            null,
            null);
    when(connector.createReader()).thenReturn(reader);
    when(connector.createWriter()).thenReturn(writer);

    var mockBatch = mock(io.recordrelay.core.port.out.TransferPipeline.class);
    when(mockBatch.supports(TransferMode.BATCH)).thenReturn(true);
    var expectedResult =
        io.recordrelay.core.domain.TransferResult.success("j3", 0L, java.time.Duration.ZERO);
    when(mockBatch.execute(any(), any(), any(), any(), any())).thenReturn(expectedResult);

    var engine = new DefaultTransferEngine(profile -> connector, new SyncPipeline(), mockBatch);
    var result = engine.transfer(batchJob);

    assertThat(result).isSameAs(expectedResult);
  }
}
