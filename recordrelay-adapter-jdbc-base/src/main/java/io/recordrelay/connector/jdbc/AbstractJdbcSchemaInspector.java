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
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.SchemaInspector;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
