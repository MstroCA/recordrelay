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

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Buffers records and writes them to a PostgreSQL table using JDBC batch inserts.
 *
 * <p>The INSERT statement is lazily built from the first record's field names to handle both
 * explicit column mappings and passthrough scenarios.
 */
public final class PostgreSqlRecordWriter implements RecordWriter {

  private static final Logger LOG = LoggerFactory.getLogger(PostgreSqlRecordWriter.class);
  private static final int DEFAULT_BATCH_SIZE = 1_000;

  private Connection conn;
  private PreparedStatement insertStmt;
  private List<String> columnOrder;
  private String qualifiedTable;
  private final List<DataRecord> buffer = new ArrayList<>(DEFAULT_BATCH_SIZE);
  private int batchSize = DEFAULT_BATCH_SIZE;
  private boolean skipExisting;

  @Override
  public void open(ConnectionProfile profile, TableRef table, boolean skipExisting)
      throws ConnectorException {
    this.skipExisting = skipExisting;
    open(profile, table);
  }

  @Override
  public void open(ConnectionProfile profile, TableRef table) throws ConnectorException {
    this.qualifiedTable = buildQuotedTableName(table);
    var url =
        "jdbc:postgresql://" + profile.host() + ":" + profile.port() + "/" + profile.database();
    try {
      conn =
          DriverManager.getConnection(
              url, profile.credentials().username(), profile.credentials().password());
      conn.setAutoCommit(false);
      LOG.debug("Opened writer connection to '{}'", qualifiedTable);
    } catch (SQLException e) {
      throw new ConnectorException("Failed to open writer for '" + qualifiedTable + "'", e);
    }
  }

  @Override
  public void write(DataRecord record) throws ConnectorException {
    if (insertStmt == null) {
      initInsertStatement(record);
    }
    buffer.add(record);
    if (buffer.size() >= batchSize) {
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

  private void initInsertStatement(DataRecord sample) throws ConnectorException {
    columnOrder = new ArrayList<>(sample.fieldNames());
    var colList = columnOrder.stream().map(this::quoteIdent).collect(Collectors.joining(", "));
    var placeholders = columnOrder.stream().map(c -> "?").collect(Collectors.joining(", "));
    var sql =
        "INSERT INTO "
            + qualifiedTable
            + " ("
            + colList
            + ") OVERRIDING SYSTEM VALUE VALUES ("
            + placeholders
            + ")"
            + (skipExisting ? " ON CONFLICT DO NOTHING" : "");
    try {
      insertStmt = conn.prepareStatement(sql);
      LOG.debug("Prepared: {}", sql);
    } catch (SQLException e) {
      throw new ConnectorException("Failed to prepare INSERT statement: " + sql, e);
    }
  }

  private String quoteIdent(String name) {
    return "\"" + name.replace("\"", "\"\"") + "\"";
  }

  private static String buildQuotedTableName(TableRef table) {
    var q = "\"" + table.tableName().replace("\"", "\"\"") + "\"";
    return table.schemaName().isEmpty()
        ? q
        : "\"" + table.schemaName().replace("\"", "\"\"") + "\"." + q;
  }

  private void flushBuffer() throws ConnectorException {
    try {
      for (DataRecord record : buffer) {
        for (int i = 0; i < columnOrder.size(); i++) {
          insertStmt.setObject(i + 1, record.get(columnOrder.get(i)));
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
