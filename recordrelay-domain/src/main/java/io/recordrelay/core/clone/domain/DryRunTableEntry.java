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

import java.util.Objects;

/**
 * Per-table summary produced by a clone dry run.
 *
 * <p>{@code minDepth} is the traversal hop count at which this table was first reached — {@code 0}
 * for the root table, {@code 1} for its immediate neighbours, and so on.
 */
public record DryRunTableEntry(String tableName, int rowCount, int minDepth) {

  public DryRunTableEntry {
    Objects.requireNonNull(tableName, "tableName");
    if (rowCount < 0) {
      throw new IllegalArgumentException("rowCount must be >= 0");
    }
    if (minDepth < 0) {
      throw new IllegalArgumentException("minDepth must be >= 0");
    }
  }
}
