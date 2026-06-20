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

import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/** Streams rows from a CSV file using Apache Commons CSV. */
public final class CsvRecordReader implements RecordReader {

  private CSVParser parser;
  private Iterator<CSVRecord> iterator;

  @Override
  public void open(ConnectionProfile profile, TableRef table)
      throws ConnectorException {
    try {
      parser =
          CSVFormat.DEFAULT
              .builder()
              .setHeader()
              .setSkipHeaderRecord(true)
              .build()
              .parse(new FileReader(profile.database()));
      iterator = parser.iterator();
    } catch (IOException e) {
      throw new ConnectorException("Failed to open CSV file: " + profile.database(), e);
    }
  }

  @Override
  public Optional<DataRecord> readNext() throws ConnectorException {
    if (!iterator.hasNext()) {
      return Optional.empty();
    }
    var csv = iterator.next();
    var fields = new LinkedHashMap<String, Object>(csv.size());
    for (String col : csv.getParser().getHeaderNames()) {
      fields.put(col, csv.get(col));
    }
    return Optional.of(new DataRecord(fields));
  }

  @Override
  public boolean hasMore() {
    return iterator.hasNext();
  }

  @Override
  public void close() throws ConnectorException {
    try {
      if (parser != null) {
        parser.close();
      }
    } catch (IOException e) {
      throw new ConnectorException("Error closing CSV reader", e);
    }
  }
}
