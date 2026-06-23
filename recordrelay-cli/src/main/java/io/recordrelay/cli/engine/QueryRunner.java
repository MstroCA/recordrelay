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
package io.recordrelay.cli.engine;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.QueryResult;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Executes read-only (SELECT-only) SQL queries against any JDBC-supported database.
 *
 * <p>Only {@code SELECT} statements are accepted; attempts to run DML or DDL are rejected before
 * any connection is made. Uses {@link DriverManager} directly — no connection pooling needed for
 * single one-shot queries.
 */
public final class QueryRunner {

  private static final Pattern SELECT_PATTERN =
      Pattern.compile("^\\s*SELECT\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  /**
   * Runs {@code sql} against the database described by {@code profile}.
   *
   * @param profile connection details
   * @param sql a SELECT-only query
   * @return the full result set
   * @throws IllegalArgumentException if {@code sql} is not a SELECT statement
   * @throws SQLException if the query fails
   */
  public QueryResult run(ConnectionProfile profile, String sql) throws SQLException {
    if (!SELECT_PATTERN.matcher(sql).find()) {
      throw new IllegalArgumentException("Only SELECT queries are permitted in the Query Analyzer");
    }

    String url = buildJdbcUrl(profile);
    String user = profile.credentials().username();
    String pass = profile.credentials().password();

    try (var conn = DriverManager.getConnection(url, user, pass);
        var stmt = conn.prepareStatement(sql)) {
      stmt.setQueryTimeout(30);
      try (var rs = stmt.executeQuery()) {
        var meta = rs.getMetaData();
        int colCount = meta.getColumnCount();

        var columns = new ArrayList<String>(colCount);
        for (int i = 1; i <= colCount; i++) {
          columns.add(meta.getColumnLabel(i));
        }

        var rows = new ArrayList<List<String>>();
        while (rs.next()) {
          var row = new ArrayList<String>(colCount);
          for (int i = 1; i <= colCount; i++) {
            Object val = rs.getObject(i);
            row.add(val == null ? "NULL" : val.toString());
          }
          rows.add(row);
        }
        return new QueryResult(columns, rows);
      }
    }
  }

  private static String buildJdbcUrl(ConnectionProfile profile) {
    return switch (profile.type()) {
      case POSTGRESQL -> profile.jdbcUrl("postgresql");
      case MYSQL -> profile.jdbcUrl("mysql");
      case MARIADB -> profile.jdbcUrl("mariadb");
      case SQLSERVER -> profile.jdbcUrl("sqlserver");
      case ORACLE -> profile.jdbcUrl("oracle:thin");
      case SQLITE -> profile.jdbcUrl("sqlite");
      default ->
          throw new IllegalArgumentException(
              "QueryRunner: JDBC query not supported for " + profile.type());
    };
  }
}
