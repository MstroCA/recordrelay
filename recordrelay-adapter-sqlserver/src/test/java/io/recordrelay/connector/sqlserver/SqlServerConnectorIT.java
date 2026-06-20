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
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.domain.TableRef;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Integration tests for {@link SqlServerConnector} using a real SQL Server instance. */
@Tag("integration")
@Testcontainers
class SqlServerConnectorIT {

  @Container
  static final MSSQLServerContainer<?> MSSQL =
      new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
          .acceptLicense()
          .withPassword("P@ssw0rdStrong1");

  private static ConnectionProfile profile;
  private final SqlServerConnector connector = new SqlServerConnector();
  private final SqlServerSchemaInspector inspector = new SqlServerSchemaInspector();

  @BeforeAll
  static void setup() throws Exception {
    profile =
        new ConnectionProfile(
            "id",
            "sqlserver-it",
            "test",
            DatabaseType.SQLSERVER,
            MSSQL.getHost(),
            MSSQL.getMappedPort(1433),
            "master",
            new Credentials(MSSQL.getUsername(), MSSQL.getPassword()),
            Map.of());

    try (var conn =
        java.sql.DriverManager.getConnection(
            "jdbc:sqlserver://"
                + MSSQL.getHost()
                + ":"
                + MSSQL.getMappedPort(1433)
                + ";databaseName=master;trustServerCertificate=true",
            MSSQL.getUsername(),
            MSSQL.getPassword())) {
      try (var stmt = conn.createStatement()) {
        stmt.execute(
            "CREATE TABLE products (id INT PRIMARY KEY, name NVARCHAR(100), price DECIMAL(10,2))");
        stmt.execute("INSERT INTO products VALUES (1, 'Widget', 9.99)");
        stmt.execute("INSERT INTO products VALUES (2, 'Gadget', 24.99)");
      }
    }
  }

  @Test
  void testConnectionSucceeds() throws Exception {
    connector.testConnection(profile);
  }

  @Test
  void listDatabasesContainsMaster() throws Exception {
    var dbs = connector.listDatabases(profile);
    assertThat(dbs).extracting("name").contains("master");
  }

  @Test
  void listTablesContainsProducts() throws Exception {
    var db = new DatabaseRef("master", DatabaseType.SQLSERVER);
    var tables = inspector.listTables(profile, db);
    assertThat(tables).extracting(TableRef::tableName).contains("products");
  }

  @Test
  void inspectColumnsReturnsExpectedColumns() throws Exception {
    var db = new DatabaseRef("master", DatabaseType.SQLSERVER);
    var tables = inspector.listTables(profile, db);
    var products = tables.stream().filter(t -> t.tableName().equals("products")).findFirst().get();
    var columns = inspector.inspectColumns(profile, products);
    assertThat(columns).extracting("name").containsExactlyInAnyOrder("id", "name", "price");
    assertThat(columns)
        .filteredOn(c -> c.name().equals("id"))
        .first()
        .satisfies(
            c -> {
              assertThat(c.primaryKey()).isTrue();
            });
  }
}
