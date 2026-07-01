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
package io.recordrelay.core.clone.port.out;

import io.recordrelay.core.clone.domain.ClonedTableSummary;
import io.recordrelay.core.clone.domain.FieldOverrideConfig;
import io.recordrelay.core.clone.domain.IdentityMapping;
import io.recordrelay.core.clone.domain.SatelliteTable;
import io.recordrelay.core.clone.exception.CloneException;
import java.util.Collection;
import java.util.List;

/**
 * Propagates the primary clone into companion databases by copying {@link SatelliteTable} rows that
 * reference the cloned root entity.
 *
 * <p>Runs after the primary clone has written the root and all in-database context. For each
 * satellite the implementation reads the companion rows keyed by the <em>source</em> root ids,
 * remaps the link column to the newly allocated <em>target</em> root ids via {@code rootMapping},
 * regenerates the companion primary key to avoid collisions, applies the same field overrides, and
 * writes the rows into the satellite's target database.
 */
public interface SatelliteSyncPort {

  /**
   * Synchronises all configured satellites.
   *
   * @param rootTable the primary clone's root table (e.g. {@code beyanname})
   * @param sourceRootIds source primary-key values of the cloned root rows
   * @param rootMapping identity mapping produced by the primary clone; used to translate {@code
   *     linkColumn} values from source ids to the freshly allocated target ids
   * @param satellites companion tables to synchronise
   * @param overrides field overrides to apply to companion rows (same config as the primary clone)
   * @param listener progress callbacks (reuses the clone progress channel)
   * @return per-satellite write summaries plus any non-fatal warnings
   */
  SatelliteSyncResult sync(
      String rootTable,
      Collection<String> sourceRootIds,
      IdentityMapping rootMapping,
      List<SatelliteTable> satellites,
      FieldOverrideConfig overrides,
      CloneProgressListener listener)
      throws CloneException;

  /** Outcome of a satellite synchronisation: written summaries and non-fatal warnings. */
  record SatelliteSyncResult(List<ClonedTableSummary> summaries, List<String> warnings) {
    public SatelliteSyncResult {
      summaries = summaries == null ? List.of() : List.copyOf(summaries);
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** Returns a result with no summaries and no warnings. */
    public static SatelliteSyncResult empty() {
      return new SatelliteSyncResult(List.of(), List.of());
    }
  }
}
