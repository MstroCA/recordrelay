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
package io.recordrelay.connector.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.DataSourceConnector;
import io.recordrelay.core.port.out.RecordReader;
import io.recordrelay.core.port.out.RecordWriter;
import io.recordrelay.core.port.out.SchemaInspector;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DataSourceConnector} adapter for Apache Cassandra.
 *
 * <p>Registered via {@code META-INF/services} for ServiceLoader discovery.
 */
public final class CassandraConnector implements DataSourceConnector {

  private static final Logger LOG = LoggerFactory.getLogger(CassandraConnector.class);

  @Override
  public String connectorId() {
    return "cassandra";
  }

  @Override
  public boolean supports(ConnectionProfile profile) {
    return profile.type() == DatabaseType.CASSANDRA;
  }

  @Override
  public void testConnection(ConnectionProfile profile) throws ConnectorException {
    try (var session = openSession(profile)) {
      // If the session opened without exception, connection succeeded.
      LOG.debug("Connection test succeeded for '{}'", profile.name());
    } catch (Exception e) {
      throw new ConnectorException(
          "Connection test failed for '" + profile.name() + "': " + e.getMessage(), e);
    }
  }

  @Override
  public List<DatabaseRef> listDatabases(ConnectionProfile profile) throws ConnectorException {
    try (var session = openSession(profile)) {
      var result = new ArrayList<DatabaseRef>();
      var rs = session.execute("SELECT keyspace_name FROM system_schema.keyspaces");
      for (var row : rs) {
        result.add(new DatabaseRef(row.getString("keyspace_name"), DatabaseType.CASSANDRA));
      }
      return List.copyOf(result);
    } catch (Exception e) {
      throw new ConnectorException("Failed to list keyspaces: " + e.getMessage(), e);
    }
  }

  @Override
  public SchemaInspector schemaInspector() {
    return new CassandraSchemaInspector();
  }

  @Override
  public RecordReader createReader() {
    return new CassandraRecordReader();
  }

  @Override
  public RecordWriter createWriter() {
    return new CassandraRecordWriter();
  }

  /** Opens a CqlSession for the given profile. Caller is responsible for closing. */
  static CqlSession openSession(ConnectionProfile profile) {
    var builder =
        CqlSession.builder()
            .addContactPoint(new InetSocketAddress(profile.host(), profile.port()))
            .withLocalDatacenter("datacenter1");
    if (!profile.credentials().username().isBlank()) {
      builder.withAuthCredentials(
          profile.credentials().username(), profile.credentials().password());
    }
    if (!profile.database().isBlank()) {
      builder.withKeyspace(profile.database());
    }
    return builder.build();
  }
}
