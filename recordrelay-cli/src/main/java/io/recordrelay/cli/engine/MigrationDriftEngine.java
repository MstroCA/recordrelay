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
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.MigrationDriftItem;
import io.recordrelay.core.domain.MigrationDriftItem.DriftKind;
import io.recordrelay.core.domain.MigrationDriftReport;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.in.MigrationDriftUseCase;
import io.recordrelay.core.port.out.ContextProviderPort;
import io.recordrelay.core.port.out.SchemaInspector;
import io.recordrelay.core.spi.ConnectorRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compares every table and column in two databases and returns a flat list of discrepancies.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>List all tables in source and target.
 *   <li>Emit {@link DriftKind#TABLE_MISSING} for tables only in source.
 *   <li>Emit {@link DriftKind#TABLE_EXTRA} for tables only in target.
 *   <li>For tables present in both, compare columns and emit column-level diffs.
 * </ol>
 */
public final class MigrationDriftEngine implements MigrationDriftUseCase {

  private final Function<ConnectionProfile, ContextProviderPort> connectorLookup;

  public MigrationDriftEngine() {
    this(ConnectorRegistry::findConnector);
  }

  MigrationDriftEngine(Function<ConnectionProfile, ContextProviderPort> connectorLookup) {
    this.connectorLookup = connectorLookup;
  }

  @Override
  public MigrationDriftReport analyzeDrift(
      ConnectionProfile sourceProfile,
      DatabaseRef sourceDatabase,
      ConnectionProfile targetProfile,
      DatabaseRef targetDatabase) {

    try {
      SchemaInspector srcInspector = connectorLookup.apply(sourceProfile).schemaInspector();
      SchemaInspector tgtInspector = connectorLookup.apply(targetProfile).schemaInspector();

      List<TableRef> srcTables = srcInspector.listTables(sourceProfile, sourceDatabase);
      List<TableRef> tgtTables = tgtInspector.listTables(targetProfile, targetDatabase);

      Map<String, TableRef> srcByName =
          srcTables.stream()
              .collect(Collectors.toMap(t -> t.tableName().toLowerCase(), Function.identity(), (a, b) -> a));
      Map<String, TableRef> tgtByName =
          tgtTables.stream()
              .collect(Collectors.toMap(t -> t.tableName().toLowerCase(), Function.identity(), (a, b) -> a));

      var items = new ArrayList<MigrationDriftItem>();

      // Tables only in source
      for (TableRef src : srcTables) {
        if (!tgtByName.containsKey(src.tableName().toLowerCase())) {
          items.add(new MigrationDriftItem(DriftKind.TABLE_MISSING, src.tableName(), null, src.tableName(), null));
        }
      }

      // Tables only in target
      for (TableRef tgt : tgtTables) {
        if (!srcByName.containsKey(tgt.tableName().toLowerCase())) {
          items.add(new MigrationDriftItem(DriftKind.TABLE_EXTRA, tgt.tableName(), null, null, tgt.tableName()));
        }
      }

      // Tables in both — compare columns
      for (TableRef src : srcTables) {
        TableRef tgt = tgtByName.get(src.tableName().toLowerCase());
        if (tgt == null) continue;

        var srcCols = srcInspector.inspectColumns(sourceProfile, src);
        var tgtCols = tgtInspector.inspectColumns(targetProfile, tgt);

        Map<String, String> srcColTypes =
            srcCols.stream()
                .collect(Collectors.toMap(c -> c.name().toLowerCase(), c -> c.nativeType(), (a, b) -> a));
        Map<String, String> tgtColTypes =
            tgtCols.stream()
                .collect(Collectors.toMap(c -> c.name().toLowerCase(), c -> c.nativeType(), (a, b) -> a));

        // Columns in source but not target
        for (var col : srcCols) {
          String key = col.name().toLowerCase();
          if (!tgtColTypes.containsKey(key)) {
            items.add(new MigrationDriftItem(DriftKind.COLUMN_MISSING, src.tableName(), col.name(), col.nativeType(), null));
          } else if (!col.nativeType().equalsIgnoreCase(tgtColTypes.get(key))) {
            items.add(new MigrationDriftItem(DriftKind.TYPE_MISMATCH, src.tableName(), col.name(), col.nativeType(), tgtColTypes.get(key)));
          }
        }

        // Columns in target but not source
        for (var col : tgtCols) {
          if (!srcColTypes.containsKey(col.name().toLowerCase())) {
            items.add(new MigrationDriftItem(DriftKind.COLUMN_EXTRA, tgt.tableName(), col.name(), null, col.nativeType()));
          }
        }
      }

      return new MigrationDriftReport(items);
    } catch (ConnectorException e) {
      throw new RuntimeException(e);
    }
  }
}
