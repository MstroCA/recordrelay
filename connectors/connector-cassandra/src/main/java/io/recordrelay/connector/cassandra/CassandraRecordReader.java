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

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordReader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Streams rows from a Cassandra table using the DataStax driver auto-pagination. */
public final class CassandraRecordReader implements RecordReader {

  private static final Logger LOG = LoggerFactory.getLogger(CassandraRecordReader.class);

  private CqlSession session;
  private Iterator<Row> rowIterator;
  private com.datastax.oss.driver.api.core.cql.ColumnDefinitions columnDefs;

  @Override
  public void open(ConnectionProfile profile, TableRef table, MappingDefinition mapping)
      throws ConnectorException {
    try {
      session = CassandraConnector.openSession(profile);
      ResultSet rs = session.execute(buildSelect(table, mapping));
      columnDefs = rs.getColumnDefinitions();
      rowIterator = rs.iterator();
      LOG.debug("Opened cursor on '{}'", table.qualifiedName());
    } catch (Exception e) {
      throw new ConnectorException("Failed to open reader on '" + table.qualifiedName() + "'", e);
    }
  }

  @Override
  public Optional<DataRecord> readNext() throws ConnectorException {
    if (!rowIterator.hasNext()) {
      return Optional.empty();
    }
    var row = rowIterator.next();
    var fields = new LinkedHashMap<String, Object>(columnDefs.size());
    for (int i = 0; i < columnDefs.size(); i++) {
      fields.put(columnDefs.get(i).getName().asInternal(), row.getObject(i));
    }
    return Optional.of(new DataRecord(fields));
  }

  @Override
  public boolean hasMore() {
    return rowIterator.hasNext();
  }

  @Override
  public void close() throws ConnectorException {
    if (session != null) {
      session.close();
    }
  }

  private String buildSelect(TableRef table, MappingDefinition mapping) {
    String keyspace = table.schemaName().isBlank() ? "" : table.schemaName() + ".";
    if (mapping.columnMappings().isEmpty()) {
      return "SELECT * FROM " + keyspace + table.tableName();
    }
    var cols =
        mapping.columnMappings().stream()
            .map(cm -> cm.sourceColumn())
            .collect(Collectors.joining(", "));
    return "SELECT " + cols + " FROM " + keyspace + table.tableName();
  }
}
