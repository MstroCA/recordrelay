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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Covers domain value objects that are not exercised by dedicated test classes. */
class DomainModelTest {

  // ── Credentials ──────────────────────────────────────────────────────────

  @Test
  void credentialsShouldDefaultEmptyPassword() {
    var c = Credentials.of("alice");
    assertThat(c.username()).isEqualTo("alice");
    assertThat(c.password()).isEmpty();
  }

  @Test
  void credentialsShouldNullifyNullPassword() {
    var c = new Credentials("bob", null);
    assertThat(c.password()).isEmpty();
  }

  // ── DatabaseRef ───────────────────────────────────────────────────────────

  @Test
  void databaseRefShouldRejectBlankName() {
    assertThatThrownBy(() -> new DatabaseRef("", DatabaseType.POSTGRESQL))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void databaseRefShouldRejectNullType() {
    assertThatThrownBy(() -> new DatabaseRef("db", null)).isInstanceOf(NullPointerException.class);
  }

  // ── TableRef ──────────────────────────────────────────────────────────────

  @Test
  void tableRefQualifiedNameWithSchema() {
    var db = new DatabaseRef("mydb", DatabaseType.POSTGRESQL);
    var t = new TableRef(db, "public", "users");
    assertThat(t.qualifiedName()).isEqualTo("public.users");
  }

  @Test
  void tableRefQualifiedNameWithoutSchema() {
    var db = new DatabaseRef("mydb", DatabaseType.MONGODB);
    var t = new TableRef(db, null, "products");
    assertThat(t.qualifiedName()).isEqualTo("products");
    assertThat(t.schemaName()).isEmpty();
  }

  @Test
  void tableRefShouldRejectBlankTableName() {
    var db = new DatabaseRef("mydb", DatabaseType.POSTGRESQL);
    assertThatThrownBy(() -> new TableRef(db, "public", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ── ColumnMeta ────────────────────────────────────────────────────────────

  @Test
  void columnMetaShouldRejectNegativeOrdinal() {
    assertThatThrownBy(() -> new ColumnMeta("col", "varchar", true, false, false, -1, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void columnMetaShouldAllowNullDefault() {
    var col = new ColumnMeta("id", "serial", false, true, false, 1, null);
    assertThat(col.defaultValue()).isNull();
    assertThat(col.primaryKey()).isTrue();
  }

  // ── ColumnMapping ─────────────────────────────────────────────────────────

  @Test
  void passthroughMappingShouldEqualSourceAndTarget() {
    var m = ColumnMapping.passthrough("email");
    assertThat(m.isPassthrough()).isTrue();
    assertThat(m.sourceColumn()).isEqualTo(m.targetColumn());
  }

  @Test
  void aliasMappingShouldNotBePassthrough() {
    var m = new ColumnMapping("user_name", "username");
    assertThat(m.isPassthrough()).isFalse();
  }

  @Test
  void columnMappingShouldRejectBlankSource() {
    assertThatThrownBy(() -> new ColumnMapping("", "col"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ── ValidationResult ─────────────────────────────────────────────────────

  @Test
  void validationResultOkShouldBeValid() {
    var r = ValidationResult.ok();
    assertThat(r.valid()).isTrue();
    assertThat(r.errors()).isEmpty();
    assertThat(r.warnings()).isEmpty();
  }

  @Test
  void validationResultOkWithWarnings() {
    var r = ValidationResult.ok(List.of("type mismatch"));
    assertThat(r.valid()).isTrue();
    assertThat(r.warnings()).hasSize(1);
  }

  @Test
  void validationResultFailedShouldBeInvalid() {
    var errors = List.of(ValidationError.error("col", "missing"));
    var r = ValidationResult.failed(errors);
    assertThat(r.valid()).isFalse();
    assertThat(r.errors()).hasSize(1);
  }

  // ── ValidationError ───────────────────────────────────────────────────────

  @Test
  void validationErrorFactoriesShouldSetSeverity() {
    assertThat(ValidationError.error("f", "msg").severity()).isEqualTo(ValidationSeverity.ERROR);
    assertThat(ValidationError.warning("f", "msg").severity())
        .isEqualTo(ValidationSeverity.WARNING);
  }

  @Test
  void validationErrorShouldDefaultNullFieldToEmpty() {
    var e = new ValidationError(null, "msg", ValidationSeverity.ERROR);
    assertThat(e.field()).isEmpty();
  }

  // ── DataRecord ────────────────────────────────────────────────────────────

  @Test
  void dataRecordShouldReturnFieldValues() {
    var rec = DataRecord.of(Map.of("name", "Alice", "age", 30));
    assertThat(rec.get("name")).isEqualTo("Alice");
    assertThat(rec.hasField("age")).isTrue();
    assertThat(rec.hasField("missing")).isFalse();
    assertThat(rec.fieldNames()).containsExactlyInAnyOrder("name", "age");
  }

  @Test
  void dataRecordShouldBeImmutable() {
    var rec = DataRecord.of(Map.of("k", "v"));
    assertThatThrownBy(() -> rec.fields().put("k2", "v2"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // ── TransferError ─────────────────────────────────────────────────────────

  @Test
  void batchErrorShouldHaveRowNumberMinusOne() {
    var e = TransferError.batchError("flush failed", "users");
    assertThat(e.rowNumber()).isEqualTo(-1L);
    assertThat(e.tableName()).isEqualTo("users");
  }

  // ── SchemaMatchReport ─────────────────────────────────────────────────────

  @Test
  void perfectMatchShouldBeFullyCompatible() {
    var report = new SchemaMatchReport(100.0, List.of(), List.of());
    assertThat(report.isFullyCompatible()).isTrue();
  }

  @Test
  void partialMatchShouldNotBeFullyCompatible() {
    var report = new SchemaMatchReport(75.0, List.of(), List.of());
    assertThat(report.isFullyCompatible()).isFalse();
  }

  @Test
  void schemaMatchReportShouldRejectInvalidPercentage() {
    assertThatThrownBy(() -> new SchemaMatchReport(101.0, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ── TransactionIsolation ──────────────────────────────────────────────────

  @Test
  void transactionIsolationJdbcConstantsShouldMatchJdbcSpec() {
    assertThat(TransactionIsolation.READ_UNCOMMITTED.jdbcConstant()).isEqualTo(1);
    assertThat(TransactionIsolation.READ_COMMITTED.jdbcConstant()).isEqualTo(2);
    assertThat(TransactionIsolation.REPEATABLE_READ.jdbcConstant()).isEqualTo(4);
    assertThat(TransactionIsolation.SERIALIZABLE.jdbcConstant()).isEqualTo(8);
  }

  // ── MappingDefinition ─────────────────────────────────────────────────────

  @Test
  void emptyMappingDefinitionShouldReportEmpty() {
    var src = new TableRef(new DatabaseRef("s", DatabaseType.POSTGRESQL), "public", "a");
    var tgt = new TableRef(new DatabaseRef("t", DatabaseType.MONGODB), "", "b");
    var m = new MappingDefinition("m1", src, tgt, null, null, null);
    assertThat(m.isEmpty()).isTrue();
    assertThat(m.format()).isEqualTo(MappingFormat.DIRECT);
  }

  @Test
  void mappingDefinitionWithColumnsShouldNotBeEmpty() {
    var src = new TableRef(new DatabaseRef("s", DatabaseType.POSTGRESQL), "public", "a");
    var tgt = new TableRef(new DatabaseRef("t", DatabaseType.MONGODB), "", "b");
    var cols = List.of(ColumnMapping.passthrough("id"));
    var m = new MappingDefinition("m2", src, tgt, cols, null, null);
    assertThat(m.isEmpty()).isFalse();
    assertThat(m.columnMappings()).hasSize(1);
  }
}
