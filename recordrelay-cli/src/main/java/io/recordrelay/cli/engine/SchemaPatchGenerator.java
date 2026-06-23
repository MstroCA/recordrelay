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
package io.recordrelay.cli.engine;

import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.domain.MigrationDriftItem;
import io.recordrelay.core.domain.MigrationDriftItem.DriftKind;
import io.recordrelay.core.domain.MigrationDriftReport;
import io.recordrelay.core.domain.SchemaPatchScript;
import io.recordrelay.core.domain.SchemaPatchStatement;
import io.recordrelay.core.domain.SchemaPatchStatement.PatchKind;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.ContextProviderPort;
import io.recordrelay.core.spi.ConnectorRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Converts a {@link MigrationDriftReport} into an executable SQL patch script.
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>TABLE_MISSING → {@code CREATE TABLE} (safe, fetches column definitions from source)
 *   <li>COLUMN_MISSING → {@code ALTER TABLE … ADD COLUMN} (safe)
 *   <li>TYPE_MISMATCH → {@code ALTER TABLE … ALTER/MODIFY COLUMN} (commented out — potentially
 *       breaking)
 *   <li>TABLE_EXTRA → {@code DROP TABLE} (commented out — destructive)
 *   <li>COLUMN_EXTRA → {@code DROP COLUMN} (commented out — destructive)
 * </ul>
 *
 * <p>SQL syntax is dialect-aware for PostgreSQL, MySQL/MariaDB, SQL Server, and Oracle. All other
 * types fall back to ANSI SQL.
 */
public final class SchemaPatchGenerator {

  private final Function<ConnectionProfile, ContextProviderPort> connectorLookup;

  public SchemaPatchGenerator() {
    this(ConnectorRegistry::findConnector);
  }

  SchemaPatchGenerator(Function<ConnectionProfile, ContextProviderPort> connectorLookup) {
    this.connectorLookup = connectorLookup;
  }

  /**
   * Generates a SQL patch script for the given drift report.
   *
   * @param report drift analysis result
   * @param sourceProfile connection to the source (reference) database server
   * @param sourceDb the source database
   * @param targetDialect SQL dialect to use for generated statements
   * @return a patch script ready to review and apply
   */
  public SchemaPatchScript generate(
      MigrationDriftReport report,
      ConnectionProfile sourceProfile,
      DatabaseRef sourceDb,
      DatabaseType targetDialect) {

    if (report.isClean()) {
      return new SchemaPatchScript(List.of(), targetDialect);
    }

    // Collect source columns for each missing table (needed for CREATE TABLE)
    Map<String, List<ColumnMeta>> missingTableColumns =
        fetchMissingTableColumns(report, sourceProfile, sourceDb);

    var statements = new ArrayList<SchemaPatchStatement>();

    // Order: CREATE TABLE → ADD COLUMN → MODIFY TYPE → DROP COLUMN → DROP TABLE
    for (var item : report.items()) {
      if (item.kind() == DriftKind.TABLE_MISSING) {
        List<ColumnMeta> cols =
            missingTableColumns.getOrDefault(item.tableName().toLowerCase(), List.of());
        statements.add(buildCreateTable(item.tableName(), cols, targetDialect));
      }
    }
    for (var item : report.items()) {
      if (item.kind() == DriftKind.COLUMN_MISSING) {
        statements.add(
            buildAddColumn(
                item.tableName(), item.columnName(), item.sourceDetail(), targetDialect));
      }
    }
    for (var item : report.items()) {
      if (item.kind() == DriftKind.TYPE_MISMATCH) {
        statements.add(
            buildModifyColumn(
                item.tableName(),
                item.columnName(),
                item.sourceDetail(),
                item.targetDetail(),
                targetDialect));
      }
    }
    for (var item : report.items()) {
      if (item.kind() == DriftKind.COLUMN_EXTRA) {
        statements.add(buildDropColumn(item.tableName(), item.columnName(), targetDialect));
      }
    }
    for (var item : report.items()) {
      if (item.kind() == DriftKind.TABLE_EXTRA) {
        statements.add(buildDropTable(item.tableName(), targetDialect));
      }
    }

    return new SchemaPatchScript(statements, targetDialect);
  }

  // ── Statement builders ────────────────────────────────────────────────────

  private SchemaPatchStatement buildCreateTable(
      String table, List<ColumnMeta> cols, DatabaseType dialect) {
    var sb = new StringBuilder("CREATE TABLE ");
    sb.append(quote(table, dialect)).append(" (");
    if (cols.isEmpty()) {
      sb.append("\n    -- column definitions unavailable\n");
    } else {
      for (int i = 0; i < cols.size(); i++) {
        var col = cols.get(i);
        sb.append("\n    ").append(quote(col.name(), dialect)).append(" ").append(col.nativeType());
        if (col.primaryKey()) sb.append(" PRIMARY KEY");
        if (!col.nullable() && !col.primaryKey()) sb.append(" NOT NULL");
        if (col.defaultValue() != null) sb.append(" DEFAULT ").append(col.defaultValue());
        if (i < cols.size() - 1) sb.append(",");
      }
      sb.append("\n");
    }
    sb.append(");");
    return new SchemaPatchStatement(PatchKind.CREATE_TABLE, table, null, sb.toString(), false);
  }

  private SchemaPatchStatement buildAddColumn(
      String table, String column, String type, DatabaseType dialect) {
    String sql =
        "ALTER TABLE "
            + quote(table, dialect)
            + " ADD COLUMN "
            + quote(column, dialect)
            + " "
            + type
            + ";";
    // MySQL/MariaDB don't use ADD COLUMN keyword — just ADD
    if (dialect == DatabaseType.MYSQL || dialect == DatabaseType.MARIADB) {
      sql =
          "ALTER TABLE "
              + quote(table, dialect)
              + " ADD "
              + quote(column, dialect)
              + " "
              + type
              + ";";
    }
    return new SchemaPatchStatement(PatchKind.ADD_COLUMN, table, column, sql, false);
  }

  private SchemaPatchStatement buildModifyColumn(
      String table, String column, String srcType, String tgtType, DatabaseType dialect) {
    String inner = modifyColumnSql(table, column, srcType, dialect);
    String sql =
        "-- TYPE MISMATCH on "
            + table
            + "."
            + column
            + " (source: "
            + srcType
            + ", target: "
            + tgtType
            + ")\n"
            + "-- Review before applying:\n"
            + "-- "
            + inner;
    return new SchemaPatchStatement(PatchKind.MODIFY_COLUMN_TYPE, table, column, sql, true);
  }

  private SchemaPatchStatement buildDropColumn(String table, String column, DatabaseType dialect) {
    String inner =
        "ALTER TABLE " + quote(table, dialect) + " DROP COLUMN " + quote(column, dialect) + ";";
    String sql = "-- EXTRA COLUMN " + table + "." + column + " (not in source)\n-- " + inner;
    return new SchemaPatchStatement(PatchKind.DROP_COLUMN, table, column, sql, true);
  }

  private SchemaPatchStatement buildDropTable(String table, DatabaseType dialect) {
    String inner = "DROP TABLE " + quote(table, dialect) + ";";
    String sql = "-- EXTRA TABLE " + table + " (not in source)\n-- " + inner;
    return new SchemaPatchStatement(PatchKind.DROP_TABLE, table, null, sql, true);
  }

  // ── Dialect helpers ───────────────────────────────────────────────────────

  private static String modifyColumnSql(
      String table, String column, String type, DatabaseType dialect) {
    return switch (dialect) {
      case POSTGRESQL ->
          "ALTER TABLE "
              + quotePostgres(table)
              + " ALTER COLUMN "
              + quotePostgres(column)
              + " TYPE "
              + type
              + ";";
      case MYSQL, MARIADB ->
          "ALTER TABLE `" + table + "` MODIFY COLUMN `" + column + "` " + type + ";";
      case SQLSERVER -> "ALTER TABLE [" + table + "] ALTER COLUMN [" + column + "] " + type + ";";
      case ORACLE -> "ALTER TABLE \"" + table + "\" MODIFY (\"" + column + "\" " + type + ");";
      default -> "ALTER TABLE " + table + " ALTER COLUMN " + column + " " + type + ";";
    };
  }

  private static String quote(String identifier, DatabaseType dialect) {
    return switch (dialect) {
      case MYSQL, MARIADB -> "`" + identifier + "`";
      case SQLSERVER -> "[" + identifier + "]";
      case ORACLE -> "\"" + identifier + "\"";
      default -> quotePostgres(identifier);
    };
  }

  private static String quotePostgres(String identifier) {
    return "\"" + identifier + "\"";
  }

  // ── Column fetch for CREATE TABLE ─────────────────────────────────────────

  private Map<String, List<ColumnMeta>> fetchMissingTableColumns(
      MigrationDriftReport report, ConnectionProfile sourceProfile, DatabaseRef sourceDb) {

    List<String> missingTables =
        report.items().stream()
            .filter(i -> i.kind() == DriftKind.TABLE_MISSING)
            .map(MigrationDriftItem::tableName)
            .collect(Collectors.toList());

    if (missingTables.isEmpty()) return Map.of();

    try {
      var inspector = connectorLookup.apply(sourceProfile).schemaInspector();
      var allSrcTables = inspector.listTables(sourceProfile, sourceDb);

      Map<String, TableRef> tableIndex =
          allSrcTables.stream()
              .collect(
                  Collectors.toMap(
                      t -> t.tableName().toLowerCase(), Function.identity(), (a, b) -> a));

      Map<String, List<ColumnMeta>> result = new LinkedHashMap<>();
      for (String name : missingTables) {
        TableRef ref = tableIndex.get(name.toLowerCase());
        if (ref != null) {
          result.put(name.toLowerCase(), inspector.inspectColumns(sourceProfile, ref));
        }
      }
      return result;
    } catch (ConnectorException e) {
      // Return empty — CREATE TABLE will be generated without column definitions
      return Map.of();
    }
  }
}
