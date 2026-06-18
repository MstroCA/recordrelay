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

/**
 * Signals that a single row should be skipped and routed to the dead-letter destination rather than
 * aborting the whole transfer.
 *
 * <p>Thrown by {@link MappingTransformer} when {@link
 * io.recordrelay.core.domain.MissingColumnStrategy#SKIP_ROW} or {@link
 * io.recordrelay.core.domain.TypeCoercionStrategy#SKIP_ROW} is active.
 */
final class SkipRowException extends RuntimeException {

  private final DataRecord sourceRecord;

  SkipRowException(String message, DataRecord sourceRecord) {
    super(message);
    this.sourceRecord = sourceRecord;
  }

  DataRecord sourceRecord() {
    return sourceRecord;
  }
}
