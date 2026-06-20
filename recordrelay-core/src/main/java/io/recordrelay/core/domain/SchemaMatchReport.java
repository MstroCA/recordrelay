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

import java.util.List;

/**
 * Result of a structural compatibility analysis between a source and target schema.
 *
 * <p>{@code matchPercentage} is {@code 100.0} when every target column has a compatible source
 * counterpart. Values below {@code 100.0} indicate missing or type-mismatched columns.
 */
public record SchemaMatchReport(
    double matchPercentage,
    List<ColumnCompatibility> columnCompatibilities,
    List<String> warnings) {

  /** Validates percentage range and produces immutable list copies. */
  public SchemaMatchReport {
    if (matchPercentage < 0.0 || matchPercentage > 100.0) {
      throw new IllegalArgumentException("matchPercentage must be between 0 and 100");
    }
    columnCompatibilities =
        columnCompatibilities == null ? List.of() : List.copyOf(columnCompatibilities);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  /**
   * Returns true when all target columns are present in the source with compatible types.
   *
   * @return true for a perfect schema match
   */
  public boolean isFullyCompatible() {
    return Double.compare(matchPercentage, 100.0) == 0 && warnings.isEmpty();
  }
}
