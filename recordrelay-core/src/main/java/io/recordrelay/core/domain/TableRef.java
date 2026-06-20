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
 * A reference to a table (SQL), collection (MongoDB), or equivalent structure in the target DB.
 *
 * <p>{@code schemaName} is optional and defaults to an empty string for databases that do not
 * support schemas (e.g., MongoDB, Redis).
 */
public record TableRef(DatabaseRef database, String schemaName, String tableName) {

  /** Validates required fields. {@code schemaName} defaults to {@code ""} when null. */
  public TableRef {
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(tableName, "tableName");
    if (tableName.isBlank()) {
      throw new IllegalArgumentException("tableName must not be blank");
    }
    schemaName = schemaName == null ? "" : schemaName;
  }

  /**
   * Returns the schema-qualified table name for display and SQL purposes.
   *
   * @return {@code "schema.table"} when schema is set, otherwise just {@code "table"}
   */
  public String qualifiedName() {
    return schemaName.isEmpty() ? tableName : schemaName + "." + tableName;
  }
}
