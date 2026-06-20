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
package io.recordrelay.connector.file.yaml;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;

import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.yaml.snakeyaml.Yaml;

/** Streams YAML documents from a file; each document is one record. */
public final class YamlRecordReader implements RecordReader {

  private Reader fileReader;
  private Iterator<Map<String, Object>> docIterator;

  @Override
  @SuppressWarnings("unchecked")
  public void open(ConnectionProfile profile, TableRef table)
      throws ConnectorException {
    try {
      fileReader = new FileReader(profile.database());
      @SuppressWarnings("unchecked")
      Iterable<Map<String, Object>> docs =
          (Iterable<Map<String, Object>>) (Iterable<?>) new Yaml().loadAll(fileReader);
      docIterator = docs.iterator();
    } catch (IOException e) {
      throw new ConnectorException("Failed to open YAML file: " + profile.database(), e);
    }
  }

  @Override
  public Optional<DataRecord> readNext() throws ConnectorException {
    if (!docIterator.hasNext()) {
      return Optional.empty();
    }
    var doc = docIterator.next();
    if (doc == null) {
      return Optional.empty();
    }
    return Optional.of(new DataRecord(new LinkedHashMap<>(doc)));
  }

  @Override
  public boolean hasMore() {
    return docIterator.hasNext();
  }

  @Override
  public void close() throws ConnectorException {
    try {
      if (fileReader != null) {
        fileReader.close();
      }
    } catch (IOException e) {
      throw new ConnectorException("Error closing YAML reader", e);
    }
  }
}
