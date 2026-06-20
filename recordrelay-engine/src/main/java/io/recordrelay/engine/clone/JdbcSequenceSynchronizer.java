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
import io.recordrelay.core.clone.domain.IdentityMapping;
import io.recordrelay.core.clone.port.out.SequenceSyncPort;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseType;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Advances database sequences / auto-increment counters after a clone operation.
 *
 * <p>After RecordRelay writes records with identity-mapped IDs, the target DB's sequence does not
 * know the highest used ID. This synchronizer fixes that so subsequent application inserts do not
 * collide.
 *
 * <p>Supported databases:
 *
 * <ul>
 *   <li><b>PostgreSQL</b> — {@code SELECT setval(pg_get_serial_sequence(t, 'id'), maxId, true)}
 *   <li><b>MySQL / MariaDB</b> — {@code ALTER TABLE t AUTO_INCREMENT = maxId + 1}
 *   <li><b>SQL Server</b> — {@code DBCC CHECKIDENT ('t', RESEED, maxId)}
 *   <li>Other databases — best-effort (logs warning if not supported)
 * </ul>
 *
 * <p>All errors are swallowed after logging: sequence sync failure is non-fatal. The cloned records
 * are already in the target; only the counter is stale.
 */
public final class JdbcSequenceSynchronizer implements SequenceSyncPort, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcSequenceSynchronizer.class);

  private final ConcurrentHashMap<String, HikariDataSource> pools = new ConcurrentHashMap<>();

  @Override
  public void synchronize(ConnectionProfile target, IdentityMapping mapping) {
    if (mapping.isEmpty()) {
      return;
    }

    var dbType = target.type();
    for (var tableEntry : mapping.snapshot().entrySet()) {
      var table = tableEntry.getKey();
      var maxTargetId =
          tableEntry.getValue().values().stream().mapToLong(Long::parseLong).max().orElse(0L);
      if (maxTargetId == 0L) {
        continue;
      }

      try {
        syncTable(target, dbType, table, maxTargetId);
        LOG.debug("Sequence synced: table='{}', maxId={}", table, maxTargetId);
      } catch (Exception e) {
        LOG.warn(
            "Sequence sync warning for table '{}': {} (non-fatal, clone succeeded)",
            table,
            e.getMessage());
      }
    }
  }

  @Override
  public void close() {
    pools.values().forEach(HikariDataSource::close);
    pools.clear();
  }

  // ── database-specific sync ──────────────────────────────────────────────────

  private void syncTable(ConnectionProfile target, DatabaseType dbType, String table, long maxId)
      throws SQLException {
    switch (dbType) {
      case POSTGRESQL -> syncPostgres(target, table, maxId);
      case MYSQL -> syncMysql(target, table, maxId);
      case SQLSERVER -> syncSqlServer(target, table, maxId);
      default ->
          LOG.warn(
              "Sequence sync not supported for database type '{}' — skipping table '{}'",
              dbType,
              table);
    }
  }

  private void syncPostgres(ConnectionProfile target, String table, long maxId)
      throws SQLException {
    var sql =
        "DO $$ DECLARE seq TEXT; BEGIN "
            + "seq := pg_get_serial_sequence('"
            + table
            + "', 'id'); "
            + "IF seq IS NOT NULL THEN PERFORM setval(seq, "
            + maxId
            + ", true); END IF; "
            + "END $$";
    try (var conn = pool(target).getConnection();
        var stmt = conn.createStatement()) {
      stmt.execute(sql);
    }
  }

  private void syncMysql(ConnectionProfile target, String table, long maxId) throws SQLException {
    var sql = "ALTER TABLE `" + table + "` AUTO_INCREMENT = " + (maxId + 1);
    try (var conn = pool(target).getConnection();
        var stmt = conn.createStatement()) {
      stmt.execute(sql);
    }
  }

  private void syncSqlServer(ConnectionProfile target, String table, long maxId)
      throws SQLException {
    var sql = "DBCC CHECKIDENT ('" + table + "', RESEED, " + maxId + ")";
    try (var conn = pool(target).getConnection();
        var stmt = conn.createStatement()) {
      stmt.execute(sql);
    }
  }

  // ── JDBC pool ───────────────────────────────────────────────────────────────

  private HikariDataSource pool(ConnectionProfile profile) {
    return pools.computeIfAbsent(
        profile.id(),
        ignored -> {
          var cfg = new HikariConfig();
          cfg.setJdbcUrl(jdbcUrl(profile));
          cfg.setUsername(profile.credentials().username());
          cfg.setPassword(profile.credentials().password());
          cfg.setMaximumPoolSize(2);
          cfg.setConnectionTimeout(10_000);
          cfg.setPoolName("seq-sync-" + profile.id());
          profile.properties().forEach(cfg::addDataSourceProperty);
          return new HikariDataSource(cfg);
        });
  }

  private String jdbcUrl(ConnectionProfile profile) {
    return switch (profile.type()) {
      case POSTGRESQL -> profile.jdbcUrl("postgresql");
      case MYSQL -> profile.jdbcUrl("mysql");
      case SQLSERVER -> profile.jdbcUrl("sqlserver");
      default -> profile.jdbcUrl(profile.type().name().toLowerCase());
    };
  }
}
