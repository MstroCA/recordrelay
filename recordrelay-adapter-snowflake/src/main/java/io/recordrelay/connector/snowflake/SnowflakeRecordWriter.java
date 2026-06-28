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
package io.recordrelay.connector.snowflake;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordWriter;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Buffers records and writes them to a Snowflake table using JDBC batch inserts.
 *
 * <p>Snowflake batch inserts are multi-row {@code VALUES} lists — each {@code executeBatch()} call
 * flushes one batch via the Snowflake optimized JDBC path. On unique constraint violations
 * (SQLState {@code 23001}) the batch is retried row-by-row with savepoints.
 */
public final class SnowflakeRecordWriter implements RecordWriter {

  private static final Logger LOG = LoggerFactory.getLogger(SnowflakeRecordWriter.class);
  private static final int DEFAULT_BATCH_SIZE = 1_000;

  // Snowflake integrity constraint violation state
  private static final String UNIQUE_VIOLATION_STATE = "23001";

  private Connection conn;
  private PreparedStatement insertStmt;
  private List<String> columnOrder;
  private String qualifiedTable;
  private String plainTableName;
  private final List<DataRecord> buffer = new ArrayList<>(DEFAULT_BATCH_SIZE);
  private final LinkedHashMap<String, String> conflictRemaps = new LinkedHashMap<>();
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
    this.plainTableName = table.tableName();
    try {
      conn = DriverManager.getConnection(buildJdbcUrl(profile), buildProperties(profile));
      conn.setAutoCommit(false);
      LOG.debug("Opened Snowflake writer connection to '{}'", qualifiedTable);
    } catch (SQLException e) {
      throw new ConnectorException(
          "Failed to open Snowflake writer for '" + qualifiedTable + "'", e);
    }
  }

  @Override
  public void write(DataRecord record) throws ConnectorException {
    if (insertStmt == null) {
      initInsertStatement(record);
    }
    buffer.add(record);
    if (buffer.size() >= DEFAULT_BATCH_SIZE) {
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
      throw new ConnectorException("Error closing Snowflake writer", e);
    }
  }

  @Override
  public Map<String, String> drainConflictRemaps() {
    if (conflictRemaps.isEmpty()) {
      return Map.of();
    }
    var result = Map.copyOf(conflictRemaps);
    conflictRemaps.clear();
    return result;
  }

  // ── Internals ─────────────────────────────────────────────────────────────

  private void initInsertStatement(DataRecord sample) throws ConnectorException {
    columnOrder = new ArrayList<>(sample.fieldNames());
    var colList = columnOrder.stream().map(this::quoteIdent).collect(Collectors.joining(", "));
    var placeholders = columnOrder.stream().map(c -> "?").collect(Collectors.joining(", "));
    var sql = "INSERT INTO " + qualifiedTable + " (" + colList + ") VALUES (" + placeholders + ")";
    try {
      insertStmt = conn.prepareStatement(sql);
      LOG.debug("Prepared Snowflake INSERT: {}", sql);
    } catch (SQLException e) {
      throw new ConnectorException("Failed to prepare Snowflake INSERT: " + sql, e);
    }
  }

  private void flushBuffer() throws ConnectorException {
    if (skipExisting) {
      flushRowByRow();
      return;
    }
    try {
      for (DataRecord record : buffer) {
        bindRecord(insertStmt, record);
        insertStmt.addBatch();
      }
      insertStmt.executeBatch();
      LOG.debug("Flushed {} rows to '{}'", buffer.size(), qualifiedTable);
      buffer.clear();
    } catch (SQLException e) {
      if (isUniqueViolation(e)) {
        rollbackQuietly();
        flushRowByRow();
      } else {
        throw new ConnectorException("Batch insert failed for '" + qualifiedTable + "'", e);
      }
    }
  }

  private void flushRowByRow() throws ConnectorException {
    var sql = buildInsertSql();
    try (var stmt = conn.prepareStatement(sql)) {
      for (DataRecord record : buffer) {
        insertWithSavepoint(stmt, record);
      }
      buffer.clear();
    } catch (ConnectorException ce) {
      throw ce;
    } catch (SQLException e) {
      throw new ConnectorException("Row-by-row insert failed for '" + qualifiedTable + "'", e);
    }
  }

  private void insertWithSavepoint(PreparedStatement stmt, DataRecord record)
      throws ConnectorException {
    Savepoint sp;
    try {
      sp = conn.setSavepoint();
    } catch (SQLException e) {
      throw new ConnectorException("Could not set savepoint for '" + qualifiedTable + "'", e);
    }
    try {
      bindRecord(stmt, record);
      stmt.executeUpdate();
      conn.releaseSavepoint(sp);
    } catch (SQLException e) {
      rollbackToSavepoint(sp);
      if (isUniqueViolation(e)) {
        // Record a remap using the PK of the attempted row; the existing row wins.
        var attemptedPk = extractPkValue(record);
        if (attemptedPk != null) {
          LOG.debug(
              "Unique conflict in '{}' for pk '{}' — recording identity remap",
              qualifiedTable,
              attemptedPk);
          conflictRemaps.put(attemptedPk, attemptedPk);
        }
      } else {
        throw new ConnectorException("Row insert failed for '" + qualifiedTable + "'", e);
      }
    }
  }

  private String extractPkValue(DataRecord record) {
    if (columnOrder == null) {
      return null;
    }
    if (columnOrder.contains("id")) {
      var val = record.get("id");
      return val == null ? null : val.toString();
    }
    var singular =
        plainTableName.endsWith("s")
            ? plainTableName.substring(0, plainTableName.length() - 1) + "_id"
            : plainTableName + "_id";
    if (columnOrder.contains(singular)) {
      var val = record.get(singular);
      return val == null ? null : val.toString();
    }
    if (!columnOrder.isEmpty()) {
      var val = record.get(columnOrder.get(0));
      return val == null ? null : val.toString();
    }
    return null;
  }

  private static boolean isUniqueViolation(SQLException e) {
    SQLException cursor = e;
    while (cursor != null) {
      String state = cursor.getSQLState();
      if (UNIQUE_VIOLATION_STATE.equals(state) || "23000".equals(state)) {
        return true;
      }
      cursor = cursor.getNextException();
    }
    if (e instanceof BatchUpdateException bue && bue.getNextException() != null) {
      return isUniqueViolation(bue.getNextException());
    }
    return false;
  }

  private void rollbackQuietly() {
    try {
      conn.rollback();
    } catch (SQLException ignored) {
      LOG.debug("Rollback failed silently for '{}'", qualifiedTable);
    }
  }

  private void rollbackToSavepoint(Savepoint sp) {
    try {
      conn.rollback(sp);
    } catch (SQLException ignored) {
      LOG.debug("Savepoint rollback failed silently for '{}'", qualifiedTable);
    }
  }

  private String buildInsertSql() {
    var colList = columnOrder.stream().map(this::quoteIdent).collect(Collectors.joining(", "));
    var placeholders = columnOrder.stream().map(c -> "?").collect(Collectors.joining(", "));
    return "INSERT INTO " + qualifiedTable + " (" + colList + ") VALUES (" + placeholders + ")";
  }

  private void bindRecord(PreparedStatement stmt, DataRecord record) throws SQLException {
    for (int i = 0; i < columnOrder.size(); i++) {
      stmt.setObject(i + 1, record.get(columnOrder.get(i)));
    }
  }

  private String quoteIdent(String name) {
    return "\"" + name.replace("\"", "\"\"") + "\"";
  }

  private static String buildQuotedTableName(TableRef table) {
    String schema = table.schemaName().isBlank() ? "PUBLIC" : table.schemaName();
    return "\""
        + schema.replace("\"", "\"\"")
        + "\".\""
        + table.tableName().replace("\"", "\"\"")
        + "\"";
  }

  private static String buildJdbcUrl(ConnectionProfile profile) {
    return "jdbc:snowflake://" + profile.host() + "/";
  }

  private static Properties buildProperties(ConnectionProfile profile) {
    var props = new Properties();
    props.setProperty("user", profile.credentials().username());
    props.setProperty("password", profile.credentials().password());
    props.setProperty("db", profile.database());
    var extra = profile.properties();
    if (extra.containsKey("warehouse")) {
      props.setProperty("warehouse", extra.get("warehouse"));
    }
    if (extra.containsKey("schema")) {
      props.setProperty("schema", extra.get("schema"));
    }
    if (extra.containsKey("role")) {
      props.setProperty("role", extra.get("role"));
    }
    return props;
  }
}
