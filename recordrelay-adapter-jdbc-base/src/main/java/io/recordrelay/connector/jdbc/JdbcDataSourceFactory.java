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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.recordrelay.core.domain.ConnectionProfile;

/**
 * Creates short-lived HikariCP pools for JDBC connector introspection operations.
 *
 * <p>Pool size is capped at 1 — introspection operations are sequential and a larger pool would
 * consume unnecessary resources for schema-discovery use cases.
 */
public final class JdbcDataSourceFactory {

  private static final int POOL_SIZE = 1;
  private static final long CONNECTION_TIMEOUT_MS = 10_000L;
  private static final long VALIDATION_TIMEOUT_MS = 5_000L;

  private JdbcDataSourceFactory() {}

  /**
   * Creates a HikariDataSource from the given profile and JDBC URL scheme.
   *
   * @param profile connection parameters
   * @param scheme JDBC sub-protocol (e.g., "mysql", "sqlserver")
   * @return configured HikariDataSource (AutoCloseable)
   */
  public static HikariDataSource create(ConnectionProfile profile, String scheme) {
    var config = new HikariConfig();
    config.setJdbcUrl(profile.jdbcUrl(scheme));
    config.setUsername(profile.credentials().username());
    config.setPassword(profile.credentials().password());
    config.setMaximumPoolSize(POOL_SIZE);
    config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
    config.setValidationTimeout(VALIDATION_TIMEOUT_MS);
    profile.properties().forEach(config::addDataSourceProperty);
    // HikariCP resolves the JDBC driver via DriverManager using the thread context classloader.
    // In an IntelliJ plugin the TCCL is the platform CL, which cannot see driver JARs bundled
    // inside the plugin sandbox. Swap to our own CL so DriverManager can find the driver.
    var cl = JdbcDataSourceFactory.class.getClassLoader();
    var tccl = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(cl);
      return new HikariDataSource(config);
    } finally {
      Thread.currentThread().setContextClassLoader(tccl);
    }
  }
}
