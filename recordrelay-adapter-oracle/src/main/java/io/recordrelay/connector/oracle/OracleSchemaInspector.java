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
package io.recordrelay.connector.oracle;

import io.recordrelay.connector.oracle.internal.OracleDataSourceFactory;
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

/** {@link SchemaInspector} adapter for Oracle using {@link DatabaseMetaData}. */
public final class OracleSchemaInspector implements SchemaInspector {

  @Override
  public List<TableRef> listTables(ConnectionProfile profile, DatabaseRef database)
      throws ConnectorException {
    try (var ds = OracleDataSourceFactory.create(profile);
        var conn = ds.getConnection()) {
      var meta = conn.getMetaData();
      // In Oracle, the schema is typically the username (upper-cased)
      String schema = profile.credentials().username().toUpperCase();
      var result = new ArrayList<TableRef>();
      try (var rs = meta.getTables(null, schema, "%", new String[] {"TABLE"})) {
        while (rs.next()) {
          result.add(
              new TableRef(database, rs.getString("TABLE_SCHEM"), rs.getString("TABLE_NAME")));
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
    try (var ds = OracleDataSourceFactory.create(profile);
        var conn = ds.getConnection()) {
      var meta = conn.getMetaData();
      var pkCols = getPrimaryKeys(meta, table);
      var result = new ArrayList<ColumnMeta>();
      String schema = table.schemaName().isBlank() ? null : table.schemaName();
      try (ResultSet rs = meta.getColumns(null, schema, table.tableName(), "%")) {
        while (rs.next()) {
          String name = rs.getString("COLUMN_NAME");
          String type = rs.getString("TYPE_NAME");
          boolean nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
          int ordinal = rs.getInt("ORDINAL_POSITION");
          String defaultVal = rs.getString("COLUMN_DEF");
          result.add(
              new ColumnMeta(
                  name, type, nullable, pkCols.contains(name), false, ordinal, defaultVal));
        }
      }
      return List.copyOf(result);
    } catch (SQLException e) {
      throw new ConnectorException("Failed to inspect columns: " + e.getMessage(), e);
    }
  }

  private Set<String> getPrimaryKeys(DatabaseMetaData meta, TableRef table) throws SQLException {
    var pks = new HashSet<String>();
    String schema = table.schemaName().isBlank() ? null : table.schemaName();
    try (var rs = meta.getPrimaryKeys(null, schema, table.tableName())) {
      while (rs.next()) {
        pks.add(rs.getString("COLUMN_NAME"));
      }
    }
    return pks;
  }
}
