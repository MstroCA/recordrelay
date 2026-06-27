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
package io.recordrelay.core.port.out;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import java.util.Map;

/** Writes records to a storage engine table. */
public interface RecordWriter extends AutoCloseable {

  /** Opens a writer targeting {@code table} using {@code profile}. */
  void open(ConnectionProfile profile, TableRef table) throws ConnectorException;

  /**
   * Opens a writer with conflict-skip semantics.
   *
   * <p>When {@code skipExisting} is {@code true} the writer uses database-specific syntax to
   * silently skip rows that would violate a unique constraint (e.g. {@code ON CONFLICT DO
   * NOTHING}). Implementations that do not support this mode fall back to a plain insert.
   */
  default void open(ConnectionProfile profile, TableRef table, boolean skipExisting)
      throws ConnectorException {
    open(profile, table);
  }

  /** Writes a single record to the target table. */
  void write(DataRecord record) throws ConnectorException;

  /** Flushes any buffered writes to the underlying storage. */
  void flush() throws ConnectorException;

  /** Flushes, commits, and releases any underlying resources. */
  @Override
  void close() throws ConnectorException;

  /**
   * Returns PK remaps accumulated during the last write operation and clears the internal buffer.
   *
   * <p>A remap entry {@code attempted → existing} is recorded when a row fails with a unique
   * constraint violation: the attempted target PK could not be inserted because another row already
   * occupies a unique slot, and {@code existing} is the PK of that pre-existing row. Callers use
   * these remaps to fix FK references in subsequent tables before writing them.
   */
  default Map<String, String> drainConflictRemaps() {
    return Map.of();
  }
}
