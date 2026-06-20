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

/**
 * Result of a connector health check.
 *
 * @param status overall health status
 * @param latencyMs round-trip latency in milliseconds (-1 if unavailable)
 * @param detail human-readable detail or error message
 */
public record HealthStatus(Status status, long latencyMs, String detail) {

  /** Health status levels. */
  public enum Status {
    OK,
    DEGRADED,
    DOWN
  }

  /**
   * Convenience factory for a healthy result.
   *
   * @param latencyMs observed latency in milliseconds
   * @return OK health status
   */
  public static HealthStatus ok(long latencyMs) {
    return new HealthStatus(Status.OK, latencyMs, "Reachable");
  }

  /**
   * Convenience factory for a down result.
   *
   * @param detail error or diagnostic message
   * @return DOWN health status
   */
  public static HealthStatus down(String detail) {
    return new HealthStatus(Status.DOWN, -1L, detail);
  }
}
