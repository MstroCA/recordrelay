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
package io.recordrelay.connector.postgresql;

import io.recordrelay.connector.postgresql.internal.DataSourceFactory;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.ContextProviderPort;
import io.recordrelay.core.port.out.RecordReader;
import io.recordrelay.core.port.out.RecordWriter;
import io.recordrelay.core.port.out.SchemaInspector;
import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ContextProviderPort} adapter for PostgreSQL.
 *
 * <p>Registered via {@code META-INF/services} for ServiceLoader discovery. Supports {@link
 * DatabaseType#POSTGRESQL} profiles only.
 */
public final class PostgreSqlConnector implements ContextProviderPort {

  private static final Logger LOG = LoggerFactory.getLogger(PostgreSqlConnector.class);

  @Override
  public String connectorId() {
    return "postgresql";
  }

  @Override
  public boolean supports(ConnectionProfile profile) {
    return profile.type() == DatabaseType.POSTGRESQL;
  }

  @Override
  public void testConnection(ConnectionProfile profile) throws ConnectorException {
    try (var ds = DataSourceFactory.create(profile);
        var conn = ds.getConnection()) {
      if (!conn.isValid(5)) {
        throw new ConnectorException("Connection validation timed out for: " + profile.name());
      }
      LOG.debug("Connection test succeeded for profile '{}'", profile.name());
    } catch (SQLException e) {
      throw new ConnectorException(
          "Connection test failed for '" + profile.name() + "': " + e.getMessage(), e);
    }
  }

  @Override
  public List<DatabaseRef> listDatabases(ConnectionProfile profile) {
    String db = profile.database();
    LOG.debug("Returning configured database '{}' for profile '{}'", db, profile.name());
    return List.of(new DatabaseRef(db, DatabaseType.POSTGRESQL));
  }

  @Override
  public SchemaInspector schemaInspector() {
    return new PostgreSqlSchemaInspector();
  }

  @Override
  public RecordReader createReader() {
    return new PostgreSqlRecordReader();
  }

  @Override
  public RecordWriter createWriter() {
    return new PostgreSqlRecordWriter();
  }
}
