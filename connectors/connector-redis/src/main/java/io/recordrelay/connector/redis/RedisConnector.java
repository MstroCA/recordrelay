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
import io.lettuce.core.RedisURI;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.DataSourceConnector;
import io.recordrelay.core.port.out.RecordReader;
import io.recordrelay.core.port.out.RecordWriter;
import io.recordrelay.core.port.out.SchemaInspector;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DataSourceConnector} adapter for Redis via Lettuce.
 *
 * <p>Registered via {@code META-INF/services} for ServiceLoader discovery.
 */
public final class RedisConnector implements DataSourceConnector {

  private static final Logger LOG = LoggerFactory.getLogger(RedisConnector.class);

  @Override
  public String connectorId() {
    return "redis";
  }

  @Override
  public boolean supports(ConnectionProfile profile) {
    return profile.type() == DatabaseType.REDIS;
  }

  @Override
  public void testConnection(ConnectionProfile profile) throws ConnectorException {
    try (var client = RedisClient.create(buildUri(profile));
        var conn = client.connect()) {
      var pong = conn.sync().ping();
      if (!"PONG".equalsIgnoreCase(pong)) {
        throw new ConnectorException("Unexpected PING response from: " + profile.name());
      }
      LOG.debug("Connection test succeeded for '{}'", profile.name());
    } catch (Exception e) {
      if (e instanceof ConnectorException ce) {
        throw ce;
      }
      throw new ConnectorException(
          "Connection test failed for '" + profile.name() + "': " + e.getMessage(), e);
    }
  }

  @Override
  public List<DatabaseRef> listDatabases(ConnectionProfile profile) throws ConnectorException {
    return List.of(new DatabaseRef("redis", DatabaseType.REDIS));
  }

  @Override
  public SchemaInspector schemaInspector() {
    // Redis is schemaless; return an inspector that reflects key patterns.
    return new RedisSchemaInspector();
  }

  @Override
  public RecordReader createReader() {
    return new RedisRecordReader();
  }

  @Override
  public RecordWriter createWriter() {
    return new RedisRecordWriter();
  }

  static RedisURI buildUri(ConnectionProfile profile) {
    var builder = RedisURI.builder().withHost(profile.host()).withPort(profile.port());
    if (!profile.credentials().password().isBlank()) {
      builder.withPassword((CharSequence) profile.credentials().password());
    }
    return builder.build();
  }
}
