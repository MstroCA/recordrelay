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
package io.recordrelay.core.clone.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A runnable clone job combining a {@link CloneRequest} with a unique execution identity.
 *
 * <p>A job does not hold runtime state; execution state lives in {@link CloneReport}.
 */
public record CloneJob(String id, CloneRequest request, Instant createdAt) {

  /** Validates required fields. */
  public CloneJob {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(createdAt, "createdAt");
  }

  /**
   * Creates a new job from a request, generating a random ID and timestamping it now.
   *
   * @param request the clone request
   * @return a ready-to-execute clone job
   */
  public static CloneJob of(CloneRequest request) {
    return new CloneJob(UUID.randomUUID().toString(), request, Instant.now());
  }
}
