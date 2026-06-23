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
 * A scored candidate for the root (starting) table of a BFS clone traversal.
 *
 * <p>The score is based on FK graph topology: tables that are referenced by many others (high
 * in-degree) and have few outgoing FK references themselves (low out-degree) are more likely to be
 * central domain entities — and therefore good root-table choices.
 *
 * @param tableName table name as returned by the schema inspector
 * @param score composite score; higher = better root candidate
 * @param inDegree number of other tables that have a FK pointing to this table
 * @param outDegree number of FK references that originate from this table
 * @param reason human-readable explanation of the score
 */
public record RootTableCandidate(
    String tableName, int score, int inDegree, int outDegree, String reason) {

  public RootTableCandidate {
    Objects.requireNonNull(tableName, "tableName");
    Objects.requireNonNull(reason, "reason");
  }

  /** Short badge text suitable for a button label, e.g. {@code "orders  ·  ×5"}. */
  public String badgeLabel() {
    if (inDegree > 0) {
      return tableName + "  ·  ×" + inDegree;
    }
    return tableName;
  }
}
