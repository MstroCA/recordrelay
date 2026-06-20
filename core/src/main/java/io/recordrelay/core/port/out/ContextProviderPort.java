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
package io.recordrelay.core.port.out;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.HealthStatus;
import io.recordrelay.core.exception.ConnectorException;
import java.util.List;

/**
 * Driven port: abstracts connectivity and context-data access for a single storage engine.
 *
 * <p>Implementations are discovered at runtime via {@link java.util.ServiceLoader} and registered
 * through {@code META-INF/services/io.recordrelay.core.port.out.ContextProviderPort}. One JAR on
 * the classpath can contribute multiple providers by listing multiple implementations.
 *
 * <p>Each provider handles exactly the {@link io.recordrelay.core.domain.DatabaseType}(s) for which
 * {@link #supports(ConnectionProfile)} returns {@code true}.
 */
public interface ContextProviderPort {

  /**
   * Returns a stable, lowercase identifier for this provider (e.g., {@code "postgresql"}, {@code
   * "mongodb"}).
   *
   * @return unique provider id
   */
  String connectorId();

  /**
   * Returns true when this provider can handle the database type of the given profile.
   *
   * @param profile the connection profile to evaluate
   * @return true if this provider supports the profile's type
   */
  boolean supports(ConnectionProfile profile);

  /**
   * Opens and immediately closes a connection to verify that the supplied credentials and network
   * address are reachable.
   *
   * @param profile connection parameters to test
   * @throws ConnectorException if the connection cannot be established
   */
  void testConnection(ConnectionProfile profile) throws ConnectorException;

  /**
   * Lists all databases (schemas, keyspaces, etc.) visible to the credentials in {@code profile}.
   *
   * @param profile connection parameters with sufficient list-databases privilege
   * @return an unmodifiable list of database references
   * @throws ConnectorException if the query fails
   */
  List<DatabaseRef> listDatabases(ConnectionProfile profile) throws ConnectorException;

  /**
   * Returns the {@link SchemaInspector} implementation for this provider.
   *
   * @return a schema inspector bound to this provider's storage engine
   */
  default SchemaInspector schemaInspector() {
    throw new UnsupportedOperationException(
        connectorId() + " does not implement schemaInspector()");
  }

  /**
   * Creates a new {@link RecordReader} for streaming records out of a context table.
   *
   * <p>The caller is responsible for calling {@link RecordReader#open} and {@link
   * RecordReader#close}.
   *
   * @return a new, unopened record reader
   */
  default RecordReader createReader() {
    throw new UnsupportedOperationException(connectorId() + " does not implement createReader()");
  }

  /**
   * Creates a new {@link RecordWriter} for writing records into a context table.
   *
   * <p>The caller is responsible for calling {@link RecordWriter#open} and {@link
   * RecordWriter#close}.
   *
   * @return a new, unopened record writer
   */
  default RecordWriter createWriter() {
    throw new UnsupportedOperationException(connectorId() + " does not implement createWriter()");
  }

  /**
   * Checks whether this provider can reach the storage and returns timing information.
   *
   * <p>The default implementation delegates to {@link #testConnection(ConnectionProfile)} and wraps
   * the result in a {@link HealthStatus}. Override for a more lightweight ping.
   *
   * @param profile connection parameters to probe
   * @return health status with latency and optional detail
   */
  default HealthStatus healthCheck(ConnectionProfile profile) {
    long start = System.currentTimeMillis();
    try {
      testConnection(profile);
      return HealthStatus.ok(System.currentTimeMillis() - start);
    } catch (Exception e) {
      return HealthStatus.down(e.getMessage());
    }
  }
}
