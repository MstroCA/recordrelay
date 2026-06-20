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

import io.recordrelay.core.clone.domain.IdentityMapping;
import io.recordrelay.core.clone.domain.RelationshipGraph;
import io.recordrelay.core.domain.DataRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rewrites primary-key and foreign-key column values in a set of extracted records using the
 * provided {@link IdentityMapping}.
 *
 * <p>Referential integrity is preserved by remapping both PKs (so the target row uses its new ID)
 * and all FK columns that point to remapped tables (so foreign references resolve correctly).
 *
 * <p>Example:
 *
 * <pre>
 * SOURCE: customers.id=1, orders.customer_id=1
 * MAPPING: customers:{1→42001}, orders:{7→78001}
 * RESULT:  customers.id=42001, orders.customer_id=42001
 * </pre>
 *
 * <p>Only columns that appear in the {@link IdentityMapping} or in the FK edges of the {@link
 * RelationshipGraph} are modified. All other field values pass through unchanged.
 */
final class FkRemapper {

  private static final Logger LOG = LoggerFactory.getLogger(FkRemapper.class);

  private FkRemapper() {}

  /**
   * Remaps all PK and FK values in {@code records} using the given identity mapping and
   * relationship graph.
   *
   * @param records extracted records grouped by table name
   * @param mapping the identity mapping produced during the allocation phase
   * @param graph the relationship graph used to discover FK columns
   * @param rootTable name of the root table (used for PK column detection)
   * @return a new map with all records remapped; original map is not modified
   */
  static Map<String, List<DataRecord>> remap(
      Map<String, List<DataRecord>> records,
      IdentityMapping mapping,
      RelationshipGraph graph,
      String rootTable) {

    if (mapping.isEmpty()) {
      return records;
    }

    // Build FK metadata: tableName → list of (fkColumn, referencedTable)
    var fksByTable = new LinkedHashMap<String, List<FkEdge>>();
    for (var edge : graph.edges()) {
      fksByTable
          .computeIfAbsent(edge.fromNode().tableName(), k -> new ArrayList<>())
          .add(new FkEdge(edge.fromColumn(), edge.toNode().tableName()));
    }

    // Build PK column lookup: tableName → pkColumn
    var pkByTable = new LinkedHashMap<String, String>();
    pkByTable.put(rootTable, "id");
    for (var edge : graph.edges()) {
      pkByTable.putIfAbsent(edge.toNode().tableName(), edge.toColumn());
      pkByTable.putIfAbsent(edge.fromNode().tableName(), "id");
    }

    var result = new LinkedHashMap<String, List<DataRecord>>();
    for (var entry : records.entrySet()) {
      var table = entry.getKey();
      var pkColumn = pkByTable.getOrDefault(table, "id");
      var fks = fksByTable.getOrDefault(table, List.of());

      var remapped = new ArrayList<DataRecord>(entry.getValue().size());
      for (var record : entry.getValue()) {
        remapped.add(remapRecord(record, table, pkColumn, fks, mapping));
      }
      result.put(table, remapped);
    }

    LOG.debug("FK remapping complete for {} tables", result.size());
    return result;
  }

  private static DataRecord remapRecord(
      DataRecord record, String table, String pkColumn, List<FkEdge> fks, IdentityMapping mapping) {

    var fields = new LinkedHashMap<>(record.fields());
    boolean modified = false;

    // Remap PK
    var pkValue = fields.get(pkColumn);
    if (pkValue != null) {
      var newId = mapping.resolve(table, pkValue.toString());
      if (newId.isPresent()) {
        fields.put(pkColumn, castToOriginalType(pkValue, newId.get()));
        modified = true;
      }
    }

    // Remap FK columns
    for (var fk : fks) {
      var fkValue = fields.get(fk.column());
      if (fkValue != null) {
        var newFk = mapping.resolve(fk.referencedTable(), fkValue.toString());
        if (newFk.isPresent()) {
          fields.put(fk.column(), castToOriginalType(fkValue, newFk.get()));
          modified = true;
        }
      }
    }

    return modified ? DataRecord.of(fields) : record;
  }

  /**
   * Casts a new ID string to the same Java type as the original value to preserve DB-native type
   * fidelity during write.
   */
  private static Object castToOriginalType(Object original, String newId) {
    if (original instanceof Long) {
      try {
        return Long.parseLong(newId);
      } catch (NumberFormatException ignored) {
      }
    } else if (original instanceof Integer) {
      try {
        return Integer.parseInt(newId);
      } catch (NumberFormatException ignored) {
      }
    } else if (original instanceof Short) {
      try {
        return Short.parseShort(newId);
      } catch (NumberFormatException ignored) {
      }
    }
    return newId;
  }

  /** Lightweight FK edge descriptor. */
  private record FkEdge(String column, String referencedTable) {}
}
