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
package io.recordrelay.core.clone.port.out;

/**
 * Receives progress events during a clone operation.
 *
 * <p>All methods have no-op default implementations, allowing callers to implement only the events
 * they care about. Use {@code new CloneProgressListener() {}} for a silent listener.
 */
public interface CloneProgressListener {

  /** Called once the root record has been fetched from the source. */
  default void onRootRecordLoaded(String tableName, String idValue) {}

  /** Called after the FK relationship graph has been resolved. */
  default void onRelationshipsDiscovered(int edgeCount) {}

  /** Called after new primary key identities have been allocated in the target. */
  default void onIdentitiesAllocated(int totalMappings) {}

  /** Called when row extraction begins for the given table. */
  default void onTableExtractionStarted(String tableName) {}

  /** Called when row extraction completes for the given table. */
  default void onTableExtractionCompleted(String tableName, long recordCount) {}

  /** Called when the import phase begins writing rows for the given table. */
  default void onImportStarted(String tableName) {}

  /** Called when the import phase finishes writing rows for the given table. */
  default void onImportCompleted(String tableName, long recordCount) {}

  /** Called when a non-fatal issue is detected during cloning. */
  default void onWarning(String message) {}
}
