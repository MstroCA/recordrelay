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
 * Row count comparison for a single table between source and target databases.
 *
 * <p>A count of {@code -1} means the connector does not support row counting (e.g. NoSQL engines).
 * {@link #status()} classifies the relationship between the two counts.
 */
public record RowCountEntry(String tableName, long sourceCount, long targetCount) {

  public RowCountEntry {
    Objects.requireNonNull(tableName, "tableName");
  }

  /**
   * Returns the source minus target row count delta ({@code 0} when either count is unsupported).
   *
   * @return signed row count difference
   */
  public long diff() {
    if (sourceCount < 0 || targetCount < 0) {
      return 0;
    }
    return sourceCount - targetCount;
  }

  /**
   * Returns {@code true} when at least one side does not support row counting.
   *
   * @return {@code true} if unsupported
   */
  public boolean hasUnsupported() {
    return sourceCount < 0 || targetCount < 0;
  }

  /**
   * Classifies the row count relationship between source and target.
   *
   * @return the {@link RowStatus} for this entry
   */
  public RowStatus status() {
    if (sourceCount < 0 || targetCount < 0) {
      return RowStatus.UNSUPPORTED;
    }
    if (sourceCount == targetCount) {
      return RowStatus.IN_SYNC;
    }
    if (targetCount < sourceCount) {
      return RowStatus.TARGET_BEHIND;
    }
    return RowStatus.TARGET_AHEAD;
  }

  /** Classification of the row count relationship. */
  public enum RowStatus {
    IN_SYNC,
    TARGET_BEHIND,
    TARGET_AHEAD,
    UNSUPPORTED
  }
}
