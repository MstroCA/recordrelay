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
package io.recordrelay.connector.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;

import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Writes documents to an Elasticsearch index using the Bulk API. */
public final class ElasticsearchRecordWriter implements RecordWriter {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchRecordWriter.class);
  private static final int DEFAULT_BATCH = 500;

  private ElasticsearchClient client;
  private co.elastic.clients.transport.rest_client.RestClientTransport transport;
  private String index;
  private final List<BulkOperation> buffer = new ArrayList<>(DEFAULT_BATCH);

  @Override
  public void open(ConnectionProfile profile, TableRef table)
      throws ConnectorException {
    this.index = table.tableName();
    try {
      transport = ElasticsearchConnector.buildTransport(profile);
      client = new ElasticsearchClient(transport);
      LOG.debug("Opened writer for index '{}'", index);
    } catch (Exception e) {
      throw new ConnectorException("Failed to open writer for index '" + index + "'", e);
    }
  }

  @Override
  public void write(DataRecord record) throws ConnectorException {
    var doc = new LinkedHashMap<String, Object>();
    for (String field : record.fieldNames()) {
      doc.put(field, record.get(field));
    }
    buffer.add(
        BulkOperation.of(b -> b.index(IndexOperation.of(i -> i.index(index).document(doc)))));
    if (buffer.size() >= DEFAULT_BATCH) {
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
    if (!buffer.isEmpty()) {
      flushBuffer();
    }
    try {
      if (transport != null) {
        transport.close();
      }
    } catch (Exception e) {
      throw new ConnectorException("Error closing writer", e);
    }
  }

  private void flushBuffer() throws ConnectorException {
    try {
      var req = BulkRequest.of(b -> b.index(index).operations(List.copyOf(buffer)));
      var response = client.bulk(req);
      if (response.errors()) {
        long errCount = response.items().stream().filter(i -> i.error() != null).count();
        throw new ConnectorException(errCount + " bulk index error(s) for index '" + index + "'");
      }
      LOG.debug("Flushed {} docs to '{}'", buffer.size(), index);
      buffer.clear();
    } catch (ConnectorException ce) {
      throw ce;
    } catch (Exception e) {
      throw new ConnectorException("Bulk write failed for '" + index + "'", e);
    }
  }
}
