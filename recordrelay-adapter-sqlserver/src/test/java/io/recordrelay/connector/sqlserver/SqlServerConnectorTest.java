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
package io.recordrelay.connector.sqlserver;

import static org.assertj.core.api.Assertions.assertThat;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DatabaseType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SqlServerConnector}. */
class SqlServerConnectorTest {

  private final SqlServerConnector connector = new SqlServerConnector();

  @Test
  void connectorIdIsSqlserver() {
    assertThat(connector.connectorId()).isEqualTo("sqlserver");
  }

  @Test
  void supportsSqlServerProfiles() {
    assertThat(connector.supports(profile(DatabaseType.SQLSERVER))).isTrue();
  }

  @Test
  void doesNotSupportMysql() {
    assertThat(connector.supports(profile(DatabaseType.MYSQL))).isFalse();
  }

  @Test
  void doesNotSupportPostgresql() {
    assertThat(connector.supports(profile(DatabaseType.POSTGRESQL))).isFalse();
  }

  private ConnectionProfile profile(DatabaseType type) {
    return new ConnectionProfile(
        "id",
        "test",
        "env",
        type,
        "localhost",
        1433,
        "master",
        new Credentials("sa", "P@ssw0rd"),
        Map.of());
  }
}
