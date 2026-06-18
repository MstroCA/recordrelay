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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("integration")
@Testcontainers
class BatchTransferPgToPgIT {

  @Container
  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> SOURCE =
      new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("srcdb");

  @Container
  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> TARGET =
      new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("tgtdb");

  private BatchPipelineAdapter batchAdapter;

  @BeforeEach
  void setUp() throws Exception {
    batchAdapter = new BatchPipelineAdapter();
    seedSource(100);
    createTargetTable();
  }

  @AfterEach
  void tearDown() {
    batchAdapter.close();
  }

  @Test
  void shouldTransfer100RowsViaSpringBatch() throws Exception {
    var srcProfile = profile("src", SOURCE);
    var tgtProfile = profile("tgt", TARGET);

    var srcRef =
        new TableRef(new DatabaseRef("srcdb", DatabaseType.POSTGRESQL), "public", "orders");
    var tgtRef =
        new TableRef(new DatabaseRef("tgtdb", DatabaseType.POSTGRESQL), "public", "orders");
    var mappings =
        List.of(
            new ColumnMapping("order_id", "order_id"),
            new ColumnMapping("customer", "customer"),
            new ColumnMapping("amount", "amount"));
    var mapping = new MappingDefinition("m-batch", srcRef, tgtRef, mappings, null, null);

    var job =
        new TransferJob(
            "job-batch-pg2pg",
            "Spring Batch PG→PG test",
            srcProfile,
            tgtProfile,
            mapping,
            TransferMode.BATCH,
            25,
            null,
            null);

    var engine = new DefaultTransferEngine(new SyncPipeline(), batchAdapter);
    var result = engine.transfer(job);

    assertThat(result.status()).isEqualTo(TransferStatus.SUCCESS);
    assertThat(result.transferredCount()).isEqualTo(100L);
    assertTargetRowCount(100);
  }

  private void seedSource(int rowCount) throws SQLException {
    try (var conn =
            DriverManager.getConnection(
                SOURCE.getJdbcUrl(), SOURCE.getUsername(), SOURCE.getPassword());
        var stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS orders "
              + "(order_id INT PRIMARY KEY, customer VARCHAR(50), amount NUMERIC)");
      stmt.execute("TRUNCATE orders");
      var sb = new StringBuilder("INSERT INTO orders VALUES ");
      for (int i = 1; i <= rowCount; i++) {
        if (i > 1) {
          sb.append(",");
        }
        sb.append("(")
            .append(i)
            .append(",'Customer")
            .append(i)
            .append("',")
            .append(i * 10)
            .append(")");
      }
      stmt.execute(sb.toString());
    }
  }

  private void createTargetTable() throws SQLException {
    try (var conn =
            DriverManager.getConnection(
                TARGET.getJdbcUrl(), TARGET.getUsername(), TARGET.getPassword());
        var stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS orders "
              + "(order_id INT PRIMARY KEY, customer VARCHAR(50), amount NUMERIC)");
      stmt.execute("TRUNCATE orders");
    }
  }

  private void assertTargetRowCount(long expected) throws SQLException {
    try (var conn =
            DriverManager.getConnection(
                TARGET.getJdbcUrl(), TARGET.getUsername(), TARGET.getPassword());
        var stmt = conn.createStatement();
        var rs = stmt.executeQuery("SELECT COUNT(*) FROM orders")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getLong(1)).isEqualTo(expected);
    }
  }

  private ConnectionProfile profile(String id, PostgreSQLContainer<?> container) {
    return new ConnectionProfile(
        id,
        "profile-" + id,
        "test",
        DatabaseType.POSTGRESQL,
        container.getHost(),
        container.getMappedPort(5432),
        container.getDatabaseName(),
        new Credentials(container.getUsername(), container.getPassword()),
        Map.of());
  }
}
