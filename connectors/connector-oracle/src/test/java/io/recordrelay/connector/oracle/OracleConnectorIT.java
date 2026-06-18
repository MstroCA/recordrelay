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
package io.recordrelay.connector.oracle;

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
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Integration tests for {@link OracleConnector} using a real Oracle XE instance. */
@Tag("integration")
@Testcontainers
class OracleConnectorIT {

  @Container
  static final OracleContainer ORACLE =
      new OracleContainer("gvenzl/oracle-xe:21-slim-faststart").withPassword("testpassword");

  private static ConnectionProfile profile;
  private final OracleConnector connector = new OracleConnector();
  private final OracleSchemaInspector inspector = new OracleSchemaInspector();

  @BeforeAll
  static void setup() throws Exception {
    profile =
        new ConnectionProfile(
            "id",
            "oracle-it",
            "test",
            DatabaseType.ORACLE,
            ORACLE.getHost(),
            ORACLE.getMappedPort(1521),
            ORACLE.getDatabaseName(),
            new Credentials(ORACLE.getUsername(), ORACLE.getPassword()),
            Map.of());

    try (var conn =
        java.sql.DriverManager.getConnection(
            "jdbc:oracle:thin:@//"
                + ORACLE.getHost()
                + ":"
                + ORACLE.getMappedPort(1521)
                + "/"
                + ORACLE.getDatabaseName(),
            ORACLE.getUsername(),
            ORACLE.getPassword())) {
      try (var stmt = conn.createStatement()) {
        stmt.execute(
            "CREATE TABLE products (id NUMBER PRIMARY KEY, name VARCHAR2(100), price NUMBER(10,2))");
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
  void listDatabasesReturnsServiceName() throws Exception {
    var dbs = connector.listDatabases(profile);
    assertThat(dbs).hasSize(1);
    assertThat(dbs.get(0).name()).isEqualToIgnoringCase(ORACLE.getDatabaseName());
  }

  @Test
  void listTablesContainsProducts() throws Exception {
    var db = new DatabaseRef(ORACLE.getDatabaseName(), DatabaseType.ORACLE);
    var tables = inspector.listTables(profile, db);
    assertThat(tables).extracting(TableRef::tableName).contains("PRODUCTS");
  }

  @Test
  void inspectColumnsReturnsExpectedColumns() throws Exception {
    var db = new DatabaseRef(ORACLE.getDatabaseName(), DatabaseType.ORACLE);
    var tables = inspector.listTables(profile, db);
    var products = tables.stream().filter(t -> t.tableName().equals("PRODUCTS")).findFirst().get();
    var columns = inspector.inspectColumns(profile, products);
    assertThat(columns).extracting("name").containsExactlyInAnyOrder("ID", "NAME", "PRICE");
    assertThat(columns)
        .filteredOn(c -> c.name().equals("ID"))
        .first()
        .satisfies(c -> assertThat(c.primaryKey()).isTrue());
  }
}
