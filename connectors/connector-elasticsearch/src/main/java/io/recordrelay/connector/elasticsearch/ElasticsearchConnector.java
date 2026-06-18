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
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.DataSourceConnector;
import io.recordrelay.core.port.out.RecordReader;
import io.recordrelay.core.port.out.RecordWriter;
import io.recordrelay.core.port.out.SchemaInspector;
import java.io.IOException;
import java.util.List;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DataSourceConnector} adapter for Elasticsearch.
 *
 * <p>Registered via {@code META-INF/services} for ServiceLoader discovery.
 */
public final class ElasticsearchConnector implements DataSourceConnector {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchConnector.class);

  @Override
  public String connectorId() {
    return "elasticsearch";
  }

  @Override
  public boolean supports(ConnectionProfile profile) {
    return profile.type() == DatabaseType.ELASTICSEARCH;
  }

  @Override
  public void testConnection(ConnectionProfile profile) throws ConnectorException {
    try (var transport = buildTransport(profile)) {
      var client = new ElasticsearchClient(transport);
      var health = client.cluster().health();
      LOG.debug(
          "Connection test succeeded for '{}', cluster status: {}",
          profile.name(),
          health.status());
    } catch (IOException e) {
      throw new ConnectorException(
          "Connection test failed for '" + profile.name() + "': " + e.getMessage(), e);
    }
  }

  @Override
  public List<DatabaseRef> listDatabases(ConnectionProfile profile) throws ConnectorException {
    // Elasticsearch indices play the role of "databases/tables".
    // Return a single entry representing the cluster.
    return List.of(
        new DatabaseRef(profile.host() + ":" + profile.port(), DatabaseType.ELASTICSEARCH));
  }

  @Override
  public SchemaInspector schemaInspector() {
    return new ElasticsearchSchemaInspector();
  }

  @Override
  public RecordReader createReader() {
    return new ElasticsearchRecordReader();
  }

  @Override
  public RecordWriter createWriter() {
    return new ElasticsearchRecordWriter();
  }

  /** Builds a {@link RestClientTransport} for the given profile. Caller must close. */
  static RestClientTransport buildTransport(ConnectionProfile profile) {
    var credentialsProvider = new BasicCredentialsProvider();
    if (!profile.credentials().username().isBlank()) {
      credentialsProvider.setCredentials(
          AuthScope.ANY,
          new UsernamePasswordCredentials(
              profile.credentials().username(), profile.credentials().password()));
    }
    var restClient =
        RestClient.builder(new HttpHost(profile.host(), profile.port(), "http"))
            .setHttpClientConfigCallback(
                cb -> cb.setDefaultCredentialsProvider(credentialsProvider))
            .build();
    return new RestClientTransport(restClient, new JacksonJsonpMapper());
  }
}
