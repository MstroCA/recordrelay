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

import java.util.List;
import java.util.Objects;

/**
 * Summary produced after a clone operation completes.
 *
 * <p>Example output:
 *
 * <pre>
 * Clone Summary
 * Root Record : customer:12345
 * Tables      : 8
 * Records     : 12,543
 * Duration    : 45s
 * Warnings    : 2
 * Masked Fields: 4,821
 * </pre>
 */
public record CloneReport(
    String rootTable,
    String rootId,
    List<ClonedTableSummary> tableSummaries,
    long durationMillis,
    List<String> warnings,
    long maskedFieldCount) {

  /** Validates required fields and copies lists. */
  public CloneReport {
    Objects.requireNonNull(rootTable, "rootTable");
    Objects.requireNonNull(rootId, "rootId");
    tableSummaries = tableSummaries == null ? List.of() : List.copyOf(tableSummaries);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  /** Total records across all cloned tables. */
  public long totalRecords() {
    return tableSummaries.stream().mapToLong(ClonedTableSummary::recordCount).sum();
  }

  /** Number of distinct tables cloned. */
  public int tableCount() {
    return tableSummaries.size();
  }

  /** Duration formatted as a human-readable string (e.g., {@code "45s"} or {@code "1m 23s"}). */
  public String formattedDuration() {
    long seconds = durationMillis / 1_000;
    if (seconds < 60) {
      return seconds + "s";
    }
    return (seconds / 60) + "m " + (seconds % 60) + "s";
  }
}
