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
package io.recordrelay.core.engine;

import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.DeadLetterRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Accumulates rows that could not be transferred and optionally writes them to a CSV file.
 *
 * <p>In-memory collection is always performed. File output is activated by supplying a non-null
 * {@code deadLetterPath} to the constructor.
 */
public final class DeadLetterCollector {

  private final List<DeadLetterRecord> records = new ArrayList<>();
  private final String deadLetterPath;

  /**
   * Creates a collector.
   *
   * @param deadLetterPath filesystem path for CSV output; {@code null} disables file output
   */
  public DeadLetterCollector(String deadLetterPath) {
    this.deadLetterPath = deadLetterPath;
  }

  /**
   * Records a failed row.
   *
   * @param record the source record that could not be processed
   * @param errorMessage description of the failure
   * @param tableName the target table that was being written
   * @param rowNumber zero-based row index in the source cursor
   */
  public void collect(DataRecord record, String errorMessage, String tableName, long rowNumber) {
    Objects.requireNonNull(record, "record");
    records.add(new DeadLetterRecord(record, errorMessage, tableName, rowNumber));
  }

  /** Returns all collected dead-letter records as an unmodifiable list. */
  public List<DeadLetterRecord> getRecords() {
    return List.copyOf(records);
  }

  /** Returns the number of rows collected so far. */
  public int size() {
    return records.size();
  }

  /** Returns true if any rows have been collected. */
  public boolean hasEntries() {
    return !records.isEmpty();
  }

  /** Returns the configured dead-letter path, or {@code null} if file output is disabled. */
  public String deadLetterPath() {
    return deadLetterPath;
  }
}
