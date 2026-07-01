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
package io.recordrelay.connector.bigquery;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.TableId;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes records to a BigQuery table using the streaming insert API ({@code insertAll}).
 *
 * <p>Records are buffered up to {@value #BATCH_SIZE} and flushed in one {@code insertAll} call. The
 * streaming insert API delivers data with low latency but incurs higher per-row cost than load
 * jobs. It is suited for cloning scenarios where target tables already exist with matching schemas.
 */
public final class BigQueryRecordWriter implements RecordWriter {

  private static final Logger LOG = LoggerFactory.getLogger(BigQueryRecordWriter.class);
  private static final int BATCH_SIZE = 500;

  private BigQuery bq;
  private TableId tableId;
  private final List<DataRecord> buffer = new ArrayList<>(BATCH_SIZE);

  @Override
  public void open(ConnectionProfile profile, TableRef table) throws ConnectorException {
    this.bq = BigQueryClientFactory.create(profile);
    this.tableId = TableId.of(profile.host(), table.database().name(), table.tableName());
    LOG.debug("Opened BigQuery writer for table '{}'", tableId);
  }

  @Override
  public void open(ConnectionProfile profile, TableRef table, boolean skipExisting)
      throws ConnectorException {
    open(profile, table);
  }

  @Override
  public void write(DataRecord record) throws ConnectorException {
    buffer.add(record);
    if (buffer.size() >= BATCH_SIZE) {
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
    flush();
    bq = null;
  }

  @Override
  public Map<String, String> drainConflictRemaps() {
    return Map.of();
  }

  private void flushBuffer() throws ConnectorException {
    var reqBuilder = InsertAllRequest.newBuilder(tableId);
    for (var record : buffer) {
      var rowContent = new HashMap<String, Object>(record.fieldNames().size());
      for (var field : record.fieldNames()) {
        Object val = record.get(field);
        if (val != null) {
          rowContent.put(field, val);
        }
      }
      reqBuilder.addRow(UUID.randomUUID().toString(), rowContent);
    }
    int count = buffer.size();
    try {
      var resp = bq.insertAll(reqBuilder.build());
      buffer.clear();
      if (resp.hasErrors()) {
        var errors = resp.getInsertErrors();
        int errorCount = errors.values().stream().mapToInt(List::size).sum();
        throw new ConnectorException(
            errorCount
                + " streaming insert error(s) into '"
                + tableId
                + "': "
                + errors.values().stream()
                    .flatMap(List::stream)
                    .map(Object::toString)
                    .collect(java.util.stream.Collectors.joining("; ")));
      }
      LOG.debug("Flushed {} row(s) to '{}'", count, tableId);
    } catch (BigQueryException e) {
      throw new ConnectorException(
          "BigQuery insertAll failed for '" + tableId + "': " + e.getMessage(), e);
    }
  }
}
