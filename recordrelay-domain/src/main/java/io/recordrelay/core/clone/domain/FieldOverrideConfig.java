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

import java.util.List;
import java.util.Objects;

/**
 * Collection of field overrides applied after FK remapping and before writing to the target.
 *
 * <p>Overrides let each company/team specify which fields carry tenant-context values that must be
 * replaced in the target environment. Common examples: {@code created_by} (user code),
 * {@code mukellef_vkn} (tax identity), {@code tenant_id}.
 */
public record FieldOverrideConfig(List<FieldOverride> overrides) {

  public FieldOverrideConfig {
    overrides = overrides == null ? List.of() : List.copyOf(overrides);
  }

  /** Returns a config with no overrides. */
  public static FieldOverrideConfig none() {
    return new FieldOverrideConfig(List.of());
  }

  /** Returns true when no overrides are configured. */
  public boolean isEmpty() {
    return overrides.isEmpty();
  }

  /** Returns all overrides that apply to the given table. */
  public List<FieldOverride> getOverridesFor(String tableName) {
    Objects.requireNonNull(tableName, "tableName");
    return overrides.stream().filter(o -> o.appliesTo(tableName)).toList();
  }
}
