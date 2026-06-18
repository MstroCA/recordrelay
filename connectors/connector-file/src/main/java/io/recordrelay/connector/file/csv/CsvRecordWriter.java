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
package io.recordrelay.connector.file.csv;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/** Writes records to a CSV file using Apache Commons CSV. */
public final class CsvRecordWriter implements RecordWriter {

  private CSVPrinter printer;
  private List<String> headers;

  @Override
  public void open(ConnectionProfile profile, TableRef table, MappingDefinition mapping)
      throws ConnectorException {
    // Headers written lazily from first record.
    try {
      // We defer actual printer creation until we know the headers.
      this.headers = null;
      // Store path for deferred open
      this.path = profile.database();
    } catch (Exception e) {
      throw new ConnectorException("Failed to prepare CSV writer: " + profile.database(), e);
    }
  }

  private String path;

  @Override
  public void write(DataRecord record) throws ConnectorException {
    if (printer == null) {
      headers = List.copyOf(record.fieldNames());
      try {
        printer =
            CSVFormat.DEFAULT
                .builder()
                .setHeader(headers.toArray(String[]::new))
                .build()
                .print(new FileWriter(path));
      } catch (IOException e) {
        throw new ConnectorException("Failed to open CSV printer: " + path, e);
      }
    }
    try {
      printer.printRecord(
          headers.stream()
              .map(
                  h -> {
                    var v = record.get(h);
                    return v == null ? "" : v.toString();
                  })
              .toList());
    } catch (IOException e) {
      throw new ConnectorException("Error writing CSV record", e);
    }
  }

  @Override
  public void flush() throws ConnectorException {
    if (printer != null) {
      try {
        printer.flush();
      } catch (IOException e) {
        throw new ConnectorException("Error flushing CSV writer", e);
      }
    }
  }

  @Override
  public void close() throws ConnectorException {
    if (printer != null) {
      try {
        printer.close();
      } catch (IOException e) {
        throw new ConnectorException("Error closing CSV writer", e);
      }
    }
  }
}
