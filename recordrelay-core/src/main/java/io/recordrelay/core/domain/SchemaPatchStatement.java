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
 * A single SQL statement produced by the schema patch generator.
 *
 * <p>Destructive operations (DROP TABLE, DROP COLUMN, ALTER COLUMN TYPE) are flagged with {@code
 * destructive = true} and emitted as SQL comments so they are never accidentally applied.
 */
public record SchemaPatchStatement(
    PatchKind kind, String tableName, String columnName, String sql, boolean destructive) {

  public SchemaPatchStatement {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(tableName, "tableName");
    Objects.requireNonNull(sql, "sql");
  }

  /** Category of a schema patch operation. */
  public enum PatchKind {
    CREATE_TABLE,
    ADD_COLUMN,
    MODIFY_COLUMN_TYPE,
    DROP_TABLE,
    DROP_COLUMN
  }
}
