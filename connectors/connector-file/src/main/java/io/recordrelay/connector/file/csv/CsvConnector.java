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
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.DataSourceConnector;
import io.recordrelay.core.port.out.RecordReader;
import io.recordrelay.core.port.out.RecordWriter;
import io.recordrelay.core.port.out.SchemaInspector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DataSourceConnector} adapter for CSV files (RFC 4180) via Apache Commons CSV.
 *
 * <p>The profile's {@code database} field is the path to the CSV file.
 */
public final class CsvConnector implements DataSourceConnector {

  private static final Logger LOG = LoggerFactory.getLogger(CsvConnector.class);

  @Override
  public String connectorId() {
    return "file-csv";
  }

  @Override
  public boolean supports(ConnectionProfile profile) {
    return profile.type() == DatabaseType.FILE_CSV;
  }

  @Override
  public void testConnection(ConnectionProfile profile) throws ConnectorException {
    var path = Path.of(profile.database());
    if (Files.exists(path) && !Files.isReadable(path)) {
      throw new ConnectorException("File exists but is not readable: " + path);
    }
    if (!Files.exists(path)) {
      try {
        Files.createDirectories(path.getParent() != null ? path.getParent() : Path.of("."));
      } catch (IOException e) {
        throw new ConnectorException("Cannot access parent directory for: " + path, e);
      }
    }
    LOG.debug("Connection test succeeded for '{}'", profile.name());
  }

  @Override
  public List<DatabaseRef> listDatabases(ConnectionProfile profile) throws ConnectorException {
    return List.of(new DatabaseRef(profile.database(), DatabaseType.FILE_CSV));
  }

  @Override
  public SchemaInspector schemaInspector() {
    return new CsvSchemaInspector();
  }

  @Override
  public RecordReader createReader() {
    return new CsvRecordReader();
  }

  @Override
  public RecordWriter createWriter() {
    return new CsvRecordWriter();
  }
}
