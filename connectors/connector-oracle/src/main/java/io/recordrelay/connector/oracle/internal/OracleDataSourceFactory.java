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
package io.recordrelay.connector.oracle.internal;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.recordrelay.core.domain.ConnectionProfile;

/** Factory that builds HikariDataSource for Oracle using the thin driver URL format. */
public final class OracleDataSourceFactory {

  private static final int POOL_SIZE = 1;
  private static final int TIMEOUT_MS = 10_000;

  private OracleDataSourceFactory() {}

  /** Builds {@code jdbc:oracle:thin:@//host:port/service} URL from the profile. */
  public static String buildUrl(ConnectionProfile profile) {
    return String.format(
        "jdbc:oracle:thin:@//%s:%d/%s", profile.host(), profile.port(), profile.database());
  }

  /** Creates a minimal HikariDataSource for the given profile. */
  public static HikariDataSource create(ConnectionProfile profile) {
    var config = new HikariConfig();
    config.setJdbcUrl(buildUrl(profile));
    config.setUsername(profile.credentials().username());
    config.setPassword(profile.credentials().password());
    config.setMaximumPoolSize(POOL_SIZE);
    config.setConnectionTimeout(TIMEOUT_MS);
    config.setPoolName("rr-oracle-" + profile.name());
    return new HikariDataSource(config);
  }
}
