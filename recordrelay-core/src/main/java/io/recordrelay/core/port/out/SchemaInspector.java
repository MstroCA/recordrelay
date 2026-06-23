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
package io.recordrelay.core.port.out;

import io.recordrelay.core.domain.ColumnCompatibility;
import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.RootTableCandidate;
import io.recordrelay.core.domain.SchemaMatchReport;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reads schema metadata from a storage engine. */
public interface SchemaInspector {

  /** Returns all tables (or collections / indices) visible in {@code database}. */
  List<TableRef> listTables(ConnectionProfile profile, DatabaseRef database)
      throws ConnectorException;

  /** Returns column (or field) metadata for the given {@code table}. */
  List<ColumnMeta> inspectColumns(ConnectionProfile profile, TableRef table)
      throws ConnectorException;

  /**
   * Returns the total row count for the given table.
   *
   * <p>The default implementation returns {@code -1}, signalling "not supported". Connectors backed
   * by SQL engines should override this with a {@code SELECT COUNT(*)} query.
   *
   * @param profile connection parameters
   * @param table the table to count
   * @return number of rows, or {@code -1} when the connector does not support counting
   * @throws ConnectorException if the query fails
   */
  default long countRows(ConnectionProfile profile, TableRef table) throws ConnectorException {
    return -1L;
  }

  /**
   * Analyses the FK graph of the given database and returns the top root-table candidates ranked by
   * score.
   *
   * <p>The default implementation returns an empty list (not supported). SQL connectors that extend
   * {@code AbstractJdbcSchemaInspector} override this with a real {@link java.sql.DatabaseMetaData}
   * implementation.
   *
   * @param profile connection parameters
   * @param database database to inspect
   * @return ordered list of candidates (best first), may be empty
   * @throws ConnectorException if the schema query fails
   */
  default List<RootTableCandidate> detectRootCandidates(
      ConnectionProfile profile, DatabaseRef database) throws ConnectorException {
    return List.of();
  }

  /**
   * Compares source and target column lists and returns a compatibility report.
   *
   * <p>Default implementation: a target column is compatible when a source column with the same
   * name (case-insensitive) and the same native type exists. Missing or type-mismatched columns
   * produce warnings.
   */
  default SchemaMatchReport analyzeCompatibility(
      List<ColumnMeta> sourceCols, List<ColumnMeta> targetCols) {
    Map<String, ColumnMeta> sourceByName =
        sourceCols.stream()
            .collect(
                Collectors.toMap(c -> c.name().toLowerCase(), Function.identity(), (a, b) -> a));
    var compatibilities = new ArrayList<ColumnCompatibility>();
    var warnings = new ArrayList<String>();

    for (var tgt : targetCols) {
      var src = sourceByName.get(tgt.name().toLowerCase());
      if (src == null) {
        compatibilities.add(
            new ColumnCompatibility(
                null, tgt.name(), false, "Column '" + tgt.name() + "' missing in source"));
        warnings.add("Missing source column: " + tgt.name());
      } else if (!src.nativeType().equalsIgnoreCase(tgt.nativeType())) {
        compatibilities.add(
            new ColumnCompatibility(
                src.name(),
                tgt.name(),
                false,
                "Type mismatch: source=" + src.nativeType() + " target=" + tgt.nativeType()));
        warnings.add(
            "Type mismatch on " + tgt.name() + ": " + src.nativeType() + " vs " + tgt.nativeType());
      } else {
        compatibilities.add(new ColumnCompatibility(src.name(), tgt.name(), true, null));
      }
    }

    long compatible = compatibilities.stream().filter(ColumnCompatibility::typeCompatible).count();
    double pct = targetCols.isEmpty() ? 100.0 : (compatible * 100.0 / targetCols.size());
    return new SchemaMatchReport(pct, compatibilities, warnings);
  }
}
