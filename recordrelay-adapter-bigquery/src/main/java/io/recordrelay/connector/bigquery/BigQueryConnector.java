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

import com.google.cloud.bigquery.BigQueryException;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.ContextProviderPort;
import io.recordrelay.core.port.out.RecordReader;
import io.recordrelay.core.port.out.RecordWriter;
import io.recordrelay.core.port.out.SchemaInspector;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ContextProviderPort} adapter for Google Cloud BigQuery.
 *
 * <p>Registered via {@code META-INF/services} for ServiceLoader discovery. Supports {@link
 * DatabaseType#BIGQUERY} profiles.
 *
 * <p>Connection profile conventions:
 *
 * <ul>
 *   <li>{@code host} — GCP project ID (e.g. {@code my-gcp-project})
 *   <li>{@code database} — BigQuery dataset ID
 *   <li>{@code credentials.password()} — path to service account JSON, inline JSON, or blank for
 *       Application Default Credentials
 *   <li>{@code properties.get("location")} — dataset location (e.g. {@code US}, {@code EU})
 * </ul>
 */
public final class BigQueryConnector implements ContextProviderPort {

  private static final Logger LOG = LoggerFactory.getLogger(BigQueryConnector.class);

  @Override
  public String connectorId() {
    return "bigquery";
  }

  @Override
  public boolean supports(ConnectionProfile profile) {
    return profile.type() == DatabaseType.BIGQUERY;
  }

  @Override
  public void testConnection(ConnectionProfile profile) throws ConnectorException {
    try {
      var bq = BigQueryClientFactory.create(profile);
      // listDatasets with projectId is a lightweight connectivity check
      bq.listDatasets(profile.host());
      LOG.debug("BigQuery connection test succeeded for project '{}'", profile.host());
    } catch (BigQueryException e) {
      throw new ConnectorException(
          "BigQuery connection test failed for '" + profile.name() + "': " + e.getMessage(), e);
    }
  }

  @Override
  public List<DatabaseRef> listDatabases(ConnectionProfile profile) throws ConnectorException {
    try {
      var bq = BigQueryClientFactory.create(profile);
      var result = new java.util.ArrayList<DatabaseRef>();
      bq.listDatasets(profile.host())
          .iterateAll()
          .forEach(
              ds ->
                  result.add(
                      new DatabaseRef(ds.getDatasetId().getDataset(), DatabaseType.BIGQUERY)));
      LOG.debug("Listed {} dataset(s) for project '{}'", result.size(), profile.host());
      return List.copyOf(result);
    } catch (BigQueryException e) {
      throw new ConnectorException("Failed to list BigQuery datasets: " + e.getMessage(), e);
    }
  }

  @Override
  public SchemaInspector schemaInspector() {
    return new BigQuerySchemaInspector();
  }

  @Override
  public RecordReader createReader() {
    return new BigQueryRecordReader();
  }

  @Override
  public RecordWriter createWriter() {
    return new BigQueryRecordWriter();
  }
}
