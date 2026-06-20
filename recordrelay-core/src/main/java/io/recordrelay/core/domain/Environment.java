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
 * An isolated deployment context (e.g., dev, staging, production).
 *
 * <p>Environments group {@link ConnectionProfile}s and guard against accidental cross-environment
 * data operations.
 */
public record Environment(String id, String name, String description) {

  /** Validates and normalises the environment. {@code description} may be null. */
  public Environment {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    if (id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    description = description == null ? "" : description;
  }
}
