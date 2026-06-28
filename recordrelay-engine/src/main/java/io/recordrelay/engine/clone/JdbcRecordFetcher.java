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
package io.recordrelay.engine.clone;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.clone.port.out.RecordFetcherPort;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC-based implementation of {@link RecordFetcherPort}.
 *
 * <p>Uses HikariCP connection pooling. A pool is created on first use per connection profile and
 * cached for the lifetime of this fetcher instance. Close this fetcher to release all pools.
 */
public final class JdbcRecordFetcher implements RecordFetcherPort, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcRecordFetcher.class);

  private final ConcurrentHashMap<String, HikariDataSource> pools = new ConcurrentHashMap<>();

  @Override
  public Optional<DataRecord> fetchById(
      ConnectionProfile profile, String tableName, String idColumn, String idValue)
      throws CloneException {
    var sql = "SELECT * FROM " + quoteName(tableName) + " WHERE " + quoteName(idColumn) + " = ?";
    try (var conn = pool(profile).getConnection();
        var stmt = conn.prepareStatement(sql)) {
      setParam(stmt, 1, idValue);
      try (var rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
        return Optional.empty();
      }
    } catch (SQLException e) {
      throw new CloneException(
          "fetchById failed for " + tableName + "." + idColumn + "=" + idValue, e);
    }
  }

  /**
   * Fetches the root record as it existed at {@code asOf} by querying {@code <table>_audit}.
   *
   * <p>The audit table is expected to have a {@code changed_at} TIMESTAMP column and an {@code
   * operation} column (INSERT/UPDATE/DELETE). If the audit table does not exist or contains no
   * matching row, falls back to {@link #fetchById}.
   */
  public Optional<DataRecord> fetchByIdAsOf(
      ConnectionProfile profile, String tableName, String idColumn, String idValue, Instant asOf)
      throws CloneException {
    var auditTable = tableName + "_audit";
    var auditSql =
        "SELECT * FROM "
            + quoteName(auditTable)
            + " WHERE "
            + quoteName(idColumn)
            + " = ?"
            + " AND changed_at <= ?"
            + " AND (operation = 'INSERT' OR operation = 'UPDATE')"
            + " ORDER BY changed_at DESC LIMIT 1";
    try (var conn = pool(profile).getConnection()) {
      try (var stmt = conn.prepareStatement(auditSql)) {
        setParam(stmt, 1, idValue);
        stmt.setTimestamp(2, java.sql.Timestamp.from(asOf));
        try (var rs = stmt.executeQuery()) {
          if (rs.next()) {
            return Optional.of(mapRow(rs));
          }
        }
      } catch (SQLException ignored) {
        // Audit table absent or schema mismatch — fall through to current record
      }
    } catch (SQLException e) {
      throw new CloneException("Connection failed for point-in-time fetch on " + tableName, e);
    }
    LOG.warn(
        "No audit trail found for {}.{}={} at {}; falling back to current record",
        tableName,
        idColumn,
        idValue,
        asOf);
    return fetchById(profile, tableName, idColumn, idValue);
  }

  @Override
  public List<DataRecord> fetchByForeignKey(
      ConnectionProfile profile, String tableName, String fkColumn, String fkValue)
      throws CloneException {
    var sql = "SELECT * FROM " + quoteName(tableName) + " WHERE " + quoteName(fkColumn) + " = ?";
    try (var conn = pool(profile).getConnection();
        var stmt = conn.prepareStatement(sql)) {
      setParam(stmt, 1, fkValue);
      try (var rs = stmt.executeQuery()) {
        var results = new ArrayList<DataRecord>();
        while (rs.next()) {
          results.add(mapRow(rs));
        }
        return List.copyOf(results);
      }
    } catch (SQLException e) {
      throw new CloneException(
          "fetchByForeignKey failed for " + tableName + "." + fkColumn + "=" + fkValue, e);
    }
  }

  private HikariDataSource pool(ConnectionProfile profile) {
    return pools.computeIfAbsent(
        profile.id(),
        ignored -> {
          var cfg = new HikariConfig();
          cfg.setJdbcUrl(jdbcUrl(profile));
          cfg.setUsername(profile.credentials().username());
          cfg.setPassword(profile.credentials().password());
          cfg.setMaximumPoolSize(4);
          cfg.setConnectionTimeout(10_000);
          cfg.setPoolName("clone-fetch-" + profile.id());
          profile.properties().forEach(cfg::addDataSourceProperty);
          LOG.debug("Creating JDBC fetch pool for profile {}", profile.id());
          return new HikariDataSource(cfg);
        });
  }

  private String jdbcUrl(ConnectionProfile profile) {
    return switch (profile.type()) {
      case POSTGRESQL -> profile.jdbcUrl("postgresql");
      case MYSQL -> profile.jdbcUrl("mysql");
      case SQLSERVER -> profile.jdbcUrl("sqlserver");
      case ORACLE ->
          "jdbc:oracle:thin:@" + profile.host() + ":" + profile.port() + "/" + profile.database();
      case SQLITE -> "jdbc:sqlite:" + profile.database();
      default ->
          throw new IllegalArgumentException(
              "JdbcRecordFetcher does not support " + profile.type());
    };
  }

  private DataRecord mapRow(ResultSet rs) throws SQLException {
    ResultSetMetaData meta = rs.getMetaData();
    var fields = new LinkedHashMap<String, Object>(meta.getColumnCount());
    for (int i = 1; i <= meta.getColumnCount(); i++) {
      fields.put(meta.getColumnLabel(i), rs.getObject(i));
    }
    return new DataRecord(fields);
  }

  private static void setParam(java.sql.PreparedStatement stmt, int idx, String value)
      throws SQLException {
    try {
      stmt.setLong(idx, Long.parseLong(value));
    } catch (NumberFormatException e) {
      stmt.setString(idx, value);
    }
  }

  private String quoteName(String name) {
    return "\"" + name.replace("\"", "\"\"") + "\"";
  }

  @Override
  public void close() {
    pools
        .values()
        .forEach(
            ds -> {
              try {
                ds.close();
              } catch (Exception e) {
                LOG.warn("Error closing HikariCP pool", e);
              }
            });
    pools.clear();
  }
}
