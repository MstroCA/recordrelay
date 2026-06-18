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
package io.recordrelay.connector.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClients;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.DataSourceConnector;
import io.recordrelay.core.port.out.RecordReader;
import io.recordrelay.core.port.out.RecordWriter;
import io.recordrelay.core.port.out.SchemaInspector;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DataSourceConnector} adapter for MongoDB.
 *
 * <p>Uses the synchronous MongoDB Java Driver. Connection pooling is managed by {@code
 * MongoClient}, which is created per-operation in Phase 1. A shared client pool keyed by profile
 * will be introduced when the transfer engine is implemented.
 */
public final class MongoDbConnector implements DataSourceConnector {

  private static final Logger LOG = LoggerFactory.getLogger(MongoDbConnector.class);
  private static final int CONNECT_TIMEOUT_MS = 10_000;

  @Override
  public String connectorId() {
    return "mongodb";
  }

  @Override
  public boolean supports(ConnectionProfile profile) {
    return profile.type() == DatabaseType.MONGODB;
  }

  @Override
  public void testConnection(ConnectionProfile profile) throws ConnectorException {
    try (var client = buildClient(profile)) {
      client.listDatabaseNames().first();
      LOG.debug("Connection test succeeded for profile '{}'", profile.name());
    } catch (MongoException e) {
      throw new ConnectorException(
          "Connection test failed for '" + profile.name() + "': " + e.getMessage(), e);
    }
  }

  @Override
  public List<DatabaseRef> listDatabases(ConnectionProfile profile) throws ConnectorException {
    try (var client = buildClient(profile)) {
      var result = new ArrayList<DatabaseRef>();
      for (var name : client.listDatabaseNames()) {
        result.add(new DatabaseRef(name, DatabaseType.MONGODB));
      }
      LOG.debug("Listed {} database(s) for profile '{}'", result.size(), profile.name());
      return List.copyOf(result);
    } catch (MongoException e) {
      throw new ConnectorException("Failed to list databases: " + e.getMessage(), e);
    }
  }

  @Override
  public SchemaInspector schemaInspector() {
    return new MongoDbSchemaInspector();
  }

  @Override
  public RecordReader createReader() {
    return new MongoDbRecordReader();
  }

  @Override
  public RecordWriter createWriter() {
    return new MongoDbRecordWriter();
  }

  private com.mongodb.client.MongoClient buildClient(ConnectionProfile profile) {
    var credentials = profile.credentials();
    var builder =
        MongoClientSettings.builder()
            .applyToClusterSettings(
                b -> b.hosts(List.of(new ServerAddress(profile.host(), profile.port()))))
            .applyToSocketSettings(
                b -> b.connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    if (!credentials.username().isBlank()) {
      builder.credential(
          MongoCredential.createCredential(
              credentials.username(), profile.database(), credentials.password().toCharArray()));
    }
    return MongoClients.create(builder.build());
  }
}
