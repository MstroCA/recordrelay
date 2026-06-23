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

import java.util.Objects;

/**
 * Health-check result for a single saved connection.
 *
 * @param connName user-assigned name of the connection
 * @param host host and port string for display (e.g. {@code "localhost:5432"})
 * @param dbType database engine type
 * @param status overall health classification
 * @param latencyMs round-trip latency in milliseconds; {@code -1} when unavailable
 * @param detail version string on success, or error message on failure
 */
public record ConnectionHealthEntry(
    String connName,
    String host,
    DatabaseType dbType,
    HealthStatus.Status status,
    long latencyMs,
    String detail) {

  public ConnectionHealthEntry {
    Objects.requireNonNull(connName, "connName");
    Objects.requireNonNull(status, "status");
  }

  /** Returns a display-friendly latency string, e.g. {@code "42 ms"} or {@code "—"}. */
  public String latencyDisplay() {
    return latencyMs < 0 ? "—" : latencyMs + " ms";
  }
}
