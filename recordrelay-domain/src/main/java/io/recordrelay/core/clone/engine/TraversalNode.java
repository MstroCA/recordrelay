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
package io.recordrelay.core.clone.engine;

import java.util.Objects;

/**
 * A work item in the BFS traversal queue: a specific record to fetch, identified by table, ID
 * column, ID value, and current traversal depth.
 */
public record TraversalNode(String tableName, String idColumn, String idValue, int depth) {

  /** Validates required fields. */
  public TraversalNode {
    Objects.requireNonNull(tableName, "tableName");
    Objects.requireNonNull(idColumn, "idColumn");
    Objects.requireNonNull(idValue, "idValue");
    if (depth < 0) {
      throw new IllegalArgumentException("depth must be non-negative");
    }
  }

  /** Returns a unique string key used for visited-node tracking: {@code "tableName:idValue"}. */
  public String visitKey() {
    return tableName + ":" + idValue;
  }
}
