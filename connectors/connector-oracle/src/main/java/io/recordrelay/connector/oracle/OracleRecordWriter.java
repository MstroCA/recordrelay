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
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Writes records to an Oracle table using JDBC batch inserts. */
public final class OracleRecordWriter implements RecordWriter {

  private static final Logger LOG = LoggerFactory.getLogger(OracleRecordWriter.class);
  private static final int DEFAULT_BATCH = 1_000;

  private Connection conn;
  private PreparedStatement insertStmt;
  private List<String> columnOrder;
  private String qualifiedTable;
  private final List<DataRecord> buffer = new ArrayList<>(DEFAULT_BATCH);

  @Override
  public void open(ConnectionProfile profile, TableRef table) throws ConnectorException {
    this.qualifiedTable = table.qualifiedName();
    try {
      conn = OracleDataSourceFactory.create(profile).getConnection();
      conn.setAutoCommit(false);
      LOG.debug("Opened writer on '{}'", qualifiedTable);
    } catch (SQLException e) {
      throw new ConnectorException("Failed to open writer for '" + qualifiedTable + "'", e);
    }
  }

  @Override
  public void write(DataRecord record) throws ConnectorException {
    if (insertStmt == null) {
      initInsert(record);
    }
    buffer.add(record);
    if (buffer.size() >= DEFAULT_BATCH) {
      flushBuffer();
    }
  }

  @Override
  public void flush() throws ConnectorException {
    if (!buffer.isEmpty()) {
      flushBuffer();
    }
    try {
      conn.commit();
    } catch (SQLException e) {
      throw new ConnectorException("Commit failed for '" + qualifiedTable + "'", e);
    }
  }

  @Override
  public void close() throws ConnectorException {
    try {
      if (!buffer.isEmpty()) {
        flushBuffer();
        conn.commit();
      }
      if (insertStmt != null) {
        insertStmt.close();
      }
      if (conn != null) {
        conn.close();
      }
    } catch (SQLException e) {
      throw new ConnectorException("Error closing writer", e);
    }
  }

  private void initInsert(DataRecord sample) throws ConnectorException {
    columnOrder = new ArrayList<>(sample.fieldNames());
    var cols = String.join(", ", columnOrder);
    var placeholders = columnOrder.stream().map(c -> "?").collect(Collectors.joining(", "));
    var sql = "INSERT INTO " + qualifiedTable + " (" + cols + ") VALUES (" + placeholders + ")";
    try {
      insertStmt = conn.prepareStatement(sql);
    } catch (SQLException e) {
      throw new ConnectorException("Failed to prepare INSERT: " + sql, e);
    }
  }

  private void flushBuffer() throws ConnectorException {
    try {
      for (DataRecord rec : buffer) {
        for (int i = 0; i < columnOrder.size(); i++) {
          insertStmt.setObject(i + 1, rec.get(columnOrder.get(i)));
        }
        insertStmt.addBatch();
      }
      insertStmt.executeBatch();
      LOG.debug("Flushed {} rows to '{}'", buffer.size(), qualifiedTable);
      buffer.clear();
    } catch (SQLException e) {
      throw new ConnectorException("Batch insert failed for '" + qualifiedTable + "'", e);
    }
  }
}
