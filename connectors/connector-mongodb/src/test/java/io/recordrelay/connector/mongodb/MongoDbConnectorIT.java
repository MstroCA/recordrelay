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
package io.recordrelay.connector.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.mongodb.client.MongoClients;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.domain.TableRef;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Integration tests for {@link MongoDbConnector} and {@link MongoDbSchemaInspector}. */
@Tag("integration")
@Testcontainers
class MongoDbConnectorIT {

  @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

  private final MongoDbConnector connector = new MongoDbConnector();
  private final MongoDbSchemaInspector inspector = new MongoDbSchemaInspector();

  @BeforeAll
  static void seedData() {
    try (var client = MongoClients.create(MONGO.getConnectionString())) {
      var collection = client.getDatabase("testdb").getCollection("products");
      collection.insertOne(
          new Document()
              .append("name", "Widget")
              .append("price", 9.99)
              .append("stock", 100)
              .append("active", true));
      collection.insertOne(
          new Document().append("name", "Gadget").append("price", 19.99).append("stock", 50));
    }
  }

  private ConnectionProfile profile() {
    return new ConnectionProfile(
        "mongo-it",
        "integration-test",
        "env-test",
        DatabaseType.MONGODB,
        MONGO.getHost(),
        MONGO.getMappedPort(27017),
        "admin",
        new Credentials("", ""),
        Map.of());
  }

  @Test
  void shouldListDatabases() {
    var databases = connector.listDatabases(profile());
    assertThat(databases).extracting(DatabaseRef::name).contains("testdb");
  }

  @Test
  void shouldListCollectionsAsTableRefs() {
    var db = new DatabaseRef("testdb", DatabaseType.MONGODB);
    var tables = inspector.listTables(profile(), db);
    assertThat(tables).extracting(TableRef::tableName).contains("products");
  }

  @Test
  void shouldInferSchemaFromSampledDocuments() {
    var db = new DatabaseRef("testdb", DatabaseType.MONGODB);
    var table = new TableRef(db, "", "products");
    var columns = inspector.inspectColumns(profile(), table);
    assertThat(columns).isNotEmpty();
    assertThat(columns).extracting(c -> c.name()).contains("_id", "name", "price", "stock");
    var idCol = columns.stream().filter(c -> "_id".equals(c.name())).findFirst().orElseThrow();
    assertThat(idCol.primaryKey()).isTrue();
  }

  @Test
  void shouldAnalyzeCompatibilityOfSampledSchema() {
    var db = new DatabaseRef("testdb", DatabaseType.MONGODB);
    var table = new TableRef(db, "", "products");
    var columns = inspector.inspectColumns(profile(), table);
    var report = inspector.analyzeCompatibility(columns, columns);
    assertThat(report.matchPercentage()).isEqualTo(100.0);
  }

  @Test
  void testConnectionShouldSucceedWhenReachable() {
    assertThatCode(() -> connector.testConnection(profile())).doesNotThrowAnyException();
  }
}
