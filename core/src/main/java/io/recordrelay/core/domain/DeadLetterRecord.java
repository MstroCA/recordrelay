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
 * A single row that could not be written to the target and was diverted to the dead-letter
 * destination.
 *
 * @param originalRecord the source record that failed transformation or writing
 * @param errorMessage human-readable description of the failure
 * @param tableName the target table that was being written when the failure occurred
 * @param rowNumber the zero-based position of this row in the source cursor
 */
public record DeadLetterRecord(
    DataRecord originalRecord, String errorMessage, String tableName, long rowNumber) {

  /** Validates required fields. */
  public DeadLetterRecord {
    Objects.requireNonNull(originalRecord, "originalRecord");
    Objects.requireNonNull(errorMessage, "errorMessage");
    Objects.requireNonNull(tableName, "tableName");
    if (rowNumber < 0) {
      throw new IllegalArgumentException("rowNumber must be >= 0");
    }
  }
}
