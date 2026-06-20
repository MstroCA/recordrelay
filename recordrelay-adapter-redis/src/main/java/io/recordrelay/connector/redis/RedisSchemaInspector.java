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
package io.recordrelay.connector.redis;

import io.lettuce.core.RedisClient;
import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.SchemaInspector;
import java.util.List;

/**
 * {@link SchemaInspector} for Redis.
 *
 * <p>Redis is schemaless; tables map to key prefixes (e.g., {@code user:*}). This inspector returns
 * the prefix patterns visible via SCAN. Columns are not introspectable without sample data.
 */
public final class RedisSchemaInspector implements SchemaInspector {

  @Override
  public List<TableRef> listTables(ConnectionProfile profile, DatabaseRef database)
      throws ConnectorException {
    try (var client = RedisClient.create(RedisConnector.buildUri(profile));
        var conn = client.connect()) {
      var sync = conn.sync();
      var keys = sync.keys("*");
      var prefixes =
          keys.stream()
              .map(k -> k.contains(":") ? k.substring(0, k.indexOf(':')) : k)
              .distinct()
              .map(p -> new TableRef(database, "", p))
              .toList();
      return prefixes;
    } catch (Exception e) {
      throw new ConnectorException("Failed to list key prefixes: " + e.getMessage(), e);
    }
  }

  @Override
  public List<ColumnMeta> inspectColumns(ConnectionProfile profile, TableRef table)
      throws ConnectorException {
    // Redis hash fields cannot be enumerated without sample data.
    return List.of();
  }
}
