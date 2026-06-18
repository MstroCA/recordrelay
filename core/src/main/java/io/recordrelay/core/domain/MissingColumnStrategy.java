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

/**
 * Controls how the transfer engine handles target columns that have no corresponding source value.
 *
 * <p>A column is considered "missing" when the source record does not contain the field that the
 * column mapping expects.
 */
public enum MissingColumnStrategy {
  /** Missing required columns cause the transfer to abort with an error. */
  FAIL,

  /** Rows with missing columns are skipped and written to the dead-letter destination. */
  SKIP_ROW,

  /**
   * Missing columns are filled with {@code null}. If the target column is NOT NULL, the write will
   * fail at the DB level unless a {@link ColumnMeta#defaultValue()} is configured.
   */
  NULL_FILL
}
