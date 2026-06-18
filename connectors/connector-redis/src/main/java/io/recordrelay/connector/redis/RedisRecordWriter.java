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
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordWriter;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes {@link DataRecord}s to Redis as hashes under {@code <tableName>:<id>} keys.
 *
 * <p>Uses the {@code _key} field if present; otherwise auto-generates a sequential key.
 */
public final class RedisRecordWriter implements RecordWriter {

  private static final Logger LOG = LoggerFactory.getLogger(RedisRecordWriter.class);

  private RedisClient client;
  private StatefulRedisConnection<String, String> conn;
  private RedisCommands<String, String> sync;
  private String tablePrefix;
  private final AtomicLong seq = new AtomicLong(1);

  @Override
  public void open(ConnectionProfile profile, TableRef table, MappingDefinition mapping)
      throws ConnectorException {
    this.tablePrefix = table.tableName() + ":";
    try {
      client = RedisClient.create(RedisConnector.buildUri(profile));
      conn = client.connect();
      sync = conn.sync();
      LOG.debug("Opened writer on prefix '{}'", tablePrefix);
    } catch (Exception e) {
      throw new ConnectorException("Failed to open writer for '" + tablePrefix + "'", e);
    }
  }

  @Override
  public void write(DataRecord record) throws ConnectorException {
    String keyField = record.hasField("_key") ? String.valueOf(record.get("_key")) : null;
    String key = tablePrefix + (keyField != null ? keyField : seq.getAndIncrement());
    var hash = new LinkedHashMap<String, String>();
    for (String field : record.fieldNames()) {
      if (!"_key".equals(field)) {
        var val = record.get(field);
        hash.put(field, val == null ? "" : val.toString());
      }
    }
    try {
      sync.hset(key, hash);
    } catch (Exception e) {
      throw new ConnectorException("Failed to write key '" + key + "': " + e.getMessage(), e);
    }
  }

  @Override
  public void flush() throws ConnectorException {
    // Lettuce writes synchronously; nothing to flush.
  }

  @Override
  public void close() throws ConnectorException {
    if (conn != null) {
      conn.close();
    }
    if (client != null) {
      client.shutdown();
    }
  }
}
