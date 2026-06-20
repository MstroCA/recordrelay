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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.domain.TableRef;
import java.sql.DriverManager;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Integration tests for {@link PostgreSqlConnector} and {@link PostgreSqlSchemaInspector}. */
@Tag("integration")
@Testcontainers
class PostgreSqlConnectorIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("testdb")
          .withUsername("tester")
          .withPassword("secret");

  private final PostgreSqlConnector connector = new PostgreSqlConnector();
  private final PostgreSqlSchemaInspector inspector = new PostgreSqlSchemaInspector();

  @BeforeAll
  static void seedSchema() throws Exception {
    try (var conn =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS users ("
              + "  id        SERIAL PRIMARY KEY, "
              + "  username  VARCHAR(100) NOT NULL, "
              + "  email     VARCHAR(255), "
              + "  active    BOOLEAN DEFAULT true"
              + ")");
    }
  }

  private ConnectionProfile profile() {
    return new ConnectionProfile(
        "pg-it",
        "integration-test",
        "env-test",
        DatabaseType.POSTGRESQL,
        POSTGRES.getHost(),
        POSTGRES.getMappedPort(5432),
        POSTGRES.getDatabaseName(),
        new Credentials(POSTGRES.getUsername(), POSTGRES.getPassword()),
        Map.of());
  }

  @Test
  void shouldTestConnectionSuccessfully() {
    assertThatCode(() -> connector.testConnection(profile())).doesNotThrowAnyException();
  }

  @Test
  void shouldListDatabases() {
    var databases = connector.listDatabases(profile());
    assertThat(databases).isNotEmpty().extracting(DatabaseRef::name).contains("testdb");
  }

  @Test
  void shouldListTablesInDatabase() {
    var db = new DatabaseRef("testdb", DatabaseType.POSTGRESQL);
    var tables = inspector.listTables(profile(), db);
    assertThat(tables).isNotEmpty().extracting(TableRef::tableName).contains("users");
  }

  @Test
  void shouldInspectColumnsOfUsersTable() {
    var db = new DatabaseRef("testdb", DatabaseType.POSTGRESQL);
    var table = new TableRef(db, "public", "users");
    var columns = inspector.inspectColumns(profile(), table);
    assertThat(columns).hasSize(4);
    assertThat(columns)
        .extracting(c -> c.name())
        .containsExactly("id", "username", "email", "active");
    assertThat(columns.get(0).primaryKey()).isTrue();
    assertThat(columns.get(1).nullable()).isFalse();
  }

  @Test
  void shouldAnalyzeCompatibilityBetweenIdenticalSchemas() {
    var db = new DatabaseRef("testdb", DatabaseType.POSTGRESQL);
    var table = new TableRef(db, "public", "users");
    var columns = inspector.inspectColumns(profile(), table);
    var report = inspector.analyzeCompatibility(columns, columns);
    assertThat(report.matchPercentage()).isEqualTo(100.0);
    assertThat(report.isFullyCompatible()).isTrue();
  }
}
