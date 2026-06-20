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

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Summary of a comparison between the state of a business entity in two environments.
 *
 * <p>A diff report answers: "is the customer-123 context in staging consistent with production?" It
 * surfaces missing records, changed field values, and unexpected additions.
 */
public record DiffReport(
    String entityName,
    String entityId,
    String leftEnvironment,
    String rightEnvironment,
    Instant generatedAt,
    List<DiffEntry> entries,
    int missingCount,
    int changedCount,
    int extraCount) {

  public DiffReport {
    Objects.requireNonNull(entityName, "entityName");
    Objects.requireNonNull(leftEnvironment, "leftEnvironment");
    Objects.requireNonNull(rightEnvironment, "rightEnvironment");
    Objects.requireNonNull(generatedAt, "generatedAt");
    entries = entries == null ? List.of() : List.copyOf(entries);
  }

  /** Returns true when both environments contain identical context for this entity. */
  public boolean isIdentical() {
    return missingCount == 0 && changedCount == 0 && extraCount == 0;
  }

  /** Returns the total number of discrepancies found. */
  public int totalDiscrepancies() {
    return missingCount + changedCount + extraCount;
  }

  /**
   * A single discrepancy between the two environments.
   *
   * @param tableName the table where the difference was found
   * @param recordId the primary-key value of the record
   * @param kind the type of difference
   * @param detail human-readable description of what differs
   */
  public record DiffEntry(String tableName, String recordId, DiffKind kind, String detail) {}

  /** Type of difference between two environment snapshots. */
  public enum DiffKind {
    /** Record exists in right environment but not in left. */
    MISSING_IN_LEFT,
    /** Record exists in left environment but not in right. */
    MISSING_IN_RIGHT,
    /** Record exists in both environments but field values differ. */
    FIELD_CHANGED,
    /** Record exists in right but was not expected based on the left context. */
    EXTRA_IN_RIGHT
  }
}
