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
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.sync.RedisCommands;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordReader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads Redis hash entries matching the pattern {@code <tableName>:*} using incremental SCAN.
 *
 * <p>Each hash key maps to one {@link DataRecord}; the "id" field carries the key suffix.
 */
public final class RedisRecordReader implements RecordReader {

  private static final Logger LOG = LoggerFactory.getLogger(RedisRecordReader.class);
  private static final long SCAN_COUNT = 200L;

  private RedisClient client;
  private io.lettuce.core.api.StatefulRedisConnection<String, String> conn;
  private RedisCommands<String, String> sync;
  private String pattern;
  private ScanCursor cursor = ScanCursor.INITIAL;
  private final Deque<String> keyBuffer = new ArrayDeque<>();
  private boolean exhausted = false;

  @Override
  public void open(ConnectionProfile profile, TableRef table, MappingDefinition mapping)
      throws ConnectorException {
    this.pattern = table.tableName() + ":*";
    try {
      client = RedisClient.create(RedisConnector.buildUri(profile));
      conn = client.connect();
      sync = conn.sync();
      scanNext();
      LOG.debug("Opened reader for pattern '{}'", pattern);
    } catch (Exception e) {
      throw new ConnectorException("Failed to open reader for pattern '" + pattern + "'", e);
    }
  }

  @Override
  public Optional<DataRecord> readNext() throws ConnectorException {
    if (!hasMore()) {
      return Optional.empty();
    }
    var key = keyBuffer.poll();
    if (keyBuffer.isEmpty() && !exhausted) {
      scanNext();
    }
    var hash = sync.hgetall(key);
    var fields = new LinkedHashMap<String, Object>(hash.size() + 1);
    fields.put("_key", key);
    fields.putAll(hash);
    return Optional.of(new DataRecord(fields));
  }

  @Override
  public boolean hasMore() {
    return !keyBuffer.isEmpty();
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

  private void scanNext() {
    var args = ScanArgs.Builder.matches(pattern).limit(SCAN_COUNT);
    var result = sync.scan(cursor, args);
    keyBuffer.addAll(result.getKeys());
    cursor = result;
    if (result.isFinished()) {
      exhausted = true;
    }
  }
}
