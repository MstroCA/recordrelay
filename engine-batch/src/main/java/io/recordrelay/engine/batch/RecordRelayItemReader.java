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
import io.recordrelay.core.port.out.RecordReader;
import java.util.Objects;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;

/**
 * Spring Batch {@link ItemReader} adapter that wraps a {@link RecordReader}.
 *
 * <p>Returns {@code null} to signal end-of-data, as required by the Spring Batch contract.
 */
public final class RecordRelayItemReader implements ItemReader<DataRecord> {

  private final RecordReader reader;

  public RecordRelayItemReader(RecordReader reader) {
    this.reader = Objects.requireNonNull(reader, "reader");
  }

  @Override
  public DataRecord read()
      throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
    try {
      return reader.readNext().orElse(null);
    } catch (ConnectorException e) {
      throw new UnexpectedInputException("Error reading next record: " + e.getMessage(), e);
    }
  }
}
