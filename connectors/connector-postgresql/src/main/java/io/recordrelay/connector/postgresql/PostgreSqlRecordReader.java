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
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Streams rows from a PostgreSQL table using a server-side cursor (via {@code setFetchSize}).
 *
 * <p>A raw {@link DriverManager} connection is used instead of a pool because a long-lived
 * streaming query must not be returned to a pool mid-flight.
 */
public final class PostgreSqlRecordReader implements RecordReader {

  private static final Logger LOG = LoggerFactory.getLogger(PostgreSqlRecordReader.class);
  private static final int FETCH_SIZE = 1_000;

  private Connection conn;
  private Statement stmt;
  private ResultSet rs;
  private boolean hasNext;

  @Override
  public void open(ConnectionProfile profile, TableRef table, MappingDefinition mapping)
      throws ConnectorException {
    var url =
        "jdbc:postgresql://" + profile.host() + ":" + profile.port() + "/" + profile.database();
    try {
      conn =
          DriverManager.getConnection(
              url, profile.credentials().username(), profile.credentials().password());
      conn.setAutoCommit(false);
      stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
      stmt.setFetchSize(FETCH_SIZE);
      rs = stmt.executeQuery(buildSelectSql(table, mapping));
      hasNext = rs.next();
      LOG.debug("Opened cursor on '{}', hasRows={}", table.qualifiedName(), hasNext);
    } catch (SQLException e) {
      throw new ConnectorException("Failed to open reader on '" + table.qualifiedName() + "'", e);
    }
  }

  @Override
  public Optional<DataRecord> readNext() throws ConnectorException {
    if (!hasNext) {
      return Optional.empty();
    }
    try {
      var record = rowToRecord();
      hasNext = rs.next();
      return Optional.of(record);
    } catch (SQLException e) {
      throw new ConnectorException("Error reading next row", e);
    }
  }

  @Override
  public boolean hasMore() {
    return hasNext;
  }

  @Override
  public void close() throws ConnectorException {
    try {
      if (rs != null) {
        rs.close();
      }
      if (stmt != null) {
        stmt.close();
      }
      if (conn != null) {
        conn.close();
      }
    } catch (SQLException e) {
      throw new ConnectorException("Error closing reader", e);
    }
  }

  private DataRecord rowToRecord() throws SQLException {
    var meta = rs.getMetaData();
    var fields = new LinkedHashMap<String, Object>(meta.getColumnCount());
    for (int i = 1; i <= meta.getColumnCount(); i++) {
      fields.put(meta.getColumnLabel(i), rs.getObject(i));
    }
    return new DataRecord(fields);
  }

  private String buildSelectSql(TableRef table, MappingDefinition mapping) {
    var tableName = table.qualifiedName();
    if (mapping.columnMappings().isEmpty()) {
      return "SELECT * FROM " + tableName;
    }
    var cols =
        mapping.columnMappings().stream()
            .map(cm -> cm.sourceColumn())
            .collect(Collectors.joining(", "));
    return "SELECT " + cols + " FROM " + tableName;
  }
}
