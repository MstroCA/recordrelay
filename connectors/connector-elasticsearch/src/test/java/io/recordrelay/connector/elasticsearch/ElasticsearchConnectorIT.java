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
package io.recordrelay.connector.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Integration tests for {@link ElasticsearchConnector} using a real Elasticsearch instance. */
@Tag("integration")
@Testcontainers
class ElasticsearchConnectorIT {

  @Container
  static final ElasticsearchContainer ELASTICSEARCH =
      new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.17.0")
          .withPassword("testpassword");

  private static ConnectionProfile profile;
  private final ElasticsearchConnector connector = new ElasticsearchConnector();
  private final ElasticsearchSchemaInspector inspector = new ElasticsearchSchemaInspector();

  @BeforeAll
  static void setup() throws Exception {
    profile =
        new ConnectionProfile(
            "id",
            "es-it",
            "test",
            DatabaseType.ELASTICSEARCH,
            ELASTICSEARCH.getHost(),
            ELASTICSEARCH.getMappedPort(9200),
            "",
            new Credentials("elastic", "testpassword"),
            Map.of());

    // Create a test index with a mapping
    try (var transport = ElasticsearchConnector.buildTransport(profile)) {
      var client = new co.elastic.clients.elasticsearch.ElasticsearchClient(transport);
      client
          .indices()
          .create(
              c ->
                  c.index("products")
                      .mappings(
                          m ->
                              m.properties("id", p -> p.keyword(k -> k))
                                  .properties("name", p -> p.text(t -> t))
                                  .properties("price", p -> p.double_(d -> d))));
    }
  }

  @Test
  void testConnectionSucceeds() throws Exception {
    connector.testConnection(profile);
  }

  @Test
  void listDatabasesReturnsClusterEntry() throws Exception {
    var dbs = connector.listDatabases(profile);
    assertThat(dbs).hasSize(1);
  }

  @Test
  void listTablesContainsProducts() throws Exception {
    var db = new DatabaseRef("cluster", DatabaseType.ELASTICSEARCH);
    var tables = inspector.listTables(profile, db);
    assertThat(tables).extracting(t -> t.tableName()).contains("products");
  }

  @Test
  void inspectColumnsReturnsExpectedFields() throws Exception {
    var db = new DatabaseRef("cluster", DatabaseType.ELASTICSEARCH);
    var tables = inspector.listTables(profile, db);
    var products = tables.stream().filter(t -> t.tableName().equals("products")).findFirst().get();
    var cols = inspector.inspectColumns(profile, products);
    assertThat(cols).extracting("name").containsExactlyInAnyOrder("id", "name", "price");
  }
}
