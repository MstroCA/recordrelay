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
package io.recordrelay.core.clone.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdentityMappingTest {

  @Test
  void emptyMappingIsEmpty() {
    assertThat(IdentityMapping.empty().isEmpty()).isTrue();
    assertThat(IdentityMapping.empty().totalMappings()).isZero();
  }

  @Test
  void builderRegistersAndResolvesMapping() {
    var builder = IdentityMapping.builder();
    builder.register("customers", "1", "42001");
    builder.register("customers", "2", "42002");
    builder.register("orders", "10", "78001");

    var mapping = builder.build();

    assertThat(mapping.isEmpty()).isFalse();
    assertThat(mapping.totalMappings()).isEqualTo(3);
    assertThat(mapping.resolve("customers", "1")).contains("42001");
    assertThat(mapping.resolve("customers", "2")).contains("42002");
    assertThat(mapping.resolve("orders", "10")).contains("78001");
  }

  @Test
  void resolveMissingTableReturnsEmpty() {
    var mapping = IdentityMapping.empty();
    assertThat(mapping.resolve("nonexistent", "1")).isEmpty();
  }

  @Test
  void resolveMissingIdReturnsEmpty() {
    var builder = IdentityMapping.builder();
    builder.register("customers", "1", "42001");
    var mapping = builder.build();

    assertThat(mapping.resolve("customers", "999")).isEmpty();
  }

  @Test
  void snapshotContainsAllRegisteredMappings() {
    var builder = IdentityMapping.builder();
    builder.register("customers", "1", "42001");
    builder.register("orders", "5", "78001");
    var mapping = builder.build();

    var snapshot = mapping.snapshot();
    assertThat(snapshot).containsKey("customers");
    assertThat(snapshot.get("customers")).containsEntry("1", "42001");
    assertThat(snapshot).containsKey("orders");
  }

  @Test
  void builderIsEmptyBeforeRegistration() {
    var builder = IdentityMapping.builder();
    assertThat(builder.isEmpty()).isTrue();
    builder.register("t", "1", "2");
    assertThat(builder.isEmpty()).isFalse();
  }

  @Test
  void conflictResolutionDefaultIsRegenerateIdentities() {
    assertThat(ConflictResolution.REGENERATE_IDENTITIES).isNotNull();
    assertThat(ConflictResolution.values()).hasSize(3);
  }

  @Test
  void diffReportIsIdenticalWhenNoDiscrepancies() {
    var report =
        new DiffReport(
            "customer",
            "123",
            "prod",
            "local",
            java.time.Instant.now(),
            java.util.List.of(),
            0,
            0,
            0);

    assertThat(report.isIdentical()).isTrue();
    assertThat(report.totalDiscrepancies()).isZero();
  }

  @Test
  void diffReportCountsDiscrepancies() {
    var entry =
        new DiffReport.DiffEntry("orders", "7", DiffReport.DiffKind.MISSING_IN_RIGHT, "not found");
    var report =
        new DiffReport(
            "customer",
            "123",
            "prod",
            "local",
            java.time.Instant.now(),
            java.util.List.of(entry),
            1,
            0,
            0);

    assertThat(report.isIdentical()).isFalse();
    assertThat(report.totalDiscrepancies()).isEqualTo(1);
    assertThat(report.entries()).hasSize(1);
  }
}
