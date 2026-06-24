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
package io.recordrelay.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.recordrelay.core.domain.MigrationDriftItem.DriftKind;
import io.recordrelay.core.domain.RowCountEntry.RowStatus;
import io.recordrelay.core.domain.SchemaPatchStatement.PatchKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers domain value objects introduced with the drift / health / patch features. */
class NewDomainModelsTest {

  // ── MigrationDriftItem ────────────────────────────────────────────────────

  @Test
  void driftItemRequiresKind() {
    assertThatThrownBy(() -> new MigrationDriftItem(null, "orders", null, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void driftItemRequiresTableName() {
    assertThatThrownBy(
            () -> new MigrationDriftItem(DriftKind.TABLE_MISSING, null, null, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void driftItemAccessors() {
    var item =
        new MigrationDriftItem(DriftKind.TYPE_MISMATCH, "users", "age", "int", "varchar(50)");
    assertThat(item.kind()).isEqualTo(DriftKind.TYPE_MISMATCH);
    assertThat(item.tableName()).isEqualTo("users");
    assertThat(item.columnName()).isEqualTo("age");
    assertThat(item.sourceDetail()).isEqualTo("int");
    assertThat(item.targetDetail()).isEqualTo("varchar(50)");
  }

  // ── MigrationDriftReport ──────────────────────────────────────────────────

  @Test
  void emptyReportIsClean() {
    assertThat(new MigrationDriftReport(List.of()).isClean()).isTrue();
  }

  @Test
  void nullItemsDefaultsToEmpty() {
    assertThat(new MigrationDriftReport(null).items()).isEmpty();
  }

  @Test
  void reportCountsByKind() {
    var items =
        List.of(
            new MigrationDriftItem(DriftKind.TABLE_MISSING, "t1", null, null, null),
            new MigrationDriftItem(DriftKind.TABLE_MISSING, "t2", null, null, null),
            new MigrationDriftItem(DriftKind.COLUMN_EXTRA, "t3", "col", null, null));
    var report = new MigrationDriftReport(items);
    assertThat(report.isClean()).isFalse();
    assertThat(report.countByKind(DriftKind.TABLE_MISSING)).isEqualTo(2L);
    assertThat(report.countByKind(DriftKind.COLUMN_EXTRA)).isEqualTo(1L);
    assertThat(report.countByKind(DriftKind.TABLE_EXTRA)).isEqualTo(0L);
  }

  @Test
  void reportItemsAreImmutable() {
    var report = new MigrationDriftReport(List.of());
    assertThatThrownBy(
            () ->
                report
                    .items()
                    .add(new MigrationDriftItem(DriftKind.TABLE_EXTRA, "t", null, null, null)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // ── RowCountEntry ─────────────────────────────────────────────────────────

  @Test
  void rowCountEntryRequiresTableName() {
    assertThatThrownBy(() -> new RowCountEntry(null, 10, 10))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rowCountInSync() {
    var e = new RowCountEntry("orders", 100, 100);
    assertThat(e.status()).isEqualTo(RowStatus.IN_SYNC);
    assertThat(e.diff()).isEqualTo(0L);
    assertThat(e.hasUnsupported()).isFalse();
  }

  @Test
  void rowCountTargetBehind() {
    var e = new RowCountEntry("orders", 100, 80);
    assertThat(e.status()).isEqualTo(RowStatus.TARGET_BEHIND);
    assertThat(e.diff()).isEqualTo(20L);
  }

  @Test
  void rowCountTargetAhead() {
    var e = new RowCountEntry("orders", 50, 90);
    assertThat(e.status()).isEqualTo(RowStatus.TARGET_AHEAD);
    assertThat(e.diff()).isEqualTo(-40L);
  }

  @Test
  void rowCountUnsupportedWhenNegative() {
    var e = new RowCountEntry("orders", -1, 10);
    assertThat(e.status()).isEqualTo(RowStatus.UNSUPPORTED);
    assertThat(e.diff()).isEqualTo(0L);
    assertThat(e.hasUnsupported()).isTrue();
  }

  // ── SchemaPatchStatement ──────────────────────────────────────────────────

  @Test
  void patchStatementRequiresKind() {
    assertThatThrownBy(
            () ->
                new SchemaPatchStatement(
                    null, "orders", null, "CREATE TABLE orders (id INT)", false))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void patchStatementRequiresSql() {
    assertThatThrownBy(
            () -> new SchemaPatchStatement(PatchKind.CREATE_TABLE, "orders", null, null, false))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void patchStatementAccessors() {
    var stmt =
        new SchemaPatchStatement(
            PatchKind.ADD_COLUMN,
            "users",
            "email",
            "ALTER TABLE users ADD COLUMN email TEXT",
            false);
    assertThat(stmt.kind()).isEqualTo(PatchKind.ADD_COLUMN);
    assertThat(stmt.tableName()).isEqualTo("users");
    assertThat(stmt.columnName()).isEqualTo("email");
    assertThat(stmt.destructive()).isFalse();
  }

  // ── SchemaPatchScript ─────────────────────────────────────────────────────

  @Test
  void emptyScriptFullScript() {
    var script = new SchemaPatchScript(List.of(), DatabaseType.POSTGRESQL);
    assertThat(script.isEmpty()).isTrue();
    assertThat(script.fullScript()).contains("No schema differences");
    assertThat(script.safeCount()).isEqualTo(0L);
    assertThat(script.destructiveCount()).isEqualTo(0L);
  }

  @Test
  void scriptWithStatements() {
    var stmts =
        List.of(
            new SchemaPatchStatement(
                PatchKind.CREATE_TABLE, "logs", null, "CREATE TABLE logs (id INT)", false),
            new SchemaPatchStatement(
                PatchKind.DROP_TABLE, "old_logs", null, "-- DROP TABLE old_logs", true));
    var script = new SchemaPatchScript(stmts, DatabaseType.MYSQL);
    assertThat(script.isEmpty()).isFalse();
    assertThat(script.safeCount()).isEqualTo(1L);
    assertThat(script.destructiveCount()).isEqualTo(1L);
    assertThat(script.fullScript()).contains("MYSQL").contains("CREATE TABLE logs");
  }

  @Test
  void scriptNullStatementsDefaultsToEmpty() {
    var script = new SchemaPatchScript(null, DatabaseType.SQLITE);
    assertThat(script.statements()).isEmpty();
  }

  // ── ConnectionHealthEntry ─────────────────────────────────────────────────

  @Test
  void healthEntryLatencyDisplay() {
    var entry =
        new ConnectionHealthEntry(
            "prod", "localhost:5432", DatabaseType.POSTGRESQL, HealthStatus.Status.OK, 42L, "v15");
    assertThat(entry.latencyDisplay()).isEqualTo("42 ms");
  }

  @Test
  void healthEntryUnknownLatencyDisplay() {
    var entry =
        new ConnectionHealthEntry(
            "dev", "host", DatabaseType.MYSQL, HealthStatus.Status.DOWN, -1L, "refused");
    assertThat(entry.latencyDisplay()).isEqualTo("—");
  }

  @Test
  void healthEntryRequiresConnName() {
    assertThatThrownBy(
            () ->
                new ConnectionHealthEntry(
                    null, "host", DatabaseType.MYSQL, HealthStatus.Status.OK, 0L, "ok"))
        .isInstanceOf(NullPointerException.class);
  }

  // ── HealthStatus ──────────────────────────────────────────────────────────

  @Test
  void healthStatusOkFactory() {
    var hs = HealthStatus.ok(10L);
    assertThat(hs.status()).isEqualTo(HealthStatus.Status.OK);
    assertThat(hs.latencyMs()).isEqualTo(10L);
    assertThat(hs.detail()).isEqualTo("Reachable");
  }

  @Test
  void healthStatusDownFactory() {
    var hs = HealthStatus.down("Connection refused");
    assertThat(hs.status()).isEqualTo(HealthStatus.Status.DOWN);
    assertThat(hs.latencyMs()).isEqualTo(-1L);
    assertThat(hs.detail()).isEqualTo("Connection refused");
  }

  // ── QueryResult ───────────────────────────────────────────────────────────

  @Test
  void queryResultRowCount() {
    var r =
        new QueryResult(List.of("id", "name"), List.of(List.of("1", "Alice"), List.of("2", "Bob")));
    assertThat(r.rowCount()).isEqualTo(2);
    assertThat(r.columns()).containsExactly("id", "name");
  }

  @Test
  void queryResultNullDefaults() {
    var r = new QueryResult(null, null);
    assertThat(r.columns()).isEmpty();
    assertThat(r.rows()).isEmpty();
    assertThat(r.rowCount()).isEqualTo(0);
  }

  // ── RootTableCandidate ────────────────────────────────────────────────────

  @Test
  void rootTableCandidateBadgeLabelWithInDegree() {
    var c = new RootTableCandidate("orders", 10, 5, 1, "high in-degree");
    assertThat(c.badgeLabel()).isEqualTo("orders  ·  ×5");
  }

  @Test
  void rootTableCandidateBadgeLabelNoInDegree() {
    var c = new RootTableCandidate("logs", 1, 0, 2, "leaf node");
    assertThat(c.badgeLabel()).isEqualTo("logs");
  }

  @Test
  void rootTableCandidateRequiresTableName() {
    assertThatThrownBy(() -> new RootTableCandidate(null, 0, 0, 0, "reason"))
        .isInstanceOf(NullPointerException.class);
  }

  // ── ColumnCompatibility ───────────────────────────────────────────────────

  @Test
  void columnCompatibilityAccessors() {
    var cc = new ColumnCompatibility("src_col", "tgt_col", true, null);
    assertThat(cc.sourceColumn()).isEqualTo("src_col");
    assertThat(cc.targetColumn()).isEqualTo("tgt_col");
    assertThat(cc.typeCompatible()).isTrue();
    assertThat(cc.warning()).isNull();
  }

  @Test
  void columnCompatibilityWithWarning() {
    var cc = new ColumnCompatibility(null, "tgt_col", false, "type mismatch");
    assertThat(cc.typeCompatible()).isFalse();
    assertThat(cc.warning()).isEqualTo("type mismatch");
  }
}
