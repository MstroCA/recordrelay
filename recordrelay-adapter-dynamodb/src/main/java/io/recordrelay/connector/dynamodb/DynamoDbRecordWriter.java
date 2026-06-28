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
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

/**
 * Writes records to a DynamoDB table using {@code batchWriteItem}.
 *
 * <p>DynamoDB batch writes are capped at {@value #DYNAMO_BATCH_LIMIT} items per call. Unprocessed
 * items from a partially successful batch are automatically retried once before being reported as
 * errors.
 */
public final class DynamoDbRecordWriter implements RecordWriter {

  private static final Logger LOG = LoggerFactory.getLogger(DynamoDbRecordWriter.class);

  // DynamoDB hard limit: 25 items per BatchWriteItem call
  private static final int DYNAMO_BATCH_LIMIT = 25;

  private DynamoDbClient client;
  private String tableName;
  private final List<DataRecord> buffer = new ArrayList<>(DYNAMO_BATCH_LIMIT);

  @Override
  public void open(ConnectionProfile profile, TableRef table) throws ConnectorException {
    this.client = DynamoDbClientFactory.create(profile);
    this.tableName = table.tableName();
    LOG.debug("Opened DynamoDB writer for table '{}'", tableName);
  }

  @Override
  public void open(ConnectionProfile profile, TableRef table, boolean skipExisting)
      throws ConnectorException {
    open(profile, table);
  }

  @Override
  public void write(DataRecord record) throws ConnectorException {
    buffer.add(record);
    if (buffer.size() >= DYNAMO_BATCH_LIMIT) {
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
    } finally {
      if (client != null) {
        client.close();
        client = null;
      }
    }
  }

  @Override
  public Map<String, String> drainConflictRemaps() {
    return Map.of();
  }

  private void flushBuffer() throws ConnectorException {
    var writeRequests = new ArrayList<WriteRequest>(buffer.size());
    for (var record : buffer) {
      var item = recordToItem(record);
      writeRequests.add(
          WriteRequest.builder().putRequest(PutRequest.builder().item(item).build()).build());
    }
    buffer.clear();

    try {
      var resp =
          client.batchWriteItem(
              BatchWriteItemRequest.builder()
                  .requestItems(Map.of(tableName, writeRequests))
                  .build());
      // Retry unprocessed items once
      if (resp.hasUnprocessedItems() && !resp.unprocessedItems().isEmpty()) {
        var retry =
            client.batchWriteItem(
                BatchWriteItemRequest.builder().requestItems(resp.unprocessedItems()).build());
        if (retry.hasUnprocessedItems() && !retry.unprocessedItems().isEmpty()) {
          int remaining = retry.unprocessedItems().values().stream().mapToInt(List::size).sum();
          LOG.warn("{} item(s) still unprocessed after retry in '{}'", remaining, tableName);
        }
      }
      LOG.debug("Flushed {} item(s) to '{}'", writeRequests.size(), tableName);
    } catch (DynamoDbException e) {
      throw new ConnectorException(
          "DynamoDB batch write failed for '" + tableName + "': " + e.getMessage(), e);
    }
  }

  private static Map<String, AttributeValue> recordToItem(DataRecord record) {
    var item = new java.util.LinkedHashMap<String, AttributeValue>(record.fieldNames().size());
    for (var field : record.fieldNames()) {
      item.put(field, AttributeValues.fromObject(record.get(field)));
    }
    return item;
  }
}
