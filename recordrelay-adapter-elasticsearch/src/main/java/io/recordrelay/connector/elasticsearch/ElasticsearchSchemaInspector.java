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
package io.recordrelay.connector.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.SchemaInspector;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link SchemaInspector} for Elasticsearch.
 *
 * <p>Indices play the role of tables; field mappings play the role of columns.
 */
public final class ElasticsearchSchemaInspector implements SchemaInspector {

  @Override
  public List<TableRef> listTables(ConnectionProfile profile, DatabaseRef database)
      throws ConnectorException {
    try (var transport = ElasticsearchConnector.buildTransport(profile)) {
      var client = new ElasticsearchClient(transport);
      var aliases = client.indices().getAlias();
      var result = new ArrayList<TableRef>();
      for (var entry : aliases.result().entrySet()) {
        result.add(new TableRef(database, "", entry.getKey()));
      }
      return List.copyOf(result);
    } catch (Exception e) {
      throw new ConnectorException("Failed to list indices: " + e.getMessage(), e);
    }
  }

  @Override
  public List<ColumnMeta> inspectColumns(ConnectionProfile profile, TableRef table)
      throws ConnectorException {
    try (var transport = ElasticsearchConnector.buildTransport(profile)) {
      var client = new ElasticsearchClient(transport);
      GetMappingResponse mappings = client.indices().getMapping(m -> m.index(table.tableName()));
      var indexMapping = mappings.result().get(table.tableName());
      if (indexMapping == null) {
        return List.of();
      }
      var result = new ArrayList<ColumnMeta>();
      var ordinal = new AtomicInteger(1);
      var props = indexMapping.mappings().properties();
      for (var entry : props.entrySet()) {
        String name = entry.getKey();
        String type = entry.getValue()._kind().jsonValue();
        result.add(new ColumnMeta(name, type, true, false, false, ordinal.getAndIncrement(), null));
      }
      return List.copyOf(result);
    } catch (Exception e) {
      throw new ConnectorException("Failed to inspect columns for '" + table.tableName() + "'", e);
    }
  }
}
