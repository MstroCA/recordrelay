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

/** A vertex in the {@link RelationshipGraph} representing a single database table. */
public record RelationshipNode(String tableName) {

  /** Validates that the table name is non-blank. */
  public RelationshipNode {
    Objects.requireNonNull(tableName, "tableName");
    if (tableName.isBlank()) {
      throw new IllegalArgumentException("tableName must not be blank");
    }
  }
}
