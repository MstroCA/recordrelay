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
 * Summary of what a clone operation would touch, without actually writing anything.
 *
 * <p>Produced by {@code DefaultCloneEngine.dryRun()}. Contains per-table row counts and traversal
 * depth so the user can verify scope before committing to a live clone.
 */
public record DryRunReport(
    String rootTable, String rootId, List<DryRunTableEntry> tables, long durationMillis) {

  public DryRunReport {
    Objects.requireNonNull(rootTable, "rootTable");
    Objects.requireNonNull(rootId, "rootId");
    tables = tables == null ? List.of() : List.copyOf(tables);
  }

  /**
   * Returns the total row count across all tables in this dry-run result.
   *
   * @return sum of row counts
   */
  public int totalRows() {
    return tables.stream().mapToInt(DryRunTableEntry::rowCount).sum();
  }

  /**
   * Returns the number of distinct tables visited during the dry run.
   *
   * @return table count
   */
  public int tableCount() {
    return tables.size();
  }
}
