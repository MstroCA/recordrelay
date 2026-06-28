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
package io.recordrelay.connector.dynamodb;

import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.RootTableCandidate;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.SchemaInspector;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

/**
 * {@link SchemaInspector} adapter for DynamoDB.
 *
 * <p>DynamoDB is schemaless: only partition key (HASH) and optional sort key (RANGE) attributes are
 * statically defined. Non-key attributes are inferred by sampling up to {@value #SAMPLE_SIZE} items
 * per table. Column types reflect DynamoDB attribute types (S, N, BOOL, etc.).
 */
public final class DynamoDbSchemaInspector implements SchemaInspector {

  private static final int SAMPLE_SIZE = 50;

  @Override
  public List<TableRef> listTables(ConnectionProfile profile, DatabaseRef database)
      throws ConnectorException {
    try (var client = DynamoDbClientFactory.create(profile)) {
      var result = new ArrayList<TableRef>();
      String lastTable = null;
      do {
        var req =
            lastTable == null
                ? software.amazon.awssdk.services.dynamodb.model.ListTablesRequest.builder()
                    .limit(100)
                    .build()
                : software.amazon.awssdk.services.dynamodb.model.ListTablesRequest.builder()
                    .limit(100)
                    .exclusiveStartTableName(lastTable)
                    .build();
        var resp = client.listTables(req);
        for (var name : resp.tableNames()) {
          result.add(new TableRef(database, "", name));
        }
        lastTable = resp.lastEvaluatedTableName();
      } while (lastTable != null);
      return List.copyOf(result);
    } catch (DynamoDbException e) {
      throw new ConnectorException("Failed to list DynamoDB tables: " + e.getMessage(), e);
    }
  }

  @Override
  public List<RootTableCandidate> detectRootCandidates(
      ConnectionProfile profile, DatabaseRef database) throws ConnectorException {
    var tables = listTables(profile, database);
    var candidates = new ArrayList<RootTableCandidate>(Math.min(5, tables.size()));
    for (int i = 0; i < Math.min(5, tables.size()); i++) {
      var t = tables.get(i);
      candidates.add(
          new RootTableCandidate(t.tableName(), 0, 0, 0, "DynamoDB table — no FK relationships"));
    }
    return List.copyOf(candidates);
  }

  @Override
  public List<ColumnMeta> inspectColumns(ConnectionProfile profile, TableRef table)
      throws ConnectorException {
    try (var client = DynamoDbClientFactory.create(profile)) {
      var keyAttrs = describeKeySchema(client, table.tableName());
      var fieldOrder = new LinkedHashMap<String, String>();
      // Add key attributes first so they appear at the top
      keyAttrs.forEach((k, v) -> fieldOrder.put(k, v));
      sampleItems(client, table.tableName(), keyAttrs.keySet(), fieldOrder);

      var result = new ArrayList<ColumnMeta>();
      int ordinal = 1;
      Set<String> pkCols = keyAttrs.keySet();
      for (var entry : fieldOrder.entrySet()) {
        boolean isPk = pkCols.contains(entry.getKey());
        result.add(
            new ColumnMeta(entry.getKey(), entry.getValue(), !isPk, isPk, false, ordinal++, null));
      }
      return List.copyOf(result);
    } catch (DynamoDbException e) {
      throw new ConnectorException(
          "Failed to inspect DynamoDB table '" + table.tableName() + "': " + e.getMessage(), e);
    }
  }

  /**
   * Returns the key schema attributes as {@code name → type} using {@code describeTable}. HASH key
   * is always first; RANGE key is second if present.
   */
  private static LinkedHashMap<String, String> describeKeySchema(
      DynamoDbClient client, String tableName) {
    var desc = client.describeTable(r -> r.tableName(tableName)).table();
    // Build attribute type lookup from attributeDefinitions
    var attrTypes =
        desc.attributeDefinitions().stream()
            .collect(
                Collectors.toMap(
                    a -> a.attributeName(), a -> dynamoTypeToString(a.attributeType())));
    var keys = new LinkedHashMap<String, String>();
    // Ensure HASH comes before RANGE
    desc.keySchema().stream()
        .filter(k -> k.keyType() == KeyType.HASH)
        .forEach(k -> keys.put(k.attributeName(), attrTypes.getOrDefault(k.attributeName(), "S")));
    desc.keySchema().stream()
        .filter(k -> k.keyType() == KeyType.RANGE)
        .forEach(k -> keys.put(k.attributeName(), attrTypes.getOrDefault(k.attributeName(), "S")));
    return keys;
  }

  private static void sampleItems(
      DynamoDbClient client,
      String tableName,
      Set<String> knownKeys,
      LinkedHashMap<String, String> fieldOrder) {
    var req = ScanRequest.builder().tableName(tableName).limit(SAMPLE_SIZE).build();
    try {
      var resp = client.scan(req);
      for (var item : resp.items()) {
        item.forEach((col, av) -> fieldOrder.computeIfAbsent(col, k -> inferAttributeType(av)));
      }
    } catch (DynamoDbException ignored) {
      // sampling failure is non-fatal; key attributes are always present
    }
  }

  private static String inferAttributeType(
      software.amazon.awssdk.services.dynamodb.model.AttributeValue av) {
    if (av.s() != null) return "S";
    if (av.n() != null) return "N";
    if (av.bool() != null) return "BOOL";
    if (av.b() != null) return "B";
    if (av.hasSs()) return "SS";
    if (av.hasNs()) return "NS";
    if (av.hasL()) return "L";
    if (av.hasM()) return "M";
    return "S";
  }

  private static String dynamoTypeToString(ScalarAttributeType type) {
    return switch (type) {
      case S -> "S";
      case N -> "N";
      case B -> "B";
      default -> "S";
    };
  }
}
