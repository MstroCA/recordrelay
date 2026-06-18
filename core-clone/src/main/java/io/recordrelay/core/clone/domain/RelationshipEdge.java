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
 * A directed edge in the {@link RelationshipGraph}.
 *
 * <p>Represents: {@code fromNode.fromColumn → toNode.toColumn}. For example, {@code
 * orders.customer_id → customers.id} describes that {@code orders.customer_id} references the
 * primary key of the {@code customers} table.
 */
public record RelationshipEdge(
    RelationshipNode fromNode,
    String fromColumn,
    RelationshipNode toNode,
    String toColumn,
    RelationshipSource source,
    double confidence) {

  /** Validates all required fields. Confidence must be in [0.0, 1.0]. */
  public RelationshipEdge {
    Objects.requireNonNull(fromNode, "fromNode");
    Objects.requireNonNull(fromColumn, "fromColumn");
    Objects.requireNonNull(toNode, "toNode");
    Objects.requireNonNull(toColumn, "toColumn");
    Objects.requireNonNull(source, "source");
    if (confidence < 0.0 || confidence > 1.0) {
      throw new IllegalArgumentException("confidence must be in [0.0, 1.0], got: " + confidence);
    }
  }

  /**
   * Returns a human-readable description of this relationship.
   *
   * @return e.g. {@code "orders.customer_id -> customers.id [FK, confidence=0.95]"}
   */
  public String describe() {
    return String.format(
        "%s.%s -> %s.%s [%s, confidence=%.2f]",
        fromNode.tableName(), fromColumn, toNode.tableName(), toColumn, source, confidence);
  }
}
