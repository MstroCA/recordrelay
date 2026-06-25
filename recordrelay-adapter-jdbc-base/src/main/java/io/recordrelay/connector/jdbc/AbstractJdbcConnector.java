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
package io.recordrelay.connector.jdbc;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.ContextProviderPort;
import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for JDBC-based {@link ContextProviderPort} implementations.
 *
 * <p>Subclasses provide the JDBC scheme and the database-listing SQL. Reader, writer, and schema
 * inspector creation are delegated to subclasses.
 */
public abstract class AbstractJdbcConnector implements ContextProviderPort {

  /** Logger available to subclasses. */
  protected final Logger log = LoggerFactory.getLogger(getClass());

  /**
   * Returns the JDBC URL sub-protocol for this engine (e.g., {@code "mysql"}).
   *
   * @return JDBC scheme
   */
  protected abstract String jdbcScheme();

  @Override
  public void testConnection(ConnectionProfile profile) throws ConnectorException {
    try (var ds = JdbcDataSourceFactory.create(profile, jdbcScheme());
        var conn = ds.getConnection()) {
      if (!conn.isValid(5)) {
        throw new ConnectorException("Validation timed out for: " + profile.name());
      }
      log.debug("Connection test succeeded for '{}'", profile.name());
    } catch (SQLException e) {
      throw new ConnectorException(
          "Connection test failed for '" + profile.name() + "': " + e.getMessage(), e);
    }
  }

  @Override
  public List<DatabaseRef> listDatabases(ConnectionProfile profile) {
    String db = profile.database();
    log.debug("Returning configured database '{}' for '{}'", db, profile.name());
    return List.of(new DatabaseRef(db, profile.type()));
  }
}
