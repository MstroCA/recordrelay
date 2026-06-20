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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DatabaseType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CloneRequestTest {

  private static ConnectionProfile profile(String id) {
    return new ConnectionProfile(
        id,
        "test",
        "env-1",
        DatabaseType.POSTGRESQL,
        "localhost",
        5432,
        "testdb",
        new Credentials("user", "pass"),
        Map.of());
  }

  @Test
  void builderDefaultDepthIsThree() {
    var request = CloneRequest.builder(profile("src"), profile("tgt"), "customers", "42").build();
    assertThat(request.depth()).isEqualTo(CloneRequest.DEFAULT_DEPTH);
  }

  @Test
  void builderCustomDepthIsApplied() {
    var request =
        CloneRequest.builder(profile("src"), profile("tgt"), "customers", "42").depth(5).build();
    assertThat(request.depth()).isEqualTo(5);
  }

  @Test
  void nullMaskingDefaultsToNoneConfig() {
    var request =
        new CloneRequest(profile("src"), profile("tgt"), "customers", "42", 3, null, null);
    assertThat(request.masking().isEmpty()).isTrue();
  }

  @Test
  void nullConflictResolutionDefaultsToRegenerateIdentities() {
    var request =
        new CloneRequest(profile("src"), profile("tgt"), "customers", "42", 3, null, null);
    assertThat(request.conflictResolution()).isEqualTo(ConflictResolution.REGENERATE_IDENTITIES);
  }

  @Test
  void blankRootTableThrowsException() {
    assertThatThrownBy(
            () -> new CloneRequest(profile("src"), profile("tgt"), "  ", "42", 3, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rootTable");
  }

  @Test
  void blankRootIdThrowsException() {
    assertThatThrownBy(
            () ->
                new CloneRequest(profile("src"), profile("tgt"), "customers", "  ", 3, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rootId");
  }

  @Test
  void depthZeroThrowsException() {
    assertThatThrownBy(
            () ->
                new CloneRequest(profile("src"), profile("tgt"), "customers", "42", 0, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("depth");
  }

  @Test
  void depthExceedingMaxThrowsException() {
    assertThatThrownBy(
            () ->
                new CloneRequest(
                    profile("src"),
                    profile("tgt"),
                    "customers",
                    "42",
                    CloneRequest.MAX_DEPTH + 1,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("depth");
  }

  @Test
  void cloneJobOfGeneratesUniqueIds() {
    var request = CloneRequest.builder(profile("src"), profile("tgt"), "orders", "1").build();
    var job1 = CloneJob.of(request);
    var job2 = CloneJob.of(request);
    assertThat(job1.id()).isNotEqualTo(job2.id());
    assertThat(job1.createdAt()).isNotNull();
  }

  @Test
  void cloneReportTotalRecordsSumsAllTables() {
    var report =
        new CloneReport(
            "customers",
            "42",
            java.util.List.of(
                new ClonedTableSummary("customers", 1),
                new ClonedTableSummary("orders", 5),
                new ClonedTableSummary("invoices", 12)),
            3000L,
            java.util.List.of(),
            0L);

    assertThat(report.totalRecords()).isEqualTo(18);
    assertThat(report.tableCount()).isEqualTo(3);
    assertThat(report.formattedDuration()).isEqualTo("3s");
  }

  @Test
  void cloneReportFormattedDurationMinuteFormat() {
    var report = new CloneReport("t", "1", java.util.List.of(), 90_000L, java.util.List.of(), 0L);
    assertThat(report.formattedDuration()).isEqualTo("1m 30s");
  }
}
