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
import io.recordrelay.core.clone.domain.ConflictResolution;
import io.recordrelay.core.clone.domain.IdentityMapping;
import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.clone.port.out.IdentityMapperPort;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.DatabaseType;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC-backed identity mapper that allocates new target primary keys using the {@code max(id) + n}
 * strategy.
 *
 * <p>For each table in the extraction set the mapper queries {@code SELECT COALESCE(MAX(id), 0)}
 * from the target and then assigns sequential IDs starting from {@code max + 1}. This guarantees no
 * collision with pre-existing rows while keeping the IDs compact and meaningful.
 *
 * <p>The mapper looks for a PK column in this priority order:
 *
 * <ol>
 *   <li>Column named {@code "id"}
 *   <li>Column named {@code "<tableName>_id"} (singular)
 *   <li>First column of the record (fallback)
 * </ol>
 *
 * <p>With {@link ConflictResolution#FAIL_SAFE} the mapper also checks that the target table is
 * empty before allocating — throwing {@link CloneException} if it is not.
 */
public final class DefaultIdentityMapper implements IdentityMapperPort, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultIdentityMapper.class);

  private final ConcurrentHashMap<String, HikariDataSource> pools = new ConcurrentHashMap<>();

  @Override
  public IdentityMapping allocate(
      ConnectionProfile target,
      String rootTable,
      Map<String, List<DataRecord>> records,
      ConflictResolution resolution)
      throws CloneException {
    return allocate(target, rootTable, records, resolution, null);
  }

  @Override
  public IdentityMapping allocate(
      ConnectionProfile target,
      String rootTable,
      Map<String, List<DataRecord>> records,
      ConflictResolution resolution,
      Long identityStart)
      throws CloneException {

    // SKIP_EXISTING inserts with original IDs; ON CONFLICT DO NOTHING handles clashes at DB level.
    if (resolution == ConflictResolution.SKIP_EXISTING) {
      return IdentityMapping.empty();
    }

    var builder = IdentityMapping.builder();
    for (var entry : records.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        allocateTable(builder, target, entry.getKey(), entry.getValue(), resolution, identityStart);
      }
    }

    var mapping = builder.build();
    LOG.info(
        "Identity allocation complete: {} total mappings across {} tables",
        mapping.totalMappings(),
        records.size());
    return mapping;
  }

  /** Allocates new IDs for a single table and registers them into {@code builder}. */
  private void allocateTable(
      IdentityMapping.Builder builder,
      ConnectionProfile target,
      String table,
      List<DataRecord> tableRecords,
      ConflictResolution resolution,
      Long identityStart)
      throws CloneException {
    var pkColumn = detectPkColumn(table, tableRecords.get(0));

    // SEQUENCE: pull fresh IDs from the table's own native sequence (nextval).
    if (resolution == ConflictResolution.SEQUENCE) {
      var seqIds = allocateFromSequence(target, table, pkColumn, tableRecords.size());
      if (seqIds != null) {
        registerAllocatedIds(builder, table, tableRecords, pkColumn, seqIds);
        LOG.debug("Allocated {} IDs for '{}' from native sequence", seqIds.size(), table);
        return;
      }
      LOG.warn("No native sequence found for {}.{} — falling back to max(id)+1", table, pkColumn);
    }

    var targetMax = queryMaxId(target, table, pkColumn, resolution);
    var sourceMax = maxSourceId(tableRecords, pkColumn);
    long counter = allocationBase(resolution, identityStart, targetMax, sourceMax);

    for (var record : tableRecords) {
      var sourceId = extractFieldAsString(record, pkColumn);
      if (sourceId == null) {
        LOG.warn(
            "Record in table '{}' has no value for PK column '{}'; skipping identity allocation",
            table,
            pkColumn);
        continue;
      }
      counter++;
      builder.register(table, sourceId, String.valueOf(counter));
    }
    LOG.debug(
        "Allocated {} new IDs for table '{}' (targetMax={})",
        tableRecords.size(),
        table,
        targetMax);
  }

  /**
   * Computes the counter's starting point (the value <em>before</em> the first {@code ++}) for the
   * non-sequence strategies.
   */
  private static long allocationBase(
      ConflictResolution resolution, Long identityStart, long targetMax, long sourceMax) {
    if (resolution == ConflictResolution.ISOLATE_NAMESPACE) {
      // Large gap so cloned IDs never overlap with any pre-existing range.
      return Math.max(targetMax, sourceMax) + 1_000_000L;
    }
    if (resolution == ConflictResolution.START_AT && identityStart != null) {
      // Honour the requested start, but never below existing rows (floor at targetMax).
      return Math.max(targetMax, identityStart - 1);
    }
    return Math.max(targetMax, sourceMax);
  }

  private static void registerAllocatedIds(
      IdentityMapping.Builder builder,
      String table,
      List<DataRecord> tableRecords,
      String pkColumn,
      List<Long> newIds) {
    int i = 0;
    for (var record : tableRecords) {
      var sourceId = record.get(pkColumn);
      if (sourceId == null) {
        LOG.warn("Record in table '{}' has no PK value for '{}'; skipping", table, pkColumn);
        continue;
      }
      builder.register(table, sourceId.toString(), String.valueOf(newIds.get(i)));
      i++;
    }
  }

  /**
   * Allocates {@code count} fresh IDs from the target table's native sequence. Returns {@code null}
   * when the database/column has no discoverable sequence, signalling a fallback to {@code
   * max(id)+1}. Currently implemented for PostgreSQL.
   */
  private List<Long> allocateFromSequence(
      ConnectionProfile target, String table, String pkColumn, int count) {
    if (target.type() != DatabaseType.POSTGRESQL) {
      return null;
    }
    try (var conn = pool(target).getConnection()) {
      String sequence = null;
      try (var ps = conn.prepareStatement("SELECT pg_get_serial_sequence(?, ?)")) {
        ps.setString(1, table);
        ps.setString(2, pkColumn);
        try (var rs = ps.executeQuery()) {
          if (rs.next()) {
            sequence = rs.getString(1);
          }
        }
      }
      if (sequence == null) {
        return null;
      }
      var ids = new ArrayList<Long>(count);
      try (var ps = conn.prepareStatement("SELECT nextval(?) FROM generate_series(1, ?)")) {
        ps.setString(1, sequence);
        ps.setInt(2, count);
        try (var rs = ps.executeQuery()) {
          while (rs.next()) {
            ids.add(rs.getLong(1));
          }
        }
      }
      return ids.size() == count ? ids : null;
    } catch (Exception e) {
      LOG.warn("Sequence allocation failed for {}.{}: {}", table, pkColumn, e.getMessage());
      return null;
    }
  }

  @Override
  public void close() {
    pools.values().forEach(HikariDataSource::close);
    pools.clear();
  }

  // ── private helpers ──────────────────────────────────────────────────────────

  private long queryMaxId(
      ConnectionProfile target, String table, String pkColumn, ConflictResolution resolution)
      throws CloneException {

    var quotedTable = "\"" + table + "\"";
    var quotedPk = "\"" + pkColumn + "\"";

    try (var conn = pool(target).getConnection();
        var stmt = conn.createStatement()) {

      if (resolution == ConflictResolution.FAIL_SAFE) {
        var countSql = "SELECT COUNT(*) FROM " + quotedTable;
        try (var rs = stmt.executeQuery(countSql)) {
          if (rs.next() && rs.getLong(1) > 0) {
            throw new CloneException(
                "FAIL_SAFE: target table '"
                    + table
                    + "' already contains rows. "
                    + "Use REGENERATE_IDENTITIES or ISOLATE_NAMESPACE to clone into a non-empty target.");
          }
        }
      }

      var maxSql = "SELECT COALESCE(MAX(" + quotedPk + "), 0) FROM " + quotedTable;
      try (var rs = stmt.executeQuery(maxSql)) {
        if (rs.next()) {
          return rs.getLong(1);
        }
      }
    } catch (CloneException e) {
      throw e;
    } catch (SQLException e) {
      LOG.warn(
          "Could not query max({}) from '{}' in target — starting from 0: {}",
          pkColumn,
          table,
          e.getMessage());
    } catch (Exception e) {
      LOG.warn("Unexpected error querying max ID for table '{}': {}", table, e.getMessage());
    }
    return 0L;
  }

  private String detectPkColumn(String tableName, DataRecord sampleRecord) {
    if (sampleRecord.hasField("id")) {
      return "id";
    }
    var singularName =
        tableName.endsWith("s")
            ? tableName.substring(0, tableName.length() - 1) + "_id"
            : tableName + "_id";
    if (sampleRecord.hasField(singularName)) {
      return singularName;
    }
    return sampleRecord.fieldNames().stream().findFirst().orElse("id");
  }

  private long maxSourceId(List<DataRecord> records, String pkColumn) {
    long max = 0L;
    for (var record : records) {
      var raw = record.get(pkColumn);
      if (raw == null) {
        continue;
      }
      try {
        long v = Long.parseLong(raw.toString());
        if (v > max) {
          max = v;
        }
      } catch (NumberFormatException ignored) {
        // non-numeric PK (UUID etc.) — no numeric max to track
      }
    }
    return max;
  }

  private String extractFieldAsString(DataRecord record, String column) {
    var value = record.get(column);
    return value == null ? null : value.toString();
  }

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
          cfg.setPoolName("identity-mapper-" + profile.id());
          profile.properties().forEach(cfg::addDataSourceProperty);
          return new HikariDataSource(cfg);
        });
  }

  private String jdbcUrl(ConnectionProfile profile) {
    return switch (profile.type()) {
      case POSTGRESQL -> profile.jdbcUrl("postgresql");
      case MYSQL -> profile.jdbcUrl("mysql");
      case SQLSERVER -> profile.jdbcUrl("sqlserver");
      case ORACLE -> profile.jdbcUrl("oracle:thin:@");
      case SQLITE -> "jdbc:sqlite:" + profile.database();
      default -> profile.jdbcUrl(profile.type().name().toLowerCase());
    };
  }
}
