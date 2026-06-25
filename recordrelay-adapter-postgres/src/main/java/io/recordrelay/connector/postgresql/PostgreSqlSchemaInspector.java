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
package io.recordrelay.connector.postgresql;

import io.recordrelay.connector.postgresql.internal.DataSourceFactory;
import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.RootTableCandidate;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.SchemaInspector;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@link SchemaInspector} adapter for PostgreSQL.
 *
 * <p>Uses {@code information_schema} views for portability across PostgreSQL versions. Primary key
 * detection is performed via a LEFT JOIN on {@code table_constraints}.
 */
public final class PostgreSqlSchemaInspector implements SchemaInspector {

  private static final String LIST_TABLES_SQL =
      "SELECT table_schema, table_name "
          + "FROM information_schema.tables "
          + "WHERE table_catalog = ? AND table_type = 'BASE TABLE' "
          + "  AND table_schema NOT IN ('pg_catalog', 'information_schema') "
          + "ORDER BY table_schema, table_name";

  private static final String LIST_TABLES_SCHEMA_SQL =
      "SELECT table_schema, table_name "
          + "FROM information_schema.tables "
          + "WHERE table_catalog = ? AND table_type = 'BASE TABLE' "
          + "  AND table_schema = ? "
          + "ORDER BY table_schema, table_name";

  private static final String LIST_COLUMNS_SQL =
      "SELECT c.column_name, c.data_type, c.is_nullable, c.column_default, "
          + "       c.ordinal_position, "
          + "       CASE WHEN tc.constraint_type = 'PRIMARY KEY' THEN true ELSE false END AS pk "
          + "FROM information_schema.columns c "
          + "LEFT JOIN information_schema.key_column_usage kcu "
          + "       ON kcu.table_catalog = c.table_catalog "
          + "      AND kcu.table_schema  = c.table_schema "
          + "      AND kcu.table_name    = c.table_name "
          + "      AND kcu.column_name   = c.column_name "
          + "LEFT JOIN information_schema.table_constraints tc "
          + "       ON tc.constraint_name = kcu.constraint_name "
          + "      AND tc.constraint_type = 'PRIMARY KEY' "
          + "WHERE c.table_catalog = ? AND c.table_schema = ? AND c.table_name = ? "
          + "ORDER BY c.ordinal_position";

  @Override
  public List<TableRef> listTables(ConnectionProfile profile, DatabaseRef database)
      throws ConnectorException {
    String schemaOverride = profile.properties().get("currentSchema");
    String sql = schemaOverride != null ? LIST_TABLES_SCHEMA_SQL : LIST_TABLES_SQL;
    try (var ds = DataSourceFactory.create(profile);
        var conn = ds.getConnection();
        var stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, database.name());
      if (schemaOverride != null) {
        stmt.setString(2, schemaOverride);
      }
      var result = new ArrayList<TableRef>();
      try (var rs = stmt.executeQuery()) {
        while (rs.next()) {
          result.add(
              new TableRef(database, rs.getString("table_schema"), rs.getString("table_name")));
        }
      }
      return List.copyOf(result);
    } catch (SQLException e) {
      throw new ConnectorException(
          "Failed to list tables in '" + database.name() + "': " + e.getMessage(), e);
    }
  }

  @Override
  public List<RootTableCandidate> detectRootCandidates(
      ConnectionProfile profile, DatabaseRef database) throws ConnectorException {
    try (var ds = DataSourceFactory.create(profile);
        var conn = ds.getConnection()) {
      var meta = conn.getMetaData();
      var tables = loadUserTables(meta, database);
      if (tables.isEmpty()) {
        return List.of();
      }
      var tableNames = tableNameSet(tables);
      var inDegree = new HashMap<String, Integer>();
      var outDegree = new HashMap<String, Integer>();
      for (var t : tables) {
        String key = t.tableName().toLowerCase(Locale.ROOT);
        inDegree.put(key, 0);
        outDegree.put(key, 0);
      }
      analyzeRelationships(meta, tables, tableNames, inDegree, outDegree);
      return rankCandidates(tables, inDegree, outDegree);
    } catch (SQLException e) {
      throw new ConnectorException("Root-table detection failed: " + e.getMessage(), e);
    }
  }

  private static List<TableRef> loadUserTables(java.sql.DatabaseMetaData meta, DatabaseRef database)
      throws SQLException {
    var tables = new ArrayList<TableRef>();
    try (var rs = meta.getTables(null, null, "%", new String[] {"TABLE"})) {
      while (rs.next()) {
        String schema = rs.getString("TABLE_SCHEM");
        if (isPgSystemSchema(schema)) {
          continue;
        }
        String tbl = rs.getString("TABLE_NAME");
        tables.add(new TableRef(database, schema != null ? schema : "", tbl));
      }
    }
    return tables;
  }

  private static Set<String> tableNameSet(List<TableRef> tables) {
    var names = new HashSet<String>(tables.size());
    for (var t : tables) {
      names.add(t.tableName().toLowerCase(Locale.ROOT));
    }
    return names;
  }

  private static void analyzeRelationships(
      java.sql.DatabaseMetaData meta,
      List<TableRef> tables,
      Set<String> tableNames,
      Map<String, Integer> inDegree,
      Map<String, Integer> outDegree) {
    for (var t : tables) {
      String schema = t.schemaName().isBlank() ? null : t.schemaName();
      String tKey = t.tableName().toLowerCase(Locale.ROOT);
      var knownOut = scanForeignKeys(meta, schema, tKey, t.tableName(), inDegree, outDegree);
      scanHeuristicColumns(meta, schema, t.tableName(), tableNames, knownOut, inDegree, outDegree);
    }
  }

  private static Set<String> scanForeignKeys(
      java.sql.DatabaseMetaData meta,
      String schema,
      String tKey,
      String tableName,
      Map<String, Integer> inDegree,
      Map<String, Integer> outDegree) {
    var knownOut = new HashSet<String>();
    try (var fks = meta.getImportedKeys(null, schema, tableName)) {
      while (fks.next()) {
        String pkTable = fks.getString("PKTABLE_NAME").toLowerCase(Locale.ROOT);
        String fkCol = fks.getString("FKCOLUMN_NAME").toLowerCase(Locale.ROOT);
        outDegree.merge(tKey, 1, Integer::sum);
        inDegree.merge(pkTable, 1, Integer::sum);
        knownOut.add(fkCol);
      }
    } catch (SQLException ignored) {
      // table may be inaccessible
    }
    return knownOut;
  }

  private static void scanHeuristicColumns(
      java.sql.DatabaseMetaData meta,
      String schema,
      String tableName,
      Set<String> tableNames,
      Set<String> knownOut,
      Map<String, Integer> inDegree,
      Map<String, Integer> outDegree) {
    String tKey = tableName.toLowerCase(Locale.ROOT);
    try (var cols = meta.getColumns(null, schema, tableName, "%")) {
      while (cols.next()) {
        String colName = cols.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
        if (!colName.endsWith("_id") || knownOut.contains(colName)) {
          continue;
        }
        String base = colName.substring(0, colName.length() - 3);
        String refTable = resolveRefTable(base, tableNames);
        if (refTable == null || refTable.equals(tKey)) {
          continue;
        }
        outDegree.merge(tKey, 1, Integer::sum);
        inDegree.merge(refTable, 1, Integer::sum);
      }
    } catch (SQLException ignored) {
      // column scan not supported
    }
  }

  private static boolean isPgSystemSchema(String schema) {
    if (schema == null) {
      return false;
    }
    var s = schema.toLowerCase(Locale.ROOT);
    return s.equals("information_schema") || s.equals("pg_catalog") || s.startsWith("pg_");
  }

  private static String resolveRefTable(String base, Set<String> tableNames) {
    for (var candidate : List.of(base, base + "s", base + "es")) {
      if (tableNames.contains(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private static List<RootTableCandidate> rankCandidates(
      List<TableRef> tables, Map<String, Integer> inDegree, Map<String, Integer> outDegree) {
    var candidates = new ArrayList<RootTableCandidate>(tables.size());
    for (var t : tables) {
      String key = t.tableName().toLowerCase(Locale.ROOT);
      int in = inDegree.getOrDefault(key, 0);
      int out = outDegree.getOrDefault(key, 0);
      int score = in * 3 - out + nameBonus(t.tableName());
      String reason =
          in > 0 || out > 0
              ? in + " table(s) reference this table, " + out + " outgoing reference(s)"
              : "no declared relationships found — selected by name pattern";
      candidates.add(new RootTableCandidate(t.tableName(), score, in, out, reason));
    }
    candidates.sort(Comparator.comparingInt(RootTableCandidate::score).reversed());
    int limit = Math.min(5, candidates.size());
    return List.copyOf(candidates.subList(0, limit));
  }

  private static int nameBonus(String tableName) {
    String lower = tableName.toLowerCase(Locale.ROOT);
    for (String root :
        List.of(
            "order",
            "customer",
            "account",
            "user",
            "patient",
            "employee",
            "invoice",
            "contract",
            "project",
            "subscription",
            "transaction",
            "ticket",
            "request",
            "case",
            "sale",
            "booking",
            "reservation")) {
      if (lower.equals(root) || lower.startsWith(root + "_") || lower.endsWith("_" + root)) {
        return 5;
      }
    }
    return 0;
  }

  @Override
  public List<ColumnMeta> inspectColumns(ConnectionProfile profile, TableRef table)
      throws ConnectorException {
    try (var ds = DataSourceFactory.create(profile);
        var conn = ds.getConnection();
        var stmt = conn.prepareStatement(LIST_COLUMNS_SQL)) {
      stmt.setString(1, table.database().name());
      stmt.setString(2, table.schemaName());
      stmt.setString(3, table.tableName());
      var result = new ArrayList<ColumnMeta>();
      try (var rs = stmt.executeQuery()) {
        while (rs.next()) {
          result.add(
              new ColumnMeta(
                  rs.getString("column_name"),
                  rs.getString("data_type"),
                  "YES".equals(rs.getString("is_nullable")),
                  rs.getBoolean("pk"),
                  false,
                  rs.getInt("ordinal_position"),
                  rs.getString("column_default")));
        }
      }
      return List.copyOf(result);
    } catch (SQLException e) {
      throw new ConnectorException(
          "Failed to inspect columns of '" + table.qualifiedName() + "': " + e.getMessage(), e);
    }
  }
}
