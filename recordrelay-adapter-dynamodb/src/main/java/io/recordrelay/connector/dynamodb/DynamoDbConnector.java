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

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.ContextProviderPort;
import io.recordrelay.core.port.out.RecordReader;
import io.recordrelay.core.port.out.RecordWriter;
import io.recordrelay.core.port.out.SchemaInspector;
import java.util.List;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

/**
 * {@link ContextProviderPort} adapter for AWS DynamoDB.
 *
 * <p>Registered via {@code META-INF/services} for ServiceLoader discovery. Supports {@link
 * DatabaseType#DYNAMODB} profiles.
 *
 * <p>Connection profile conventions:
 *
 * <ul>
 *   <li>{@code host} — AWS region name (e.g. {@code us-east-1})
 *   <li>{@code credentials.username()} — AWS Access Key ID (blank = use default credential chain)
 *   <li>{@code credentials.password()} — AWS Secret Access Key (blank = use default credential
 *       chain)
 *   <li>{@code properties.get("endpoint")} — override endpoint URL (local DynamoDB testing)
 * </ul>
 */
public final class DynamoDbConnector implements ContextProviderPort {

  @Override
  public String connectorId() {
    return "dynamodb";
  }

  @Override
  public boolean supports(ConnectionProfile profile) {
    return profile.type() == DatabaseType.DYNAMODB;
  }

  @Override
  public void testConnection(ConnectionProfile profile) throws ConnectorException {
    try (var client = DynamoDbClientFactory.create(profile)) {
      // listTables with limit=1 is the cheapest connectivity probe
      client.listTables(r -> r.limit(1));
    } catch (DynamoDbException e) {
      throw new ConnectorException(
          "DynamoDB connection test failed for '" + profile.name() + "': " + e.getMessage(), e);
    }
  }

  @Override
  public List<DatabaseRef> listDatabases(ConnectionProfile profile) {
    // DynamoDB is region-scoped; treat the region as a single logical "database"
    return List.of(new DatabaseRef(profile.host(), DatabaseType.DYNAMODB));
  }

  @Override
  public SchemaInspector schemaInspector() {
    return new DynamoDbSchemaInspector();
  }

  @Override
  public RecordReader createReader() {
    return new DynamoDbRecordReader();
  }

  @Override
  public RecordWriter createWriter() {
    return new DynamoDbRecordWriter();
  }

  static DynamoDbClient buildClient(ConnectionProfile profile) {
    return DynamoDbClientFactory.create(profile);
  }
}
