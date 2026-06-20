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
package io.recordrelay.connector.sqlserver.internal;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.recordrelay.core.domain.ConnectionProfile;

/**
 * Creates HikariCP pools for SQL Server connector operations.
 *
 * <p>SQL Server JDBC URL uses semicolon-separated parameters rather than the standard {@code
 * /database} path suffix.
 */
public final class SqlServerDataSourceFactory {

  private static final int POOL_SIZE = 1;
  private static final long CONNECTION_TIMEOUT_MS = 10_000L;

  private SqlServerDataSourceFactory() {}

  /**
   * Creates a DataSource for the given SQL Server profile.
   *
   * @param profile connection parameters
   * @return configured HikariDataSource (AutoCloseable)
   */
  public static HikariDataSource create(ConnectionProfile profile) {
    var config = new HikariConfig();
    config.setJdbcUrl(buildUrl(profile));
    config.setUsername(profile.credentials().username());
    config.setPassword(profile.credentials().password());
    config.setMaximumPoolSize(POOL_SIZE);
    config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
    return new HikariDataSource(config);
  }

  /**
   * Builds a SQL Server JDBC URL from the profile.
   *
   * @param profile connection parameters
   * @return JDBC URL with semicolon-separated parameters
   */
  public static String buildUrl(ConnectionProfile profile) {
    return String.format(
        "jdbc:sqlserver://%s:%d;databaseName=%s;trustServerCertificate=true",
        profile.host(), profile.port(), profile.database());
  }
}
