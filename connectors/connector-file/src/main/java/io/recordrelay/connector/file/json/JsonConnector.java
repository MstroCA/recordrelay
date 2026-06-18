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

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.DataSourceConnector;
import io.recordrelay.core.port.out.RecordReader;
import io.recordrelay.core.port.out.RecordWriter;
import io.recordrelay.core.port.out.SchemaInspector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DataSourceConnector} adapter for JSON Lines files.
 *
 * <p>Each line of the file is a JSON object representing one record.
 */
public final class JsonConnector implements DataSourceConnector {

  private static final Logger LOG = LoggerFactory.getLogger(JsonConnector.class);

  @Override
  public String connectorId() {
    return "file-json";
  }

  @Override
  public boolean supports(ConnectionProfile profile) {
    return profile.type() == DatabaseType.FILE_JSON;
  }

  @Override
  public void testConnection(ConnectionProfile profile) throws ConnectorException {
    var path = Path.of(profile.database());
    if (Files.exists(path) && !Files.isReadable(path)) {
      throw new ConnectorException("JSON file is not readable: " + path);
    }
    LOG.debug("Connection test succeeded for '{}'", profile.name());
  }

  @Override
  public List<DatabaseRef> listDatabases(ConnectionProfile profile) throws ConnectorException {
    return List.of(new DatabaseRef(profile.database(), DatabaseType.FILE_JSON));
  }

  @Override
  public SchemaInspector schemaInspector() {
    return new JsonSchemaInspector();
  }

  @Override
  public RecordReader createReader() {
    return new JsonRecordReader();
  }

  @Override
  public RecordWriter createWriter() {
    return new JsonRecordWriter();
  }
}
