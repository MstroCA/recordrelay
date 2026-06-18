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
package io.recordrelay.connector.file.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordReader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Optional;

/** Reads records from a JSON Lines file (one JSON object per line) via Jackson. */
public final class JsonRecordReader implements RecordReader {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private BufferedReader reader;
  private String nextLine;

  @Override
  public void open(ConnectionProfile profile, TableRef table, MappingDefinition mapping)
      throws ConnectorException {
    try {
      reader = new BufferedReader(new FileReader(profile.database()));
      advance();
    } catch (IOException e) {
      throw new ConnectorException("Failed to open JSON file: " + profile.database(), e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Optional<DataRecord> readNext() throws ConnectorException {
    if (nextLine == null) {
      return Optional.empty();
    }
    try {
      var map = MAPPER.readValue(nextLine, LinkedHashMap.class);
      advance();
      return Optional.of(new DataRecord(map));
    } catch (IOException e) {
      throw new ConnectorException("Error parsing JSON line: " + e.getMessage(), e);
    }
  }

  @Override
  public boolean hasMore() {
    return nextLine != null;
  }

  @Override
  public void close() throws ConnectorException {
    try {
      if (reader != null) {
        reader.close();
      }
    } catch (IOException e) {
      throw new ConnectorException("Error closing JSON reader", e);
    }
  }

  private void advance() throws IOException {
    do {
      nextLine = reader.readLine();
    } while (nextLine != null && nextLine.isBlank());
  }
}
