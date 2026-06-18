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
package io.recordrelay.engine.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClients;
import io.recordrelay.core.domain.ColumnMapping;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.domain.TransferJob;
import io.recordrelay.core.domain.TransferMode;
import io.recordrelay.core.domain.TransferStatus;
import io.recordrelay.core.engine.DefaultTransferEngine;
import io.recordrelay.core.engine.SyncPipeline;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("integration")
@Testcontainers
class SyncTransferPgToMongoIT {

  @Container
  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> PG =
      new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("srcdb");

  @Container
  @SuppressWarnings("resource")
  private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

  @Test
  void shouldTransferRowsFromPostgresIntoMongoDB() throws Exception {
    seedPostgres();

    var srcProfile = pgProfile();
    var tgtProfile = mongoProfile();

    var srcRef =
        new TableRef(new DatabaseRef("srcdb", DatabaseType.POSTGRESQL), "public", "products");
    var tgtRef = new TableRef(new DatabaseRef("testdb", DatabaseType.MONGODB), null, "products");
    var mappings =
        List.of(
            new ColumnMapping("sku", "sku"),
            new ColumnMapping("name", "name"),
            new ColumnMapping("price", "price"));
    var mapping = new MappingDefinition("m-pg2mongo", srcRef, tgtRef, mappings, null, null);

    var job =
        new TransferJob(
            "job-pg2mongo-1",
            "PG→Mongo sync test",
            srcProfile,
            tgtProfile,
            mapping,
            TransferMode.SYNC,
            100,
            null,
            null);

    var engine = new DefaultTransferEngine(new SyncPipeline());
    var result = engine.transfer(job);

    assertThat(result.status()).isEqualTo(TransferStatus.SUCCESS);
    assertThat(result.transferredCount()).isEqualTo(3L);
    assertMongoHas3Documents();
  }

  private void seedPostgres() throws SQLException {
    try (var conn =
            DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        var stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS products "
              + "(sku VARCHAR(20) PRIMARY KEY, name VARCHAR(100), price NUMERIC)");
      stmt.execute("TRUNCATE products");
      stmt.execute(
          "INSERT INTO products VALUES ('A1','Widget',9.99),('B2','Gadget',24.99),('C3','Gizmo',4.99)");
    }
  }

  private void assertMongoHas3Documents() {
    var settings =
        MongoClientSettings.builder()
            .applyToClusterSettings(
                b ->
                    b.hosts(
                        List.of(new ServerAddress(MONGO.getHost(), MONGO.getMappedPort(27017)))))
            .applyToSocketSettings(b -> b.connectTimeout(5_000, TimeUnit.MILLISECONDS))
            .credential(MongoCredential.createCredential("test", "testdb", "test".toCharArray()))
            .build();
    try (var client = MongoClients.create(settings)) {
      var count = client.getDatabase("testdb").getCollection("products").countDocuments();
      assertThat(count).isEqualTo(3L);
    }
  }

  private ConnectionProfile pgProfile() {
    return new ConnectionProfile(
        "pg-src",
        "pg-source",
        "test",
        DatabaseType.POSTGRESQL,
        PG.getHost(),
        PG.getMappedPort(5432),
        PG.getDatabaseName(),
        new Credentials(PG.getUsername(), PG.getPassword()),
        Map.of());
  }

  private ConnectionProfile mongoProfile() {
    return new ConnectionProfile(
        "mongo-tgt",
        "mongo-target",
        "test",
        DatabaseType.MONGODB,
        MONGO.getHost(),
        MONGO.getMappedPort(27017),
        "testdb",
        new Credentials("test", "test"),
        Map.of());
  }
}
