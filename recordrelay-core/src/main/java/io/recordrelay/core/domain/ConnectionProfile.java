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
package io.recordrelay.core.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Connectivity parameters and credentials for a single database instance.
 *
 * <p>A profile belongs to exactly one {@link Environment}, identified by {@code environmentId}.
 * Extra JDBC/driver-specific parameters (e.g., {@code ssl=true}) are passed via {@code properties}.
 */
public record ConnectionProfile(
    String id,
    String name,
    String environmentId,
    DatabaseType type,
    String host,
    int port,
    String database,
    Credentials credentials,
    Map<String, String> properties) {

  /** Validates required fields and produces an immutable copy of {@code properties}. */
  public ConnectionProfile {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(environmentId, "environmentId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(host, "host");
    Objects.requireNonNull(credentials, "credentials");
    if (port < 1 || port > 65_535) {
      throw new IllegalArgumentException("port out of range: " + port);
    }
    properties = properties == null ? Map.of() : Map.copyOf(properties);
  }

  /**
   * Builds a standard JDBC URL for SQL connectors.
   *
   * <p>When a {@code currentSchema} property is set and the type is PostgreSQL, it is appended so
   * that unqualified table names resolve in that schema (equivalent to setting {@code
   * search_path}).
   *
   * @param scheme the JDBC sub-protocol (e.g., "postgresql", "mysql")
   * @return a JDBC URL in the form {@code jdbc:<scheme>://<host>:<port>/<database>}
   */
  public String jdbcUrl(String scheme) {
    var url = String.format("jdbc:%s://%s:%d/%s", scheme, host, port, database);
    var schema = schema();
    if (type == DatabaseType.POSTGRESQL && !schema.isBlank()) {
      url += "?currentSchema=" + schema;
    }
    return url;
  }

  /** Returns the configured schema ({@code currentSchema} property), or an empty string. */
  public String schema() {
    var schema = properties.get("currentSchema");
    return schema == null ? "" : schema.trim();
  }
}
