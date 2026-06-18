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
 * A single row-level or batch-level error captured during transfer execution.
 *
 * <p>{@code rowNumber} is {@code -1} when the error is not associated with a specific row (e.g., a
 * batch flush failure).
 */
public record TransferError(String message, String tableName, long rowNumber) {

  /** Validates required fields. */
  public TransferError {
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(tableName, "tableName");
  }

  /**
   * Creates an error not associated with a specific row.
   *
   * @param message human-readable error description
   * @param tableName the table being written when the error occurred
   * @return a batch-level error
   */
  public static TransferError batchError(String message, String tableName) {
    return new TransferError(message, tableName, -1L);
  }
}
