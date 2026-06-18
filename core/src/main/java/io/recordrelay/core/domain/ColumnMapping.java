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

import java.util.Objects;

/**
 * A directional mapping from a source column to a target column, optionally aliased and
 * transformed.
 *
 * <p>When {@code sourceColumn} and {@code targetColumn} are identical the mapping is a passthrough.
 * Different names enable rename-on-transfer (alias). {@code transform} is an optional transform
 * spec string (e.g., {@code "trim"}, {@code "cast:int"}, {@code "date-format:yyyy-MM-dd"}).
 */
public record ColumnMapping(String sourceColumn, String targetColumn, String transform) {

  /** Validates that neither column name is blank. */
  public ColumnMapping {
    Objects.requireNonNull(sourceColumn, "sourceColumn");
    Objects.requireNonNull(targetColumn, "targetColumn");
    if (sourceColumn.isBlank()) {
      throw new IllegalArgumentException("sourceColumn must not be blank");
    }
    if (targetColumn.isBlank()) {
      throw new IllegalArgumentException("targetColumn must not be blank");
    }
  }

  /** Backwards-compatible 2-arg constructor; transform defaults to {@code null}. */
  public ColumnMapping(String sourceColumn, String targetColumn) {
    this(sourceColumn, targetColumn, null);
  }

  /**
   * Creates a passthrough mapping where source and target column names are identical.
   *
   * @param columnName the shared column name
   * @return a passthrough mapping
   */
  public static ColumnMapping passthrough(String columnName) {
    return new ColumnMapping(columnName, columnName, null);
  }

  /** Returns true when this is a passthrough (no rename). */
  public boolean isPassthrough() {
    return sourceColumn.equals(targetColumn);
  }
}
