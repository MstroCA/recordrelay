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
package io.recordrelay.connector.jdbc;

import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.RootTableCandidate;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.SchemaInspector;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JDBC schema inspector that uses {@link DatabaseMetaData} for portability across SQL engines.
 *
 * <p>Subclasses provide the JDBC scheme. Primary key detection uses {@link
 * DatabaseMetaData#getPrimaryKeys}.
 */
public abstract class AbstractJdbcSchemaInspector implements SchemaInspector {

  /**
   * Returns the JDBC URL sub-protocol for this engine.
   *
   * @return JDBC scheme (e.g., "mysql")
   */
  protected abstract String jdbcScheme();

  @Override
  public List<TableRef> listTables(ConnectionProfile profile, DatabaseRef database)
      throws ConnectorException {
    try (var ds = JdbcDataSourceFactory.create(profile, jdbcScheme());
        var conn = ds.getConnection()) {
      var meta = conn.getMetaData();
      var result = new ArrayList<TableRef>();
      try (var rs = meta.getTables(database.name(), null, "%", new String[] {"TABLE"})) {
        while (rs.next()) {
          String schema = rs.getString("TABLE_SCHEM");
          String tbl = rs.getString("TABLE_NAME");
          result.add(new TableRef(database, schema != null ? schema : "", tbl));
        }
      }
      return List.copyOf(result);
    } catch (SQLException e) {
      throw new ConnectorException("Failed to list tables: " + e.getMessage(), e);
    }
  }

  @Override
  public List<ColumnMeta> inspectColumns(ConnectionProfile profile, TableRef table)
      throws ConnectorException {
    try (var ds = JdbcDataSourceFactory.create(profile, jdbcScheme());
        var conn = ds.getConnection()) {
      var meta = conn.getMetaData();
      var pkCols = getPrimaryKeys(meta, table);
      var result = new ArrayList<ColumnMeta>();
      String schemaPattern = table.schemaName().isBlank() ? null : table.schemaName();
      try (ResultSet rs =
          meta.getColumns(table.database().name(), schemaPattern, table.tableName(), "%")) {
        while (rs.next()) {
          result.add(buildColumnMeta(rs, pkCols));
        }
      }
      return List.copyOf(result);
    } catch (SQLException e) {
      throw new ConnectorException("Failed to inspect columns: " + e.getMessage(), e);
    }
  }

  @Override
  public long countRows(ConnectionProfile profile, TableRef table) throws ConnectorException {
    String qualified =
        table.schemaName().isBlank()
            ? quoteName(table.tableName())
            : quoteName(table.schemaName()) + "." + quoteName(table.tableName());
    try (var ds = JdbcDataSourceFactory.create(profile, jdbcScheme());
        var conn = ds.getConnection();
        var stmt = conn.createStatement();
        var rs = stmt.executeQuery("SELECT COUNT(*) FROM " + qualified)) {
      return rs.next() ? rs.getLong(1) : 0L;
    } catch (SQLException e) {
      throw new ConnectorException(
          "Failed to count rows in " + table.tableName() + ": " + e.getMessage(), e);
    }
  }

  @Override
  public List<RootTableCandidate> detectRootCandidates(
      ConnectionProfile profile, DatabaseRef database) throws ConnectorException {
    try (var ds = JdbcDataSourceFactory.create(profile, jdbcScheme());
        var conn = ds.getConnection()) {
      var meta = conn.getMetaData();
      var tables = collectTables(meta, database);
      if (tables.isEmpty()) {
        return List.of();
      }
      var inDegree = new HashMap<String, Integer>();
      var outDegree = new HashMap<String, Integer>();
      buildDegreeMaps(meta, tables, database, inDegree, outDegree);
      return rankCandidates(tables, inDegree, outDegree);
    } catch (SQLException e) {
      throw new ConnectorException("Root-table detection failed: " + e.getMessage(), e);
    }
  }

  private static List<TableRef> collectTables(DatabaseMetaData meta, DatabaseRef database)
      throws SQLException {
    var tables = new ArrayList<TableRef>();
    try (var rs = meta.getTables(database.name(), null, "%", new String[] {"TABLE"})) {
      while (rs.next()) {
        String schema = rs.getString("TABLE_SCHEM");
        String tbl = rs.getString("TABLE_NAME");
        tables.add(new TableRef(database, schema != null ? schema : "", tbl));
      }
    }
    return tables;
  }

  private static void buildDegreeMaps(
      DatabaseMetaData meta,
      List<TableRef> tables,
      DatabaseRef database,
      Map<String, Integer> inDegree,
      Map<String, Integer> outDegree)
      throws SQLException {
    for (TableRef t : tables) {
      inDegree.put(t.tableName().toLowerCase(), 0);
      outDegree.put(t.tableName().toLowerCase(), 0);
    }
    for (TableRef t : tables) {
      String schema = t.schemaName().isBlank() ? null : t.schemaName();
      try (ResultSet fks = meta.getImportedKeys(database.name(), schema, t.tableName())) {
        while (fks.next()) {
          String pkTable = fks.getString("PKTABLE_NAME").toLowerCase();
          outDegree.merge(t.tableName().toLowerCase(), 1, Integer::sum);
          inDegree.merge(pkTable, 1, Integer::sum);
        }
      } catch (SQLException ignored) {
        // table may be inaccessible
      }
    }
  }

  private static List<RootTableCandidate> rankCandidates(
      List<TableRef> tables, Map<String, Integer> inDegree, Map<String, Integer> outDegree) {
    var candidates = new ArrayList<RootTableCandidate>(tables.size());
    for (TableRef t : tables) {
      String key = t.tableName().toLowerCase();
      int in = inDegree.getOrDefault(key, 0);
      int out = outDegree.getOrDefault(key, 0);
      int bonus = nameBonus(t.tableName());
      int score = in * 3 - out + bonus;
      candidates.add(new RootTableCandidate(t.tableName(), score, in, out, buildReason(in, out)));
    }
    candidates.sort(Comparator.comparingInt(RootTableCandidate::score).reversed());
    int limit = Math.min(5, candidates.size());
    return List.copyOf(candidates.subList(0, limit));
  }

  private static int nameBonus(String tableName) {
    String lower = tableName.toLowerCase();
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
      if (lower.equals(root)
          || lower.startsWith(root + "_")
          || lower.endsWith("_" + root)
          || lower.equals(root + "s")) {
        return 2;
      }
    }
    return 0;
  }

  private static String buildReason(int inDegree, int outDegree) {
    var parts = new ArrayList<String>(2);
    if (inDegree > 0) {
      parts.add(inDegree + " tablo bu tabloyu referans alıyor");
    }
    if (outDegree > 0) {
      parts.add(outDegree + " FK bağlantısı var");
    }
    if (parts.isEmpty()) {
      return "FK ilişkisi bulunamadı";
    }
    return String.join(" · ", parts);
  }

  protected String quoteName(String name) {
    return "\"" + name.replace("\"", "\"\"") + "\"";
  }

  private Set<String> getPrimaryKeys(DatabaseMetaData meta, TableRef table) throws SQLException {
    var pks = new HashSet<String>();
    String schemaPattern = table.schemaName().isBlank() ? null : table.schemaName();
    try (var rs = meta.getPrimaryKeys(table.database().name(), schemaPattern, table.tableName())) {
      while (rs.next()) {
        pks.add(rs.getString("COLUMN_NAME"));
      }
    }
    return pks;
  }

  private ColumnMeta buildColumnMeta(ResultSet rs, Set<String> pkCols) throws SQLException {
    String name = rs.getString("COLUMN_NAME");
    String type = rs.getString("TYPE_NAME");
    boolean nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
    int ordinal = rs.getInt("ORDINAL_POSITION");
    String defaultVal = rs.getString("COLUMN_DEF");
    return new ColumnMeta(name, type, nullable, pkCols.contains(name), false, ordinal, defaultVal);
  }
}
