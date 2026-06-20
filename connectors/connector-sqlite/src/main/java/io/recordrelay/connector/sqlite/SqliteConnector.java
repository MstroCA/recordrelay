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
package io.recordrelay.connector.sqlite;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.ContextProviderPort;
import io.recordrelay.core.port.out.RecordReader;
import io.recordrelay.core.port.out.RecordWriter;
import io.recordrelay.core.port.out.SchemaInspector;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ContextProviderPort} adapter for SQLite.
 *
 * <p>Registered via {@code META-INF/services} for ServiceLoader discovery. The {@code database}
 * field of the profile is interpreted as the file path to the SQLite database file.
 */
public final class SqliteConnector implements ContextProviderPort {

  private static final Logger LOG = LoggerFactory.getLogger(SqliteConnector.class);

  @Override
  public String connectorId() {
    return "sqlite";
  }

  @Override
  public boolean supports(ConnectionProfile profile) {
    return profile.type() == DatabaseType.SQLITE;
  }

  @Override
  public void testConnection(ConnectionProfile profile) throws ConnectorException {
    // For in-memory or new databases, the JDBC driver will always connect successfully.
    // For file-based databases we also accept paths that don't exist yet (SQLite auto-creates).
    var path = profile.database();
    var url = "jdbc:sqlite:" + path;
    try (var conn = DriverManager.getConnection(url)) {
      if (!conn.isValid(5)) {
        throw new ConnectorException("Validation timed out for: " + profile.name());
      }
      LOG.debug("Connection test succeeded for '{}'", profile.name());
    } catch (SQLException e) {
      throw new ConnectorException(
          "Connection test failed for '" + profile.name() + "': " + e.getMessage(), e);
    }
  }

  @Override
  public List<DatabaseRef> listDatabases(ConnectionProfile profile) throws ConnectorException {
    // SQLite is a single-file database; return the file path as the sole entry.
    return List.of(new DatabaseRef(profile.database(), DatabaseType.SQLITE));
  }

  @Override
  public SchemaInspector schemaInspector() {
    return new SqliteSchemaInspector();
  }

  @Override
  public RecordReader createReader() {
    return new SqliteRecordReader();
  }

  @Override
  public RecordWriter createWriter() {
    return new SqliteRecordWriter();
  }
}
