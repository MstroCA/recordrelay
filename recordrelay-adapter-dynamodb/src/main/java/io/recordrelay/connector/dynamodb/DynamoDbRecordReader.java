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
import io.recordrelay.core.port.out.RecordReader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

/**
 * Scans all items from a DynamoDB table using paginated {@code Scan} calls.
 *
 * <p>Items are buffered page-by-page (up to {@value #PAGE_SIZE} items per call). The {@code
 * ExclusiveStartKey} token is tracked across pages to resume scanning.
 */
public final class DynamoDbRecordReader implements RecordReader {

  private static final Logger LOG = LoggerFactory.getLogger(DynamoDbRecordReader.class);
  private static final int PAGE_SIZE = 100;

  private DynamoDbClient client;
  private String tableName;
  private final Deque<Map<String, AttributeValue>> pageBuffer = new ArrayDeque<>(PAGE_SIZE);
  private Map<String, AttributeValue> lastEvaluatedKey;
  private boolean exhausted = false;

  @Override
  public void open(ConnectionProfile profile, TableRef table) throws ConnectorException {
    this.client = DynamoDbClientFactory.create(profile);
    this.tableName = table.tableName();
    fetchNextPage();
    LOG.debug(
        "Opened DynamoDB scanner on '{}', items in first page={}", tableName, pageBuffer.size());
  }

  @Override
  public Optional<DataRecord> readNext() throws ConnectorException {
    if (pageBuffer.isEmpty() && !exhausted) {
      fetchNextPage();
    }
    if (pageBuffer.isEmpty()) {
      return Optional.empty();
    }
    var item = pageBuffer.poll();
    return Optional.of(itemToRecord(item));
  }

  @Override
  public boolean hasMore() {
    return !pageBuffer.isEmpty() || !exhausted;
  }

  @Override
  public void close() throws ConnectorException {
    if (client != null) {
      client.close();
      client = null;
    }
  }

  private void fetchNextPage() throws ConnectorException {
    try {
      var reqBuilder = ScanRequest.builder().tableName(tableName).limit(PAGE_SIZE);
      if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
        reqBuilder.exclusiveStartKey(lastEvaluatedKey);
      }
      var resp = client.scan(reqBuilder.build());
      for (var item : resp.items()) {
        pageBuffer.add(item);
      }
      lastEvaluatedKey = resp.lastEvaluatedKey();
      if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
        exhausted = true;
      }
    } catch (DynamoDbException e) {
      throw new ConnectorException(
          "DynamoDB scan failed on '" + tableName + "': " + e.getMessage(), e);
    }
  }

  private static DataRecord itemToRecord(Map<String, AttributeValue> item) {
    var fields = new LinkedHashMap<String, Object>(item.size());
    item.forEach((k, v) -> fields.put(k, AttributeValues.toObject(v)));
    return new DataRecord(fields);
  }
}
