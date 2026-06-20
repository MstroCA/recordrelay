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
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.SchemaInspector;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
    try (var ds = DataSourceFactory.create(profile);
        var conn = ds.getConnection();
        var stmt = conn.prepareStatement(LIST_TABLES_SQL)) {
      stmt.setString(1, database.name());
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
