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
package io.recordrelay.connector.cassandra;

import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.SchemaInspector;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** {@link SchemaInspector} for Cassandra using {@code system_schema} tables. */
public final class CassandraSchemaInspector implements SchemaInspector {

  @Override
  public List<TableRef> listTables(ConnectionProfile profile, DatabaseRef database)
      throws ConnectorException {
    try (var session = CassandraConnector.openSession(profile)) {
      var result = new ArrayList<TableRef>();
      var rs =
          session.execute(
              "SELECT table_name FROM system_schema.tables WHERE keyspace_name = '"
                  + database.name()
                  + "'");
      for (var row : rs) {
        result.add(new TableRef(database, database.name(), row.getString("table_name")));
      }
      return List.copyOf(result);
    } catch (Exception e) {
      throw new ConnectorException("Failed to list tables: " + e.getMessage(), e);
    }
  }

  @Override
  public List<ColumnMeta> inspectColumns(ConnectionProfile profile, TableRef table)
      throws ConnectorException {
    try (var session = CassandraConnector.openSession(profile)) {
      var pkCols = getPartitionKeys(session, table);
      var result = new ArrayList<ColumnMeta>();
      var rs =
          session.execute(
              "SELECT column_name, type, position FROM system_schema.columns"
                  + " WHERE keyspace_name = '"
                  + table.schemaName()
                  + "' AND table_name = '"
                  + table.tableName()
                  + "'");
      var ordinal = new AtomicInteger(1);
      for (var row : rs) {
        String name = row.getString("column_name");
        String type = row.getString("type");
        boolean pk = pkCols.contains(name);
        result.add(new ColumnMeta(name, type, !pk, pk, false, ordinal.getAndIncrement(), null));
      }
      return List.copyOf(result);
    } catch (Exception e) {
      throw new ConnectorException("Failed to inspect columns: " + e.getMessage(), e);
    }
  }

  private Set<String> getPartitionKeys(
      com.datastax.oss.driver.api.core.CqlSession session, TableRef table) {
    var pks = new HashSet<String>();
    var rs =
        session.execute(
            "SELECT column_name FROM system_schema.columns"
                + " WHERE keyspace_name = '"
                + table.schemaName()
                + "' AND table_name = '"
                + table.tableName()
                + "' AND kind IN ('partition_key', 'clustering')");
    for (var row : rs) {
      pks.add(row.getString("column_name"));
    }
    return pks;
  }
}
