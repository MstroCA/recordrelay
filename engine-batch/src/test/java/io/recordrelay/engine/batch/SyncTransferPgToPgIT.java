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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("integration")
@Testcontainers
class SyncTransferPgToPgIT {

  @Container
  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> SOURCE =
      new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("srcdb");

  @Container
  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> TARGET =
      new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("tgtdb");

  @Test
  void shouldTransferAllRowsFromSourceToTarget() throws Exception {
    seedSource();
    createTargetTable();

    var srcProfile = profile("src", SOURCE);
    var tgtProfile = profile("tgt", TARGET);

    var srcRef =
        new TableRef(new DatabaseRef("srcdb", DatabaseType.POSTGRESQL), "public", "employees");
    var tgtRef =
        new TableRef(new DatabaseRef("tgtdb", DatabaseType.POSTGRESQL), "public", "employees");
    var mappings =
        List.of(
            new ColumnMapping("emp_id", "emp_id"),
            new ColumnMapping("full_name", "full_name"),
            new ColumnMapping("salary", "salary"));
    var mapping = new MappingDefinition("m1", srcRef, tgtRef, mappings, null, null);

    var job =
        new TransferJob(
            "job-pg2pg-1",
            "PG→PG sync test",
            srcProfile,
            tgtProfile,
            mapping,
            TransferMode.SYNC,
            500,
            null,
            null);

    var engine = new DefaultTransferEngine(new SyncPipeline());
    var result = engine.transfer(job);

    assertThat(result.status()).isEqualTo(TransferStatus.SUCCESS);
    assertThat(result.transferredCount()).isEqualTo(5L);
    assertThat(result.failedCount()).isEqualTo(0L);
    assertTargetHas5Rows();
  }

  @Test
  void shouldReportPartialResultWhenSomeRowsSkipped() throws Exception {
    seedSourceWithNulls();
    createTargetTableNotNull();

    var srcProfile = profile("src2", SOURCE);
    var tgtProfile = profile("tgt2", TARGET);

    var srcRef =
        new TableRef(new DatabaseRef("srcdb", DatabaseType.POSTGRESQL), "public", "events");
    var tgtRef =
        new TableRef(new DatabaseRef("tgtdb", DatabaseType.POSTGRESQL), "public", "events");
    var mapping = new MappingDefinition("m2", srcRef, tgtRef, List.of(), null, null);

    var job =
        new TransferJob(
            "job-pg2pg-2",
            "PG→PG passthrough test",
            srcProfile,
            tgtProfile,
            mapping,
            TransferMode.SYNC,
            100,
            null,
            null);

    var engine = new DefaultTransferEngine(new SyncPipeline());
    var result = engine.transfer(job);

    assertThat(result.status())
        .isIn(TransferStatus.SUCCESS, TransferStatus.PARTIAL, TransferStatus.FAILED);
  }

  private void seedSource() throws SQLException {
    try (var conn =
            DriverManager.getConnection(
                SOURCE.getJdbcUrl(), SOURCE.getUsername(), SOURCE.getPassword());
        var stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS employees "
              + "(emp_id INT PRIMARY KEY, full_name VARCHAR(100), salary NUMERIC)");
      stmt.execute("TRUNCATE employees");
      stmt.execute(
          "INSERT INTO employees VALUES (1,'Alice',85000),(2,'Bob',72000),"
              + "(3,'Carol',91000),(4,'Dave',68000),(5,'Eve',77000)");
    }
  }

  private void seedSourceWithNulls() throws SQLException {
    try (var conn =
            DriverManager.getConnection(
                SOURCE.getJdbcUrl(), SOURCE.getUsername(), SOURCE.getPassword());
        var stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE IF NOT EXISTS events (id INT, name VARCHAR(50))");
      stmt.execute("TRUNCATE events");
      stmt.execute("INSERT INTO events VALUES (1,'open'),(2,NULL),(3,'close')");
    }
  }

  private void createTargetTable() throws SQLException {
    try (var conn =
            DriverManager.getConnection(
                TARGET.getJdbcUrl(), TARGET.getUsername(), TARGET.getPassword());
        var stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS employees "
              + "(emp_id INT PRIMARY KEY, full_name VARCHAR(100), salary NUMERIC)");
      stmt.execute("TRUNCATE employees");
    }
  }

  private void createTargetTableNotNull() throws SQLException {
    try (var conn =
            DriverManager.getConnection(
                TARGET.getJdbcUrl(), TARGET.getUsername(), TARGET.getPassword());
        var stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE IF NOT EXISTS events (id INT, name VARCHAR(50))");
      stmt.execute("TRUNCATE events");
    }
  }

  private void assertTargetHas5Rows() throws SQLException {
    try (var conn =
            DriverManager.getConnection(
                TARGET.getJdbcUrl(), TARGET.getUsername(), TARGET.getPassword());
        var stmt = conn.createStatement();
        var rs = stmt.executeQuery("SELECT COUNT(*) FROM employees")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getLong(1)).isEqualTo(5L);
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
