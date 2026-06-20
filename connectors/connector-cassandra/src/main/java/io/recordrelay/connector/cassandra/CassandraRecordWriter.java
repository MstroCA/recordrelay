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
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Writes records to a Cassandra table using UNLOGGED batch inserts. */
public final class CassandraRecordWriter implements RecordWriter {

  private static final Logger LOG = LoggerFactory.getLogger(CassandraRecordWriter.class);
  private static final int DEFAULT_BATCH = 100;

  private CqlSession session;
  private PreparedStatement insertStmt;
  private List<String> columnOrder;
  private String qualifiedTable;
  private final List<BoundStatement> buffer = new ArrayList<>(DEFAULT_BATCH);

  @Override
  public void open(ConnectionProfile profile, TableRef table) throws ConnectorException {
    String keyspace = table.schemaName().isBlank() ? "" : table.schemaName() + ".";
    this.qualifiedTable = keyspace + table.tableName();
    try {
      session = CassandraConnector.openSession(profile);
      LOG.debug("Opened writer on '{}'", qualifiedTable);
    } catch (Exception e) {
      throw new ConnectorException("Failed to open writer for '" + qualifiedTable + "'", e);
    }
  }

  @Override
  public void write(DataRecord record) throws ConnectorException {
    if (insertStmt == null) {
      initInsert(record);
    }
    var values = columnOrder.stream().map(c -> record.get(c)).toArray();
    buffer.add(insertStmt.bind(values));
    if (buffer.size() >= DEFAULT_BATCH) {
      flushBuffer();
    }
  }

  @Override
  public void flush() throws ConnectorException {
    if (!buffer.isEmpty()) {
      flushBuffer();
    }
  }

  @Override
  public void close() throws ConnectorException {
    if (!buffer.isEmpty()) {
      flushBuffer();
    }
    if (session != null) {
      session.close();
    }
  }

  private void initInsert(DataRecord sample) {
    columnOrder = new ArrayList<>(sample.fieldNames());
    var cols = String.join(", ", columnOrder);
    var placeholders = columnOrder.stream().map(c -> "?").collect(Collectors.joining(", "));
    var cql = "INSERT INTO " + qualifiedTable + " (" + cols + ") VALUES (" + placeholders + ")";
    insertStmt = session.prepare(cql);
  }

  private void flushBuffer() throws ConnectorException {
    try {
      var batch = BatchStatement.newInstance(BatchType.UNLOGGED).addAll(buffer);
      session.execute(batch);
      LOG.debug("Flushed {} rows to '{}'", buffer.size(), qualifiedTable);
      buffer.clear();
    } catch (Exception e) {
      throw new ConnectorException("Batch insert failed for '" + qualifiedTable + "'", e);
    }
  }
}
