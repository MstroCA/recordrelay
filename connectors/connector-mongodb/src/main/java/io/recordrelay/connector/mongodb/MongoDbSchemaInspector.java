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
import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.SchemaInspector;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bson.Document;

/**
 * {@link SchemaInspector} adapter for MongoDB.
 *
 * <p>MongoDB does not enforce a fixed schema per collection. Column metadata is inferred by
 * sampling up to {@value #SAMPLE_SIZE} documents and merging their field sets. The resulting {@link
 * ColumnMeta} list reflects the union of all fields observed in the sample.
 *
 * <p>{@code ordinalPosition} values are assigned based on first-seen order across sampled documents
 * and may differ between runs if document shapes vary.
 */
public final class MongoDbSchemaInspector implements SchemaInspector {

  private static final int SAMPLE_SIZE = 100;
  private static final int CONNECT_TIMEOUT_MS = 10_000;

  @Override
  public List<TableRef> listTables(ConnectionProfile profile, DatabaseRef database)
      throws ConnectorException {
    try (var client = buildClient(profile)) {
      var db = client.getDatabase(database.name());
      var result = new ArrayList<TableRef>();
      for (var name : db.listCollectionNames()) {
        result.add(new TableRef(database, "", name));
      }
      return List.copyOf(result);
    } catch (MongoException e) {
      throw new ConnectorException(
          "Failed to list collections in '" + database.name() + "': " + e.getMessage(), e);
    }
  }

  @Override
  public List<ColumnMeta> inspectColumns(ConnectionProfile profile, TableRef table)
      throws ConnectorException {
    try (var client = buildClient(profile)) {
      var collection = client.getDatabase(table.database().name()).getCollection(table.tableName());

      var fieldOrder = new LinkedHashMap<String, String>();
      var cursor = collection.find().limit(SAMPLE_SIZE).iterator();
      while (cursor.hasNext()) {
        mergeDocumentFields(cursor.next(), fieldOrder);
      }

      var result = new ArrayList<ColumnMeta>();
      int ordinal = 1;
      for (var entry : fieldOrder.entrySet()) {
        boolean isPk = "_id".equals(entry.getKey());
        result.add(
            new ColumnMeta(entry.getKey(), entry.getValue(), !isPk, isPk, false, ordinal++, null));
      }
      return List.copyOf(result);
    } catch (MongoException e) {
      throw new ConnectorException(
          "Failed to inspect collection '" + table.tableName() + "': " + e.getMessage(), e);
    }
  }

  private com.mongodb.client.MongoClient buildClient(ConnectionProfile profile) {
    var creds = profile.credentials();
    var settings =
        MongoClientSettings.builder()
            .applyToClusterSettings(
                b -> b.hosts(List.of(new ServerAddress(profile.host(), profile.port()))))
            .applyToSocketSettings(b -> b.connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
            .credential(
                MongoCredential.createCredential(
                    creds.username(), profile.database(), creds.password().toCharArray()))
            .build();
    return MongoClients.create(settings);
  }

  private void mergeDocumentFields(Document doc, LinkedHashMap<String, String> fieldOrder) {
    for (var entry : doc.entrySet()) {
      fieldOrder.computeIfAbsent(entry.getKey(), k -> inferType(entry.getValue()));
    }
  }

  private String inferType(Object value) {
    if (value == null) {
      return "null";
    }
    return value.getClass().getSimpleName().toLowerCase();
  }
}
