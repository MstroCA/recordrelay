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
package io.recordrelay.connector.cassandra;

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
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Integration tests for {@link CassandraConnector} using a real Cassandra instance. */
@Tag("integration")
@Testcontainers
class CassandraConnectorIT {

  @Container
  @SuppressWarnings("resource")
  static final CassandraContainer<?> CASSANDRA = new CassandraContainer<>("cassandra:4");

  private static ConnectionProfile profileWithKeyspace;
  private static ConnectionProfile profileNoKeyspace;
  private final CassandraConnector connector = new CassandraConnector();
  private final CassandraSchemaInspector inspector = new CassandraSchemaInspector();

  @BeforeAll
  static void setup() {
    profileNoKeyspace =
        new ConnectionProfile(
            "id",
            "cassandra-it",
            "test",
            DatabaseType.CASSANDRA,
            CASSANDRA.getHost(),
            CASSANDRA.getMappedPort(9042),
            "",
            new Credentials("", ""),
            Map.of());

    // Create keyspace and table
    try (var session = CassandraConnector.openSession(profileNoKeyspace)) {
      session.execute(
          "CREATE KEYSPACE IF NOT EXISTS testks WITH replication = "
              + "{'class': 'SimpleStrategy', 'replication_factor': 1}");
      session.execute(
          "CREATE TABLE IF NOT EXISTS testks.products "
              + "(id UUID PRIMARY KEY, name TEXT, price DECIMAL)");
    }

    profileWithKeyspace =
        new ConnectionProfile(
            "id",
            "cassandra-it-ks",
            "test",
            DatabaseType.CASSANDRA,
            CASSANDRA.getHost(),
            CASSANDRA.getMappedPort(9042),
            "testks",
            new Credentials("", ""),
            Map.of());
  }

  @Test
  void testConnectionSucceeds() throws Exception {
    connector.testConnection(profileNoKeyspace);
  }

  @Test
  void listDatabasesContainsTestKeyspace() throws Exception {
    var dbs = connector.listDatabases(profileNoKeyspace);
    assertThat(dbs).extracting("name").contains("testks");
  }

  @Test
  void listTablesContainsProducts() throws Exception {
    var db = new DatabaseRef("testks", DatabaseType.CASSANDRA);
    var tables = inspector.listTables(profileWithKeyspace, db);
    assertThat(tables).extracting(TableRef::tableName).contains("products");
  }

  @Test
  void inspectColumnsReturnsExpectedColumns() throws Exception {
    var db = new DatabaseRef("testks", DatabaseType.CASSANDRA);
    var tables = inspector.listTables(profileWithKeyspace, db);
    var products = tables.stream().filter(t -> t.tableName().equals("products")).findFirst().get();
    var columns = inspector.inspectColumns(profileWithKeyspace, products);
    assertThat(columns).extracting("name").containsExactlyInAnyOrder("id", "name", "price");
    assertThat(columns)
        .filteredOn(c -> c.name().equals("id"))
        .first()
        .satisfies(c -> assertThat(c.primaryKey()).isTrue());
  }
}
