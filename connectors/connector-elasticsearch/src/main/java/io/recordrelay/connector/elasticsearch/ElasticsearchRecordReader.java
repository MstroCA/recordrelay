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
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordReader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads documents from an Elasticsearch index using the scroll/search_after API.
 *
 * <p>Uses {@code Map<String,Object>} as the document type for schema-agnostic reading.
 */
public final class ElasticsearchRecordReader implements RecordReader {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchRecordReader.class);
  private static final int PAGE_SIZE = 500;

  private ElasticsearchClient client;
  private co.elastic.clients.transport.rest_client.RestClientTransport transport;
  private String index;
  private String scrollId;
  private final Deque<Map<String, Object>> buffer = new ArrayDeque<>(PAGE_SIZE);
  private boolean exhausted = false;

  @Override
  @SuppressWarnings("unchecked")
  public void open(ConnectionProfile profile, TableRef table, MappingDefinition mapping)
      throws ConnectorException {
    this.index = table.tableName();
    try {
      transport = ElasticsearchConnector.buildTransport(profile);
      client = new ElasticsearchClient(transport);
      SearchResponse<Map> response =
          client.search(s -> s.index(index).size(PAGE_SIZE).scroll(t -> t.time("2m")), Map.class);
      scrollId = response.scrollId();
      loadHits(response.hits().hits());
      LOG.debug("Opened reader for index '{}', hits={}", index, buffer.size());
    } catch (Exception e) {
      throw new ConnectorException("Failed to open reader for index '" + index + "'", e);
    }
  }

  @Override
  public Optional<DataRecord> readNext() throws ConnectorException {
    if (!hasMore()) {
      return Optional.empty();
    }
    var doc = buffer.poll();
    if (buffer.isEmpty() && !exhausted) {
      fetchNextPage();
    }
    return Optional.of(new DataRecord(new LinkedHashMap<>(doc)));
  }

  @Override
  public boolean hasMore() {
    return !buffer.isEmpty();
  }

  @Override
  @SuppressWarnings("unchecked")
  public void close() throws ConnectorException {
    try {
      if (scrollId != null) {
        client.clearScroll(c -> c.scrollId(List.of(scrollId)));
      }
      if (transport != null) {
        transport.close();
      }
    } catch (Exception e) {
      throw new ConnectorException("Error closing reader", e);
    }
  }

  @SuppressWarnings("unchecked")
  private void fetchNextPage() throws ConnectorException {
    try {
      var response = client.scroll(s -> s.scrollId(scrollId).scroll(t -> t.time("2m")), Map.class);
      scrollId = response.scrollId();
      var hits = response.hits().hits();
      if (hits.isEmpty()) {
        exhausted = true;
      } else {
        loadHits(hits);
      }
    } catch (Exception e) {
      throw new ConnectorException("Error fetching next page from '" + index + "'", e);
    }
  }

  @SuppressWarnings("unchecked")
  private void loadHits(List<Hit<Map>> hits) {
    for (var hit : hits) {
      if (hit.source() != null) {
        buffer.add(hit.source());
      }
    }
  }
}
