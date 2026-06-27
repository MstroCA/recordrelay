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
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Buffers records and writes them to a PostgreSQL table using JDBC batch inserts.
 *
 * <p>On a unique-constraint violation (SQLState 23505) the batch is rolled back and retried
 * row-by-row using savepoints. Each conflicting row is resolved by querying the existing target row
 * and recording a PK remap that the engine will apply to FK columns in subsequent tables.
 */
public final class PostgreSqlRecordWriter implements RecordWriter {

  private static final Logger LOG = LoggerFactory.getLogger(PostgreSqlRecordWriter.class);
  private static final int DEFAULT_BATCH_SIZE = 1_000;

  // Parses PostgreSQL detail: "Key (email)=(john@example.com) already exists."
  private static final Pattern DETAIL_PATTERN =
      Pattern.compile("Key \\(([^)]+)\\)=\\(([^)]*)\\) already exists");

  private Connection conn;
  private PreparedStatement insertStmt;
  private List<String> columnOrder;
  private String qualifiedTable;
  private String plainTableName;
  private final List<DataRecord> buffer = new ArrayList<>(DEFAULT_BATCH_SIZE);
  private final LinkedHashMap<String, String> conflictRemaps = new LinkedHashMap<>();
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
    this.plainTableName = table.tableName();
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

  @Override
  public Map<String, String> drainConflictRemaps() {
    if (conflictRemaps.isEmpty()) {
      return Map.of();
    }
    var result = Map.copyOf(conflictRemaps);
    conflictRemaps.clear();
    return result;
  }

  private void initInsertStatement(DataRecord sample) throws ConnectorException {
    columnOrder = new ArrayList<>(sample.fieldNames());
    var colList = columnOrder.stream().map(this::quoteIdent).collect(Collectors.joining(", "));
    var placeholders = columnOrder.stream().map(c -> "?").collect(Collectors.joining(", "));
    // OVERRIDING SYSTEM VALUE allows inserting explicit values into identity/serial columns.
    var sql =
        "INSERT INTO "
            + qualifiedTable
            + " ("
            + colList
            + ") OVERRIDING SYSTEM VALUE VALUES ("
            + placeholders
            + ")";
    try {
      insertStmt = conn.prepareStatement(sql);
      LOG.debug("Prepared: {}", sql);
    } catch (SQLException e) {
      throw new ConnectorException("Failed to prepare INSERT statement: " + sql, e);
    }
  }

  private void flushBuffer() throws ConnectorException {
    if (skipExisting) {
      // SKIP_EXISTING: use row-by-row so we can detect and remap non-PK unique conflicts.
      flushRowByRow();
      return;
    }
    // Fast path: batch insert.
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
        handleUniqueViolation(record, e);
      } else {
        throw new ConnectorException("Row insert failed for '" + qualifiedTable + "'", e);
      }
    }
  }

  private void handleUniqueViolation(DataRecord record, SQLException e) {
    var detail = parseConflictDetail(e);
    if (detail.isEmpty()) {
      LOG.warn("Unique violation on '{}' but could not parse conflict detail", qualifiedTable);
      return;
    }
    var conflictColumn = detail.get()[0];
    var conflictValue = detail.get()[1];
    var attemptedPk = extractPkValue(record);
    if (attemptedPk == null) {
      return;
    }
    try {
      findExistingPk(conflictColumn, conflictValue)
          .ifPresentOrElse(
              existingPk -> {
                if (!attemptedPk.equals(existingPk)) {
                  conflictRemaps.put(attemptedPk, existingPk);
                }
                LOG.debug(
                    "Conflict remap {}→{} in '{}' ({}={})",
                    attemptedPk,
                    existingPk,
                    qualifiedTable,
                    conflictColumn,
                    conflictValue);
              },
              () ->
                  LOG.warn(
                      "Could not locate existing row in '{}' for {}={}",
                      qualifiedTable,
                      conflictColumn,
                      conflictValue));
    } catch (SQLException ex) {
      LOG.warn(
          "Error querying existing PK in '{}' for conflict on {}: {}",
          qualifiedTable,
          conflictColumn,
          ex.getMessage());
    }
  }

  private Optional<String> findExistingPk(String conflictColumn, String conflictValue)
      throws SQLException {
    var pkCol = detectPkColumn();
    var sql =
        "SELECT "
            + quoteIdent(pkCol)
            + " FROM "
            + qualifiedTable
            + " WHERE "
            + quoteIdent(conflictColumn)
            + " = ?";
    try (var stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, conflictValue, Types.OTHER);
      try (var rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(rs.getString(1));
        }
      }
    }
    return Optional.empty();
  }

  private String extractPkValue(DataRecord record) {
    var pkCol = detectPkColumn();
    var val = record.get(pkCol);
    return val == null ? null : val.toString();
  }

  private String detectPkColumn() {
    if (columnOrder == null) {
      return "id";
    }
    if (columnOrder.contains("id")) {
      return "id";
    }
    var singular =
        plainTableName.endsWith("s")
            ? plainTableName.substring(0, plainTableName.length() - 1) + "_id"
            : plainTableName + "_id";
    if (columnOrder.contains(singular)) {
      return singular;
    }
    return columnOrder.isEmpty() ? "id" : columnOrder.get(0);
  }

  private static Optional<String[]> parseConflictDetail(SQLException e) {
    // Walk the exception chain including BatchUpdateException.getNextException()
    Throwable cursor = e;
    while (cursor != null) {
      var matched = matchDetail(cursor.getMessage());
      if (matched.isPresent()) {
        return matched;
      }
      if (cursor instanceof PSQLException psql) {
        var msg = psql.getServerErrorMessage();
        if (msg != null) {
          var matched2 = matchDetail(msg.getDetail());
          if (matched2.isPresent()) {
            return matched2;
          }
        }
      }
      if (cursor instanceof BatchUpdateException bue && bue.getNextException() != null) {
        var nested = parseConflictDetail(bue.getNextException());
        if (nested.isPresent()) {
          return nested;
        }
      }
      cursor = cursor.getCause();
    }
    return Optional.empty();
  }

  private static Optional<String[]> matchDetail(String text) {
    if (text == null) {
      return Optional.empty();
    }
    var m = DETAIL_PATTERN.matcher(text);
    if (m.find()) {
      return Optional.of(new String[] {m.group(1), m.group(2)});
    }
    return Optional.empty();
  }

  private static boolean isUniqueViolation(SQLException e) {
    SQLException cursor = e;
    while (cursor != null) {
      if ("23505".equals(cursor.getSQLState())) {
        return true;
      }
      cursor = cursor.getNextException();
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
    return "INSERT INTO "
        + qualifiedTable
        + " ("
        + colList
        + ") OVERRIDING SYSTEM VALUE VALUES ("
        + placeholders
        + ")";
  }

  private void bindRecord(PreparedStatement stmt, DataRecord record) throws SQLException {
    for (int i = 0; i < columnOrder.size(); i++) {
      Object val = record.get(columnOrder.get(i));
      if (val instanceof String) {
        stmt.setObject(i + 1, val, Types.OTHER);
      } else {
        stmt.setObject(i + 1, val);
      }
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
}
