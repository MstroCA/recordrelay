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
package io.recordrelay.connector.snowflake.internal;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.recordrelay.core.domain.ConnectionProfile;

/**
 * Creates short-lived HikariCP pools for Snowflake connector introspection.
 *
 * <p>The JDBC URL uses the Snowflake driver scheme with the account hostname as host. Database,
 * schema, and warehouse are forwarded as connection properties so they can be overridden per
 * profile without re-encoding them in the URL.
 */
public final class DataSourceFactory {

  private static final int INTROSPECTION_POOL_SIZE = 1;
  private static final long CONNECTION_TIMEOUT_MS = 30_000L;
  private static final long VALIDATION_TIMEOUT_MS = 10_000L;

  private DataSourceFactory() {}

  /**
   * Creates a HikariDataSource for Snowflake.
   *
   * <p>The {@code host} field in the profile must be the Snowflake account identifier with domain,
   * e.g. {@code myorg-myaccount.snowflakecomputing.com}. Optional profile properties: {@code
   * warehouse}, {@code schema}, {@code role}.
   *
   * @param profile the connection profile
   * @return a configured HikariDataSource (safe for try-with-resources)
   */
  public static HikariDataSource create(ConnectionProfile profile) {
    var config = new HikariConfig();
    // Snowflake JDBC URL: jdbc:snowflake://<account>.snowflakecomputing.com/
    config.setJdbcUrl("jdbc:snowflake://" + profile.host() + "/");
    config.setUsername(profile.credentials().username());
    config.setPassword(profile.credentials().password());
    config.setMaximumPoolSize(INTROSPECTION_POOL_SIZE);
    config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
    config.setValidationTimeout(VALIDATION_TIMEOUT_MS);

    // Core Snowflake connection parameters
    config.addDataSourceProperty("db", profile.database());
    var props = profile.properties();
    if (props.containsKey("warehouse")) {
      config.addDataSourceProperty("warehouse", props.get("warehouse"));
    }
    if (props.containsKey("schema")) {
      config.addDataSourceProperty("schema", props.get("schema"));
    }
    if (props.containsKey("role")) {
      config.addDataSourceProperty("role", props.get("role"));
    }
    // Forward any remaining caller-supplied properties
    props.forEach(
        (k, v) -> {
          if (!k.equals("warehouse") && !k.equals("schema") && !k.equals("role")) {
            config.addDataSourceProperty(k, v);
          }
        });

    // TCCL swap: plugin classloaders may shadow the Snowflake driver JAR
    var cl = DataSourceFactory.class.getClassLoader();
    var tccl = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(cl);
      return new HikariDataSource(config);
    } finally {
      Thread.currentThread().setContextClassLoader(tccl);
    }
  }
}
