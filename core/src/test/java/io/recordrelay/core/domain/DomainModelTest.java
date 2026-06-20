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
}
