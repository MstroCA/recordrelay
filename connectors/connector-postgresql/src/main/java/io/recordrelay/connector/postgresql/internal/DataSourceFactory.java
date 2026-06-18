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
package io.recordrelay.connector.postgresql.internal;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.recordrelay.core.domain.ConnectionProfile;

/**
 * Creates short-lived HikariCP pools for connector introspection operations.
 *
 * <p>Pool size is capped at 1 — introspection operations are sequential and a larger pool would
 * consume unnecessary resources for schema-discovery use cases.
 */
public final class DataSourceFactory {

  private static final int INTROSPECTION_POOL_SIZE = 1;
  private static final long CONNECTION_TIMEOUT_MS = 10_000L;
  private static final long VALIDATION_TIMEOUT_MS = 5_000L;

  private DataSourceFactory() {}

  /**
   * Creates a DataSource configured from the given connection profile.
   *
   * <p>Callers are responsible for closing the returned DataSource after use.
   *
   * @param profile the connection profile providing host, port, database, and credentials
   * @return a configured HikariDataSource (implements AutoCloseable, safe for try-with-resources)
   */
  public static HikariDataSource create(ConnectionProfile profile) {
    var config = new HikariConfig();
    config.setJdbcUrl(profile.jdbcUrl("postgresql"));
    config.setUsername(profile.credentials().username());
    config.setPassword(profile.credentials().password());
    config.setMaximumPoolSize(INTROSPECTION_POOL_SIZE);
    config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
    config.setValidationTimeout(VALIDATION_TIMEOUT_MS);
    profile.properties().forEach(config::addDataSourceProperty);
    return new HikariDataSource(config);
  }
}
