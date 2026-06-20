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
package io.recordrelay.connector.mysql;

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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Integration tests for {@link MySqlConnector}. */
@Tag("integration")
@Testcontainers
class MySqlConnectorIT {

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0")
          .withDatabaseName("testdb")
          .withUsername("tester")
          .withPassword("secret");

  private final MySqlConnector connector = new MySqlConnector();
  private final MySqlSchemaInspector inspector = new MySqlSchemaInspector();

  @BeforeAll
  static void seedSchema() throws Exception {
    try (var conn =
            DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS products ("
              + "  id    INT PRIMARY KEY AUTO_INCREMENT, "
              + "  name  VARCHAR(200) NOT NULL, "
              + "  price DECIMAL(10,2)"
              + ")");
    }
  }

  private ConnectionProfile profile() {
    return new ConnectionProfile(
        "mysql-it",
        "it-mysql",
        "env-test",
        DatabaseType.MYSQL,
        MYSQL.getHost(),
        MYSQL.getMappedPort(3306),
        MYSQL.getDatabaseName(),
        new Credentials(MYSQL.getUsername(), MYSQL.getPassword()),
        Map.of());
  }

  @Test
  void shouldTestConnectionSuccessfully() {
    assertThatCode(() -> connector.testConnection(profile())).doesNotThrowAnyException();
  }

  @Test
  void shouldListDatabasesContainingTestdb() {
    var dbs = connector.listDatabases(profile());
    assertThat(dbs).extracting(DatabaseRef::name).contains("testdb");
  }

  @Test
  void shouldListTablesInTestdb() {
    var db = new DatabaseRef("testdb", DatabaseType.MYSQL);
    var tables = inspector.listTables(profile(), db);
    assertThat(tables).extracting(TableRef::tableName).contains("products");
  }

  @Test
  void shouldInspectColumnsOfProductsTable() {
    var db = new DatabaseRef("testdb", DatabaseType.MYSQL);
    var table = new TableRef(db, "", "products");
    var cols = inspector.inspectColumns(profile(), table);
    assertThat(cols).hasSizeGreaterThanOrEqualTo(3);
    assertThat(cols).extracting(c -> c.name()).contains("id", "name", "price");
  }
}
