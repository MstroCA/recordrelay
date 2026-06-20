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
import com.mongodb.client.MongoCursor;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Streams documents from a MongoDB collection as {@link DataRecord} instances. */
public final class MongoDbRecordReader implements RecordReader {

  private static final Logger LOG = LoggerFactory.getLogger(MongoDbRecordReader.class);
  private static final int CONNECT_TIMEOUT_MS = 10_000;

  private MongoClient client;
  private MongoCursor<Document> cursor;
  private boolean hasNext;

  @Override
  public void open(ConnectionProfile profile, TableRef table) throws ConnectorException {
    try {
      client = buildClient(profile);
      var collection = client.getDatabase(profile.database()).getCollection(table.tableName());
      cursor = collection.find().cursor();
      hasNext = cursor.hasNext();
      LOG.debug("Opened cursor on collection '{}', hasDocuments={}", table.tableName(), hasNext);
    } catch (MongoException e) {
      throw new ConnectorException(
          "Failed to open reader on collection '" + table.tableName() + "'", e);
    }
  }

  @Override
  public Optional<DataRecord> readNext() throws ConnectorException {
    if (!hasNext) {
      return Optional.empty();
    }
    try {
      var doc = cursor.next();
      hasNext = cursor.hasNext();
      return Optional.of(docToRecord(doc));
    } catch (MongoException e) {
      throw new ConnectorException("Error reading next document", e);
    }
  }

  @Override
  public boolean hasMore() {
    return hasNext;
  }

  @Override
  public void close() throws ConnectorException {
    try {
      if (cursor != null) {
        cursor.close();
      }
      if (client != null) {
        client.close();
      }
    } catch (MongoException e) {
      throw new ConnectorException("Error closing MongoDB reader", e);
    }
  }

  private DataRecord docToRecord(Document doc) {
    var fields = new LinkedHashMap<String, Object>(doc.size());
    for (var entry : doc.entrySet()) {
      fields.put(entry.getKey(), entry.getValue());
    }
    return new DataRecord(fields);
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
