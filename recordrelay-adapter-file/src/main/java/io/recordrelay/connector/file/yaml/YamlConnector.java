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
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.ContextProviderPort;
import io.recordrelay.core.port.out.RecordReader;
import io.recordrelay.core.port.out.RecordWriter;
import io.recordrelay.core.port.out.SchemaInspector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ContextProviderPort} adapter for YAML files via SnakeYAML.
 *
 * <p>The file is treated as a YAML stream of mapping documents; each document is one record.
 */
public final class YamlConnector implements ContextProviderPort {

  private static final Logger LOG = LoggerFactory.getLogger(YamlConnector.class);

  @Override
  public String connectorId() {
    return "file-yaml";
  }

  @Override
  public boolean supports(ConnectionProfile profile) {
    return profile.type() == DatabaseType.FILE_YAML;
  }

  @Override
  public void testConnection(ConnectionProfile profile) throws ConnectorException {
    var path = Path.of(profile.database());
    if (Files.exists(path) && !Files.isReadable(path)) {
      throw new ConnectorException("YAML file is not readable: " + path);
    }
    LOG.debug("Connection test succeeded for '{}'", profile.name());
  }

  @Override
  public List<DatabaseRef> listDatabases(ConnectionProfile profile) throws ConnectorException {
    return List.of(new DatabaseRef(profile.database(), DatabaseType.FILE_YAML));
  }

  @Override
  public SchemaInspector schemaInspector() {
    return new YamlSchemaInspector();
  }

  @Override
  public RecordReader createReader() {
    return new YamlRecordReader();
  }

  @Override
  public RecordWriter createWriter() {
    return new YamlRecordWriter();
  }
}
