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
package io.recordrelay.connector.sqlite;

import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.SchemaInspector;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** {@link SchemaInspector} for SQLite using {@code sqlite_master} and {@code PRAGMA table_info}. */
public final class SqliteSchemaInspector implements SchemaInspector {

  @Override
  public List<TableRef> listTables(ConnectionProfile profile, DatabaseRef database)
      throws ConnectorException {
    var url = "jdbc:sqlite:" + profile.database();
    try (var conn = DriverManager.getConnection(url);
        var stmt = conn.createStatement();
        var rs =
            stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")) {
      var result = new ArrayList<TableRef>();
      while (rs.next()) {
        result.add(new TableRef(database, "", rs.getString("name")));
      }
      return List.copyOf(result);
    } catch (SQLException e) {
      throw new ConnectorException("Failed to list tables: " + e.getMessage(), e);
    }
  }

  @Override
  public List<ColumnMeta> inspectColumns(ConnectionProfile profile, TableRef table)
      throws ConnectorException {
    var url = "jdbc:sqlite:" + profile.database();
    try (var conn = DriverManager.getConnection(url);
        var stmt = conn.createStatement();
        var rs = stmt.executeQuery("PRAGMA table_info(" + table.tableName() + ")")) {
      var result = new ArrayList<ColumnMeta>();
      while (rs.next()) {
        int ordinal = rs.getInt("cid") + 1;
        String name = rs.getString("name");
        String type = rs.getString("type");
        boolean nullable = rs.getInt("notnull") == 0;
        boolean pk = rs.getInt("pk") > 0;
        String defaultVal = rs.getString("dflt_value");
        result.add(new ColumnMeta(name, type, nullable, pk, false, ordinal, defaultVal));
      }
      return List.copyOf(result);
    } catch (SQLException e) {
      throw new ConnectorException("Failed to inspect columns for '" + table.tableName() + "'", e);
    }
  }
}
