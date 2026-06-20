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
package io.recordrelay.connector.file.parquet;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.io.LocalInputFile;

/** Reads records from a Parquet file using AvroParquetReader. */
public final class ParquetRecordReader implements RecordReader {

  private ParquetReader<GenericRecord> reader;
  private GenericRecord next;

  @Override
  public void open(ConnectionProfile profile, TableRef table) throws ConnectorException {
    try {
      reader =
          AvroParquetReader.<GenericRecord>builder(new LocalInputFile(Path.of(profile.database())))
              .build();
      advance();
    } catch (IOException e) {
      throw new ConnectorException("Failed to open Parquet file: " + profile.database(), e);
    }
  }

  @Override
  public Optional<DataRecord> readNext() throws ConnectorException {
    if (next == null) {
      return Optional.empty();
    }
    var schema = next.getSchema();
    var fields = new LinkedHashMap<String, Object>(schema.getFields().size());
    for (var field : schema.getFields()) {
      fields.put(field.name(), next.get(field.name()));
    }
    try {
      advance();
    } catch (IOException e) {
      throw new ConnectorException("Error advancing Parquet reader", e);
    }
    return Optional.of(new DataRecord(fields));
  }

  @Override
  public boolean hasMore() {
    return next != null;
  }

  @Override
  public void close() throws ConnectorException {
    try {
      if (reader != null) {
        reader.close();
      }
    } catch (IOException e) {
      throw new ConnectorException("Error closing Parquet reader", e);
    }
  }

  private void advance() throws IOException {
    next = reader.read();
  }
}
