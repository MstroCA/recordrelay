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
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Buffers records and inserts them into a MongoDB collection using bulk insert.
 *
 * <p>MongoDB does not have ACID transactions in the same sense as RDBMS; {@link #flush()} performs
 * an ordered {@code insertMany} for the current buffer.
 */
public final class MongoDbRecordWriter implements RecordWriter {

  private static final Logger LOG = LoggerFactory.getLogger(MongoDbRecordWriter.class);
  private static final int CONNECT_TIMEOUT_MS = 10_000;
  private static final int DEFAULT_BATCH_SIZE = 1_000;

  private MongoClient client;
  private MongoCollection<Document> collection;
  private final List<Document> buffer = new ArrayList<>(DEFAULT_BATCH_SIZE);

  @Override
  public void open(ConnectionProfile profile, TableRef table, MappingDefinition mapping)
      throws ConnectorException {
    try {
      client = buildClient(profile);
      collection = client.getDatabase(profile.database()).getCollection(table.tableName());
      LOG.debug("Opened writer for collection '{}'", table.tableName());
    } catch (MongoException e) {
      throw new ConnectorException(
          "Failed to open writer for collection '" + table.tableName() + "'", e);
    }
  }

  @Override
  public void write(DataRecord record) throws ConnectorException {
    buffer.add(new Document(record.fields()));
    if (buffer.size() >= DEFAULT_BATCH_SIZE) {
      flushBuffer();
    }
  }

  @Override
  public void flush() throws ConnectorException {
    if (!buffer.isEmpty()) {
      flushBuffer();
    }
  }

  @Override
  public void close() throws ConnectorException {
    try {
      flush();
      if (client != null) {
        client.close();
      }
    } catch (ConnectorException e) {
      throw e;
    } catch (MongoException e) {
      throw new ConnectorException("Error closing MongoDB writer", e);
    }
  }

  private void flushBuffer() throws ConnectorException {
    try {
      collection.insertMany(new ArrayList<>(buffer));
      LOG.debug("Inserted {} documents", buffer.size());
      buffer.clear();
    } catch (MongoException e) {
      throw new ConnectorException("Bulk insert failed: " + e.getMessage(), e);
    }
  }

  private MongoClient buildClient(ConnectionProfile profile) {
    var creds = profile.credentials();
    var builder =
        MongoClientSettings.builder()
            .applyToClusterSettings(
                b -> b.hosts(List.of(new ServerAddress(profile.host(), profile.port()))))
            .applyToSocketSettings(
                b -> b.connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    if (!creds.username().isBlank()) {
      builder.credential(
          MongoCredential.createCredential(
              creds.username(), profile.database(), creds.password().toCharArray()));
    }
    return MongoClients.create(builder.build());
  }
}
