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
package io.recordrelay.connector.snowflake;

import io.recordrelay.connector.snowflake.internal.DataSourceFactory;
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
 * {@link ContextProviderPort} adapter for Snowflake.
 *
 * <p>Registered via {@code META-INF/services} for ServiceLoader discovery. Supports {@link
 * DatabaseType#SNOWFLAKE} profiles only.
 *
 * <p>The connection profile {@code host} field must be the Snowflake account identifier with full
 * domain, e.g. {@code myorg-myaccount.snowflakecomputing.com}. Additional Snowflake parameters such
 * as {@code warehouse}, {@code schema}, and {@code role} are provided via the profile's {@code
 * properties} map.
 */
public final class SnowflakeConnector implements ContextProviderPort {

  private static final Logger LOG = LoggerFactory.getLogger(SnowflakeConnector.class);

  @Override
  public String connectorId() {
    return "snowflake";
  }

  @Override
  public boolean supports(ConnectionProfile profile) {
    return profile.type() == DatabaseType.SNOWFLAKE;
  }

  @Override
  public void testConnection(ConnectionProfile profile) throws ConnectorException {
    try (var ds = DataSourceFactory.create(profile);
        var conn = ds.getConnection()) {
      if (!conn.isValid(10)) {
        throw new ConnectorException("Connection validation timed out for: " + profile.name());
      }
      LOG.debug("Snowflake connection test succeeded for profile '{}'", profile.name());
    } catch (SQLException e) {
      throw new ConnectorException(
          "Snowflake connection test failed for '" + profile.name() + "': " + e.getMessage(), e);
    }
  }

  @Override
  public List<DatabaseRef> listDatabases(ConnectionProfile profile) {
    String db = profile.database();
    LOG.debug("Returning configured database '{}' for Snowflake profile '{}'", db, profile.name());
    return List.of(new DatabaseRef(db, DatabaseType.SNOWFLAKE));
  }

  @Override
  public SchemaInspector schemaInspector() {
    return new SnowflakeSchemaInspector();
  }

  @Override
  public RecordReader createReader() {
    return new SnowflakeRecordReader();
  }

  @Override
  public RecordWriter createWriter() {
    return new SnowflakeRecordWriter();
  }
}
