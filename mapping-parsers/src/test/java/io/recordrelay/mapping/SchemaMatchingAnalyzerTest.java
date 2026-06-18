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
package io.recordrelay.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import io.recordrelay.core.domain.ColumnMeta;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaMatchingAnalyzerTest {

  private static ColumnMeta col(String name, String type) {
    return new ColumnMeta(name, type, true, false, false, 0, null);
  }

  @Test
  void perfectMatchReturns100Percent() {
    var src = List.of(col("id", "int4"), col("email", "varchar"));
    var tgt = List.of(col("id", "int4"), col("email", "varchar"));
    var report = SchemaMatchingAnalyzer.analyze(src, tgt);
    assertThat(report.matchPercentage()).isEqualTo(100.0);
    assertThat(report.isFullyCompatible()).isTrue();
    assertThat(report.warnings()).isEmpty();
  }

  @Test
  void emptyTargetReturns100Percent() {
    var src = List.of(col("id", "int4"));
    var report = SchemaMatchingAnalyzer.analyze(src, List.of());
    assertThat(report.matchPercentage()).isEqualTo(100.0);
  }

  @Test
  void missingTargetColumnReducesMatchPercent() {
    var src = List.of(col("id", "int4"));
    var tgt = List.of(col("id", "int4"), col("email", "varchar"));
    var report = SchemaMatchingAnalyzer.analyze(src, tgt);
    assertThat(report.matchPercentage()).isEqualTo(50.0);
    assertThat(report.warnings()).hasSize(1);
  }

  @Test
  void typeMismatchReducesMatchAndAddsWarning() {
    var src = List.of(col("age", "varchar"));
    var tgt = List.of(col("age", "int4"));
    var report = SchemaMatchingAnalyzer.analyze(src, tgt);
    assertThat(report.matchPercentage()).isEqualTo(0.0);
    assertThat(report.warnings()).hasSize(1);
    assertThat(report.warnings().get(0)).contains("cast:");
  }

  @Test
  void caseInsensitiveNameMatchCounts() {
    var src = List.of(col("EMAIL", "varchar"));
    var tgt = List.of(col("email", "varchar"));
    var report = SchemaMatchingAnalyzer.analyze(src, tgt);
    assertThat(report.matchPercentage()).isEqualTo(100.0);
  }

  @Test
  void suggestionProvidedForSimilarColumnName() {
    var src = List.of(col("customer_id", "int4"));
    var tgt = List.of(col("id", "int4"));
    var report = SchemaMatchingAnalyzer.analyze(src, tgt);
    assertThat(report.matchPercentage()).isEqualTo(0.0);
    assertThat(report.warnings()).hasSize(1);
  }

  @Test
  void columnCompatibilityListHasOneEntryPerTargetColumn() {
    var src = List.of(col("a", "int4"), col("b", "varchar"));
    var tgt = List.of(col("a", "int4"), col("b", "varchar"), col("c", "bool"));
    var report = SchemaMatchingAnalyzer.analyze(src, tgt);
    assertThat(report.columnCompatibilities()).hasSize(3);
  }

  @Test
  void fullyCompatibleReturnsFalseWhenWarningsPresent() {
    var src = List.of(col("id", "int4"), col("name", "varchar"));
    var tgt = List.of(col("id", "bigint"));
    var report = SchemaMatchingAnalyzer.analyze(src, tgt);
    assertThat(report.isFullyCompatible()).isFalse();
  }
}
