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

/**
 * Preview of what a satellite (companion-table) sync would do, produced during a dry run.
 *
 * @param table companion table name (e.g. {@code read_model})
 * @param sourceRowCount number of source rows that would be synced for the cloned root ids
 * @param skippedColumns source columns absent from the target table (they will not be written)
 * @param targetOnlyColumns target columns absent from the source (left to their default/null)
 */
public record SatellitePreview(
    String table,
    long sourceRowCount,
    List<String> skippedColumns,
    List<String> targetOnlyColumns) {

  public SatellitePreview {
    skippedColumns = skippedColumns == null ? List.of() : List.copyOf(skippedColumns);
    targetOnlyColumns = targetOnlyColumns == null ? List.of() : List.copyOf(targetOnlyColumns);
  }
}
