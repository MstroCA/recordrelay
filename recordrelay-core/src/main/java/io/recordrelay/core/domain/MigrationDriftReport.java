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

import io.recordrelay.core.domain.MigrationDriftItem.DriftKind;
import java.util.List;

/**
 * Aggregated result of a full database-level migration drift analysis.
 *
 * <p>Contains a flat list of every discrepancy found — missing tables, extra tables, missing
 * columns, extra columns, and type mismatches — across all tables visible in both source and target
 * databases.
 */
public record MigrationDriftReport(List<MigrationDriftItem> items) {

  public MigrationDriftReport {
    items = items == null ? List.of() : List.copyOf(items);
  }

  /**
   * Returns the number of drift items with the given {@link DriftKind}.
   *
   * @param kind the drift category to count
   * @return count of matching items
   */
  public long countByKind(DriftKind kind) {
    return items.stream().filter(i -> i.kind() == kind).count();
  }

  /**
   * Returns {@code true} when no drift items were found.
   *
   * @return {@code true} if source and target are in sync
   */
  public boolean isClean() {
    return items.isEmpty();
  }
}
