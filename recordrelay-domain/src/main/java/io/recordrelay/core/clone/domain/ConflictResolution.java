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

/**
 * Strategy applied when a clone operation would write records into a target that may already
 * contain data.
 *
 * <p>The default is {@link #REGENERATE_IDENTITIES} — every entity in the target receives a fresh
 * primary key, ensuring no collision with pre-existing data.
 */
public enum ConflictResolution {

  /**
   * Allocate brand-new primary keys for every cloned record and remap all foreign keys accordingly.
   * This is the safest default and works for any target state.
   *
   * <p>After writing, database sequences / auto-increment counters are advanced past the highest
   * allocated ID so that subsequent inserts by the application do not collide.
   */
  REGENERATE_IDENTITIES,

  /**
   * Scope all cloned records to an isolated namespace by prefixing or offsetting IDs.
   *
   * <p>Useful when multiple cloned contexts must coexist in the same target schema without
   * interfering with each other or with pre-existing data.
   */
  ISOLATE_NAMESPACE,

  /**
   * Refuse to write any data if the target already contains records in any of the affected tables.
   *
   * <p>Provides the strongest safety guarantee: only succeeds on a clean target. Throws {@link
   * io.recordrelay.core.clone.exception.CloneException} if a conflict is detected before the first
   * write.
   */
  FAIL_SAFE,

  /**
   * Skip any record that would violate a unique or primary-key constraint in the target.
   *
   * <p>Translates to database-specific syntax: PostgreSQL uses {@code ON CONFLICT DO NOTHING},
   * MySQL/MariaDB uses {@code INSERT IGNORE}, SQLite uses {@code INSERT OR IGNORE}.
   *
   * <p>Useful when the target may already contain the same data (e.g. same-to-same DB cloning or
   * repeated imports).
   */
  SKIP_EXISTING
}
