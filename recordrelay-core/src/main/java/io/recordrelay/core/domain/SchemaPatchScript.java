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

import java.util.List;

/**
 * The complete SQL patch script produced by the schema patch generator.
 *
 * <p>Destructive statements are emitted as SQL comments (prefixed with {@code --}) so they can be
 * reviewed before being applied. Safe statements (CREATE TABLE, ADD COLUMN) are emitted as
 * executable SQL.
 */
public record SchemaPatchScript(List<SchemaPatchStatement> statements, DatabaseType targetDialect) {

  public SchemaPatchScript {
    statements = statements == null ? List.of() : List.copyOf(statements);
  }

  /** Returns all statements rendered as a single SQL string, one statement per line. */
  public String fullScript() {
    if (statements.isEmpty()) {
      return "-- No schema differences found. Target is in sync with source.";
    }
    var sb = new StringBuilder();
    sb.append("-- RecordRelay SQL Patch Script\n");
    sb.append("-- Target dialect: ").append(targetDialect).append("\n");
    sb.append("-- Destructive statements are commented out — review before applying.\n\n");

    SchemaPatchStatement.PatchKind lastKind = null;
    for (var stmt : statements) {
      if (stmt.kind() != lastKind) {
        sb.append("\n-- ").append(sectionHeader(stmt.kind())).append("\n");
        lastKind = stmt.kind();
      }
      sb.append(stmt.sql()).append("\n");
    }
    return sb.toString();
  }

  public boolean isEmpty() {
    return statements.isEmpty();
  }

  /**
   * Returns the number of non-destructive statements in this script.
   *
   * @return count of safe statements
   */
  public long safeCount() {
    return statements.stream().filter(s -> !s.destructive()).count();
  }

  /**
   * Returns the number of destructive (commented-out) statements in this script.
   *
   * @return count of destructive statements
   */
  public long destructiveCount() {
    return statements.stream().filter(SchemaPatchStatement::destructive).count();
  }

  private static String sectionHeader(SchemaPatchStatement.PatchKind kind) {
    return switch (kind) {
      case CREATE_TABLE -> "Missing tables — CREATE TABLE";
      case ADD_COLUMN -> "Missing columns — ALTER TABLE ADD COLUMN";
      case MODIFY_COLUMN_TYPE -> "Type mismatches — ALTER COLUMN (review carefully)";
      case DROP_TABLE -> "Extra tables — DROP TABLE (destructive, commented out)";
      case DROP_COLUMN -> "Extra columns — DROP COLUMN (destructive, commented out)";
        // Explicit default keeps this non-exhaustive so javac emits no java.lang.MatchException
        // reference (absent on IntelliJ 2024.1 / JBR 17, which bundles this module).
      default -> kind.name();
    };
  }
}
