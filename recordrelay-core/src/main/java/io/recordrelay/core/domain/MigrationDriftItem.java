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
 * A single structural discrepancy found during migration drift analysis.
 *
 * <p>For table-level diffs ({@link DriftKind#TABLE_MISSING}, {@link DriftKind#TABLE_EXTRA}), {@code
 * itemName} holds the table name and {@code columnName} is {@code null}. For column-level diffs,
 * {@code columnName} holds the column name and {@code itemName} holds the parent table name.
 */
public record MigrationDriftItem(
    DriftKind kind, String tableName, String columnName, String sourceDetail, String targetDetail) {

  public MigrationDriftItem {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(tableName, "tableName");
  }

  /** Severity category of a single drift finding. */
  public enum DriftKind {
    /** Table exists in source but is absent in target — likely a missing migration. */
    TABLE_MISSING,
    /** Table exists in target but has no counterpart in source — possibly a stale artifact. */
    TABLE_EXTRA,
    /** Column exists in source table but is absent in the corresponding target table. */
    COLUMN_MISSING,
    /** Column exists in target table but has no counterpart in the source table. */
    COLUMN_EXTRA,
    /** Column exists in both but the native types differ — potential type migration needed. */
    TYPE_MISMATCH
  }
}
