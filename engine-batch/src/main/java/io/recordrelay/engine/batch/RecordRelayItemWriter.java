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
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordWriter;
import java.util.Objects;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

/**
 * Spring Batch {@link ItemWriter} adapter that delegates to a {@link RecordWriter}.
 *
 * <p>Calls {@link RecordWriter#flush()} after each chunk to commit the writes within the Spring
 * Batch transaction boundary.
 */
public final class RecordRelayItemWriter implements ItemWriter<DataRecord> {

  private final RecordWriter writer;

  public RecordRelayItemWriter(RecordWriter writer) {
    this.writer = Objects.requireNonNull(writer, "writer");
  }

  @Override
  public void write(Chunk<? extends DataRecord> chunk) throws Exception {
    try {
      for (var record : chunk) {
        writer.write(record);
      }
      writer.flush();
    } catch (ConnectorException e) {
      throw new RuntimeException("Write failed: " + e.getMessage(), e);
    }
  }
}
