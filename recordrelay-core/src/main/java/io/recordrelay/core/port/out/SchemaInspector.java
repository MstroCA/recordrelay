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

  List<TableRef> listTables(ConnectionProfile profile, DatabaseRef database)
      throws ConnectorException;

  List<ColumnMeta> inspectColumns(ConnectionProfile profile, TableRef table)
      throws ConnectorException;

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
            .collect(Collectors.toMap(c -> c.name().toLowerCase(), Function.identity(),
                (a, b) -> a));
    var compatibilities = new ArrayList<ColumnCompatibility>();
    var warnings = new ArrayList<String>();

    for (var tgt : targetCols) {
      var src = sourceByName.get(tgt.name().toLowerCase());
      if (src == null) {
        compatibilities.add(new ColumnCompatibility(null, tgt.name(), false,
            "Column '" + tgt.name() + "' missing in source"));
        warnings.add("Missing source column: " + tgt.name());
      } else if (!src.nativeType().equalsIgnoreCase(tgt.nativeType())) {
        compatibilities.add(new ColumnCompatibility(src.name(), tgt.name(), false,
            "Type mismatch: source=" + src.nativeType() + " target=" + tgt.nativeType()));
        warnings.add("Type mismatch on " + tgt.name() + ": " + src.nativeType()
            + " vs " + tgt.nativeType());
      } else {
        compatibilities.add(new ColumnCompatibility(src.name(), tgt.name(), true, null));
      }
    }

    long compatible = compatibilities.stream().filter(ColumnCompatibility::typeCompatible).count();
    double pct = targetCols.isEmpty() ? 100.0 : (compatible * 100.0 / targetCols.size());
    return new SchemaMatchReport(pct, compatibilities, warnings);
  }
}
