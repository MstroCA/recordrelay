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
import io.recordrelay.core.domain.RowCountEntry;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.ContextProviderPort;
import io.recordrelay.core.port.out.SchemaInspector;
import io.recordrelay.core.spi.ConnectorRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compares row counts for every table present in both source and target databases.
 *
 * <p>Tables present only in source or only in target are included with a count of {@code -2}
 * (sentinel for "table absent") on the missing side.
 */
public final class RowCountDiffEngine {

  /** Sentinel value used when a table does not exist on that side. */
  public static final long ABSENT = -2L;

  private final Function<ConnectionProfile, ContextProviderPort> connectorLookup;

  public RowCountDiffEngine() {
    this(ConnectorRegistry::findConnector);
  }

  RowCountDiffEngine(Function<ConnectionProfile, ContextProviderPort> connectorLookup) {
    this.connectorLookup = connectorLookup;
  }

  /**
   * Counts rows for every table in both databases and returns per-table results.
   *
   * @param sourceProfile source connection
   * @param sourceDb source database
   * @param targetProfile target connection
   * @param targetDb target database
   * @return list of row count entries ordered by table name
   */
  public List<RowCountEntry> compare(
      ConnectionProfile sourceProfile,
      DatabaseRef sourceDb,
      ConnectionProfile targetProfile,
      DatabaseRef targetDb) {

    try {
      SchemaInspector srcInspector = connectorLookup.apply(sourceProfile).schemaInspector();
      SchemaInspector tgtInspector = connectorLookup.apply(targetProfile).schemaInspector();

      List<TableRef> srcTables = srcInspector.listTables(sourceProfile, sourceDb);
      List<TableRef> tgtTables = tgtInspector.listTables(targetProfile, targetDb);

      Map<String, TableRef> srcByName = index(srcTables);
      Map<String, TableRef> tgtByName = index(tgtTables);

      var results = new ArrayList<RowCountEntry>();

      for (TableRef src : srcTables) {
        TableRef tgt = tgtByName.get(src.tableName().toLowerCase());
        long srcCount = safeCount(srcInspector, sourceProfile, src);
        long tgtCount = tgt != null ? safeCount(tgtInspector, targetProfile, tgt) : ABSENT;
        results.add(new RowCountEntry(src.tableName(), srcCount, tgtCount));
      }

      // Tables only in target
      for (TableRef tgt : tgtTables) {
        if (!srcByName.containsKey(tgt.tableName().toLowerCase())) {
          long tgtCount = safeCount(tgtInspector, targetProfile, tgt);
          results.add(new RowCountEntry(tgt.tableName(), ABSENT, tgtCount));
        }
      }

      results.sort(java.util.Comparator.comparing(RowCountEntry::tableName));
      return results;
    } catch (ConnectorException e) {
      throw new RuntimeException(e);
    }
  }

  private static Map<String, TableRef> index(List<TableRef> tables) {
    return tables.stream()
        .collect(
            Collectors.toMap(t -> t.tableName().toLowerCase(), Function.identity(), (a, b) -> a));
  }

  private static long safeCount(
      SchemaInspector inspector, ConnectionProfile profile, TableRef table) {
    try {
      return inspector.countRows(profile, table);
    } catch (Exception e) {
      return -1L;
    }
  }
}
