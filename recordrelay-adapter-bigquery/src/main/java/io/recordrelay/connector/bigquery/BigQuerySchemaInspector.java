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
package io.recordrelay.connector.bigquery;

import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.DatasetId;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.StandardTableDefinition;
import com.google.cloud.bigquery.TableId;
import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.RootTableCandidate;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.SchemaInspector;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link SchemaInspector} adapter for BigQuery.
 *
 * <p>Column metadata is derived from BigQuery table schemas, which are always explicitly defined.
 * BigQuery does not enforce primary keys or foreign keys, so {@code isPk} is inferred from column
 * names ending with {@code _id} that appear first, and {@code detectRootCandidates} returns tables
 * ranked by name heuristics only.
 */
public final class BigQuerySchemaInspector implements SchemaInspector {

  @Override
  public List<TableRef> listTables(ConnectionProfile profile, DatabaseRef database)
      throws ConnectorException {
    try {
      var bq = BigQueryClientFactory.create(profile);
      var datasetId = DatasetId.of(profile.host(), database.name());
      var result = new ArrayList<TableRef>();
      bq.listTables(datasetId)
          .iterateAll()
          .forEach(
              t -> result.add(new TableRef(database, database.name(), t.getTableId().getTable())));
      return List.copyOf(result);
    } catch (BigQueryException e) {
      throw new ConnectorException(
          "Failed to list BigQuery tables in dataset '" + database.name() + "': " + e.getMessage(),
          e);
    }
  }

  @Override
  public List<RootTableCandidate> detectRootCandidates(
      ConnectionProfile profile, DatabaseRef database) throws ConnectorException {
    var tables = listTables(profile, database);
    var candidates = new ArrayList<RootTableCandidate>(Math.min(5, tables.size()));
    for (int i = 0; i < Math.min(5, tables.size()); i++) {
      var t = tables.get(i);
      int score = nameBonus(t.tableName());
      candidates.add(
          new RootTableCandidate(
              t.tableName(), score, 0, 0, "BigQuery table — no FK relationships"));
    }
    candidates.sort((a, b) -> Integer.compare(b.score(), a.score()));
    return List.copyOf(candidates);
  }

  @Override
  public List<ColumnMeta> inspectColumns(ConnectionProfile profile, TableRef table)
      throws ConnectorException {
    try {
      var bq = BigQueryClientFactory.create(profile);
      var tableId = TableId.of(profile.host(), table.database().name(), table.tableName());
      var bqTable = bq.getTable(tableId);
      if (bqTable == null) {
        throw new ConnectorException("BigQuery table not found: " + tableId);
      }
      var schema = bqTable.<StandardTableDefinition>getDefinition().getSchema();
      if (schema == null) {
        return List.of();
      }
      var result = new ArrayList<ColumnMeta>();
      int ordinal = 1;
      for (var field : schema.getFields()) {
        boolean isPk = inferPk(field, ordinal);
        boolean nullable = field.getMode() != Field.Mode.REQUIRED;
        result.add(
            new ColumnMeta(
                field.getName(), field.getType().name(), nullable, isPk, false, ordinal++, null));
      }
      return List.copyOf(result);
    } catch (BigQueryException e) {
      throw new ConnectorException(
          "Failed to inspect BigQuery table '" + table.qualifiedName() + "': " + e.getMessage(), e);
    }
  }

  private static boolean inferPk(Field field, int ordinal) {
    // BigQuery has no formal PK — use first column or columns named *_id / id as a heuristic
    return ordinal == 1
        || field.getName().equalsIgnoreCase("id")
        || field.getName().toLowerCase(java.util.Locale.ROOT).endsWith("_id") && ordinal <= 2;
  }

  private static int nameBonus(String tableName) {
    String lower = tableName.toLowerCase(java.util.Locale.ROOT);
    for (String root :
        List.of(
            "order",
            "customer",
            "account",
            "user",
            "patient",
            "employee",
            "invoice",
            "contract",
            "project",
            "subscription",
            "transaction",
            "ticket",
            "request",
            "sale",
            "booking")) {
      if (lower.equals(root) || lower.startsWith(root + "_") || lower.endsWith("_" + root)) {
        return 5;
      }
    }
    return 0;
  }
}
