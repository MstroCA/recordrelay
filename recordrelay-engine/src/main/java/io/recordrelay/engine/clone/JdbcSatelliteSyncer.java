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

import io.recordrelay.core.clone.domain.ClonedTableSummary;
import io.recordrelay.core.clone.domain.ConflictResolution;
import io.recordrelay.core.clone.domain.FieldOverrideConfig;
import io.recordrelay.core.clone.domain.IdentityMapping;
import io.recordrelay.core.clone.domain.SatelliteTable;
import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.core.clone.port.out.IdentityMapperPort;
import io.recordrelay.core.clone.port.out.RecordFetcherPort;
import io.recordrelay.core.clone.port.out.SatelliteSyncPort;
import io.recordrelay.core.clone.port.out.SequenceSyncPort;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.spi.ConnectorRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC-backed {@link SatelliteSyncPort}.
 *
 * <p>For each configured {@link SatelliteTable} this:
 *
 * <ol>
 *   <li>reads the companion rows from the secondary <em>source</em> keyed by the source root ids;
 *   <li>remaps the link column to the newly allocated target root id (via the primary clone's
 *       identity mapping — falls back to the source id when no remap exists, e.g. SKIP_EXISTING);
 *   <li>allocates a fresh primary key for the companion row against the secondary <em>target</em>
 *       so it never collides with pre-existing rows;
 *   <li>applies the same field overrides used by the primary clone (e.g. {@code mukellef_vkn},
 *       {@code kullanici_kod});
 *   <li>writes the rows into the secondary target and bumps its identity sequence.
 * </ol>
 *
 * <p>A failure syncing one satellite is reported as a warning and does not abort the others.
 */
public final class JdbcSatelliteSyncer implements SatelliteSyncPort, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcSatelliteSyncer.class);

  private final RecordFetcherPort fetcher;
  private final IdentityMapperPort identityMapper;
  private final SequenceSyncPort sequenceSyncer;

  public JdbcSatelliteSyncer() {
    this(new JdbcRecordFetcher(), new DefaultIdentityMapper(), new JdbcSequenceSynchronizer());
  }

  public JdbcSatelliteSyncer(
      RecordFetcherPort fetcher,
      IdentityMapperPort identityMapper,
      SequenceSyncPort sequenceSyncer) {
    this.fetcher = fetcher;
    this.identityMapper = identityMapper;
    this.sequenceSyncer = sequenceSyncer;
  }

  @Override
  public SatelliteSyncResult sync(
      String rootTable,
      Collection<String> sourceRootIds,
      IdentityMapping rootMapping,
      List<SatelliteTable> satellites,
      FieldOverrideConfig overrides,
      CloneProgressListener listener)
      throws CloneException {
    var sink = new Sink(new ArrayList<>(), new ArrayList<>());
    if (satellites == null || satellites.isEmpty() || sourceRootIds.isEmpty()) {
      return SatelliteSyncResult.empty();
    }
    for (var satellite : satellites) {
      try {
        processSatellite(
            satellite, rootTable, sourceRootIds, rootMapping, overrides, listener, sink);
      } catch (CloneException e) {
        var msg = "Satellite '" + satellite.table() + "' sync failed: " + e.getMessage();
        sink.warnings().add(msg);
        listener.onWarning(msg);
        LOG.warn(
            "Satellite sync failed  table={}  reason={}", satellite.table(), e.getMessage(), e);
      }
    }
    return new SatelliteSyncResult(sink.summaries(), sink.warnings());
  }

  /** Accumulates per-satellite write summaries and non-fatal warnings across the run. */
  private record Sink(List<ClonedTableSummary> summaries, List<String> warnings) {}

  private void processSatellite(
      SatelliteTable satellite,
      String rootTable,
      Collection<String> sourceRootIds,
      IdentityMapping rootMapping,
      FieldOverrideConfig overrides,
      CloneProgressListener listener,
      Sink sink)
      throws CloneException {
    listener.onTableExtractionStarted(satellite.table());
    var collected = collectAndRelink(satellite, rootTable, sourceRootIds, rootMapping);
    listener.onTableExtractionCompleted(satellite.table(), collected.size());
    if (collected.isEmpty()) {
      var msg =
          "Satellite '"
              + satellite.table()
              + "': no source rows found for "
              + satellite.linkColumn()
              + " in "
              + sourceRootIds;
      sink.warnings().add(msg);
      listener.onWarning(msg);
      return;
    }

    // Regenerate the companion's own primary key against the secondary target.
    var pkMapping =
        identityMapper.allocate(
            satellite.target(),
            satellite.table(),
            Map.of(satellite.table(), collected),
            ConflictResolution.REGENERATE_IDENTITIES);
    var pkColumn =
        satellite.pkColumn() != null
            ? satellite.pkColumn()
            : detectPkColumn(satellite.table(), collected.get(0));

    var toWrite = new ArrayList<DataRecord>(collected.size());
    for (var record : collected) {
      var fields = new LinkedHashMap<>(record.fields());
      var oldPk = stringValue(fields.get(pkColumn));
      if (oldPk != null) {
        pkMapping
            .resolve(satellite.table(), oldPk)
            .ifPresent(newPk -> fields.put(pkColumn, castLike(fields.get(pkColumn), newPk)));
      }
      applyOverrides(fields, satellite.table(), overrides);
      toWrite.add(new DataRecord(fields));
    }

    writeRows(satellite.target(), satellite.table(), toWrite, listener, sink.warnings());
    sequenceSyncer.synchronize(satellite.target(), pkMapping);
    sink.summaries().add(new ClonedTableSummary(satellite.table(), toWrite.size()));
  }

  /**
   * Reads companion rows for every source root id and remaps the link column to the new root id.
   */
  private List<DataRecord> collectAndRelink(
      SatelliteTable satellite,
      String rootTable,
      Collection<String> sourceRootIds,
      IdentityMapping rootMapping)
      throws CloneException {
    var collected = new ArrayList<DataRecord>();
    for (var sourceRootId : sourceRootIds) {
      var rows =
          fetcher.fetchByForeignKey(
              satellite.source(), satellite.table(), satellite.linkColumn(), sourceRootId);
      if (rows.isEmpty()) {
        continue;
      }
      var newRootId = rootMapping.resolve(rootTable, sourceRootId).orElse(sourceRootId);
      for (var row : rows) {
        var fields = new LinkedHashMap<>(row.fields());
        fields.put(satellite.linkColumn(), castLike(fields.get(satellite.linkColumn()), newRootId));
        collected.add(new DataRecord(fields));
      }
    }
    return collected;
  }

  private void writeRows(
      ConnectionProfile target,
      String tableName,
      List<DataRecord> records,
      CloneProgressListener listener,
      List<String> warnings)
      throws CloneException {
    listener.onImportStarted(tableName);
    var tableRef = new TableRef(new DatabaseRef(target.database(), target.type()), "", tableName);
    try {
      var connector = ConnectorRegistry.findConnector(target);
      try (var writer = connector.createWriter()) {
        writer.open(target, tableRef, false);
        for (var record : records) {
          writer.write(record);
        }
        writer.flush();
      }
      listener.onImportCompleted(tableName, records.size());
      LOG.debug("Satellite wrote  table={}  rows={}", tableName, records.size());
    } catch (Exception e) {
      var msg = "Failed to write satellite '" + tableName + "': " + e.getMessage();
      warnings.add(msg);
      listener.onWarning(msg);
      listener.onImportCompleted(tableName, 0);
      throw new CloneException(msg, e);
    }
  }

  private static void applyOverrides(
      Map<String, Object> fields, String tableName, FieldOverrideConfig overrides) {
    if (overrides == null || overrides.isEmpty()) {
      return;
    }
    for (var override : overrides.getOverridesFor(tableName)) {
      if (fields.containsKey(override.column())) {
        fields.put(override.column(), override.value());
      }
    }
  }

  /**
   * Mirrors {@link DefaultIdentityMapper}'s PK-detection order: id → {@code <table>_id} → first.
   */
  private static String detectPkColumn(String tableName, DataRecord sample) {
    if (sample.hasField("id")) {
      return "id";
    }
    var singular =
        tableName.endsWith("s")
            ? tableName.substring(0, tableName.length() - 1) + "_id"
            : tableName + "_id";
    if (sample.hasField(singular)) {
      return singular;
    }
    return sample.fieldNames().stream().findFirst().orElse("id");
  }

  private static String stringValue(Object value) {
    return value == null ? null : value.toString();
  }

  /** Casts {@code newValue} to the numeric type of {@code original} so JDBC binding stays typed. */
  private static Object castLike(Object original, String newValue) {
    if (original instanceof Long) {
      try {
        return Long.parseLong(newValue);
      } catch (NumberFormatException ignored) {
        // fall through to string
      }
    } else if (original instanceof Integer) {
      try {
        return Integer.parseInt(newValue);
      } catch (NumberFormatException ignored) {
        // fall through to string
      }
    } else if (original instanceof java.math.BigInteger) {
      try {
        return new java.math.BigInteger(newValue);
      } catch (NumberFormatException ignored) {
        // fall through to string
      }
    }
    return newValue;
  }

  @Override
  public void close() {
    closeQuietly(fetcher);
    closeQuietly(identityMapper);
  }

  private static void closeQuietly(Object o) {
    if (o instanceof AutoCloseable c) {
      try {
        c.close();
      } catch (Exception e) {
        LOG.warn("Error closing {}", o.getClass().getSimpleName(), e);
      }
    }
  }
}
