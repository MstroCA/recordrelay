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
import static org.mockito.Mockito.verify;
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
import io.recordrelay.core.port.out.RecordReader;
import io.recordrelay.core.port.out.RecordTransformer;
import io.recordrelay.core.port.out.RecordWriter;
import io.recordrelay.core.port.out.TransferProgressListener;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncPipelineTest {

  @Mock private RecordReader reader;
  @Mock private RecordWriter writer;
  @Mock private TransferProgressListener listener;

  private SyncPipeline pipeline;
  private TransferJob job;

  @BeforeEach
  void setUp() {
    pipeline = new SyncPipeline();
    var src = new TableRef(new DatabaseRef("srcDb", DatabaseType.POSTGRESQL), "public", "orders");
    var tgt = new TableRef(new DatabaseRef("tgtDb", DatabaseType.POSTGRESQL), "public", "orders");
    var mapping =
        new MappingDefinition("m1", src, tgt, List.of(new ColumnMapping("id", "id")), null, null);
    var profile =
        new ConnectionProfile(
            "p1",
            "p1",
            "test",
            DatabaseType.POSTGRESQL,
            "localhost",
            5432,
            "db",
            new Credentials("u", "pw"),
            Map.of());
    job =
        new TransferJob(
            "j1", "test", profile, profile, mapping, TransferMode.SYNC, 100, null, null);
  }

  @Test
  void supportsSyncAndAsyncModes() {
    assertThat(pipeline.supports(TransferMode.SYNC)).isTrue();
    assertThat(pipeline.supports(TransferMode.ASYNC)).isTrue();
    assertThat(pipeline.supports(TransferMode.BATCH)).isFalse();
  }

  @Test
  void singleRecordTransferReturnsSuccess() throws Exception {
    var record = DataRecord.of(Map.of("id", 1));
    when(reader.hasMore()).thenReturn(true, false);
    when(reader.readNext()).thenReturn(Optional.of(record));
    RecordTransformer transformer = source -> source;

    var result = pipeline.execute(job, reader, transformer, writer, listener);

    assertThat(result.status()).isEqualTo(TransferStatus.SUCCESS);
    assertThat(result.transferredCount()).isEqualTo(1L);
    assertThat(result.failedCount()).isEqualTo(0L);
    verify(listener).onStart(job);
    verify(writer).write(record);
    verify(writer).flush();
    verify(listener).onComplete(result);
  }

  @Test
  void emptySourceReturnsSuccessWithZeroCount() throws Exception {
    when(reader.hasMore()).thenReturn(false);
    RecordTransformer transformer = source -> source;

    var result = pipeline.execute(job, reader, transformer, writer, listener);

    assertThat(result.status()).isEqualTo(TransferStatus.SUCCESS);
    assertThat(result.transferredCount()).isEqualTo(0L);
  }

  @Test
  void readerExceptionReturnsFailedResult() throws Exception {
    when(reader.hasMore()).thenThrow(new ConnectorException("DB error"));
    RecordTransformer transformer = source -> source;

    var result = pipeline.execute(job, reader, transformer, writer, listener);

    assertThat(result.status()).isEqualTo(TransferStatus.FAILED);
    assertThat(result.errors()).isNotEmpty();
    verify(listener).onError(any(), any());
  }

  @Test
  void transformerThrowingConnectorExceptionAbortsTransfer() throws Exception {
    var record = DataRecord.of(Map.of("id", 1));
    when(reader.hasMore()).thenReturn(true, false);
    when(reader.readNext()).thenReturn(Optional.of(record));
    RecordTransformer transformer =
        source -> {
          throw new ConnectorException("transform fail");
        };

    var result = pipeline.execute(job, reader, transformer, writer, listener);

    assertThat(result.status()).isEqualTo(TransferStatus.FAILED);
    verify(listener).onError(any(), any());
  }

  @Test
  void multipleRecordsAllTransferred() throws Exception {
    var r1 = DataRecord.of(Map.of("id", 1));
    var r2 = DataRecord.of(Map.of("id", 2));
    var r3 = DataRecord.of(Map.of("id", 3));
    when(reader.hasMore()).thenReturn(true, true, true, false);
    when(reader.readNext()).thenReturn(Optional.of(r1), Optional.of(r2), Optional.of(r3));
    RecordTransformer transformer = source -> source;

    var result = pipeline.execute(job, reader, transformer, writer, listener);

    assertThat(result.status()).isEqualTo(TransferStatus.SUCCESS);
    assertThat(result.transferredCount()).isEqualTo(3L);
  }
}
