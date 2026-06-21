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

import java.util.Objects;

/**
 * Overrides a column value in cloned records.
 *
 * <p>Solves the tenant-isolation problem where user-context fields (e.g. {@code created_by},
 * {@code mukellef_vkn}) must match the target environment user rather than the original source
 * values. Without overrides, cloned records may be invisible to the logged-in user in the target.
 *
 * <pre>
 * // Apply to ALL tables containing this column:
 * FieldOverride.global("created_by", "TEST_USER_001")
 *
 * // Apply only to beyanname table:
 * FieldOverride.forTable("beyanname", "mukellef_vkn", "1234567890")
 * </pre>
 */
public record FieldOverride(String tableName, String column, String value) {

  public FieldOverride {
    Objects.requireNonNull(column, "column");
    Objects.requireNonNull(value, "value");
    if (column.isBlank()) {
      throw new IllegalArgumentException("column must not be blank");
    }
  }

  /** Creates an override that applies to ALL tables containing the given column. */
  public static FieldOverride global(String column, String value) {
    return new FieldOverride(null, column, value);
  }

  /** Creates an override that applies only to the specified table. */
  public static FieldOverride forTable(String tableName, String column, String value) {
    Objects.requireNonNull(tableName, "tableName");
    return new FieldOverride(tableName, column, value);
  }

  /** Returns true when this override applies to the given table. */
  public boolean appliesTo(String table) {
    return tableName == null || tableName.equalsIgnoreCase(table);
  }

  /** Returns true when this override applies globally (no specific table). */
  public boolean isGlobal() {
    return tableName == null;
  }
}
