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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that tracks the mapping from source primary-key values to new target primary-key values
 * for every table involved in a clone operation.
 *
 * <p>This is the CRITICAL component that implements the no-direct-PK-copy contract. Every record
 * written to the target receives a fresh identity; all foreign-key references are remapped through
 * this registry before writing so that referential integrity is preserved across the entire cloned
 * context.
 *
 * <p>Example:
 *
 * <pre>
 * SOURCE:  customers.id = 1,  orders.customer_id = 1
 * MAPPING: customers: {1 → 42001},  orders: {7 → 78001}
 * TARGET:  customers.id = 42001, orders.customer_id = 42001
 * </pre>
 *
 * <p>Use {@link Builder} to build up the mapping during identity allocation, then call {@link
 * Builder#build()} to obtain an immutable snapshot. The snapshot can be serialized to {@code
 * identity-mapping.json} inside a {@code .rrpkg} archive.
 */
public final class IdentityMapping {

  private final Map<String, Map<String, String>> tableMap;

  private IdentityMapping(Map<String, Map<String, String>> tableMap) {
    var copy = new LinkedHashMap<String, Map<String, String>>();
    tableMap.forEach((table, entries) -> copy.put(table, Map.copyOf(entries)));
    this.tableMap = Collections.unmodifiableMap(copy);
  }

  /** Creates an empty mapping (e.g. when identity mapping is not applicable). */
  public static IdentityMapping empty() {
    return new IdentityMapping(Map.of());
  }

  /**
   * Looks up the target ID that was assigned to a source record.
   *
   * @param tableName table that the source ID belongs to
   * @param sourceId the source primary-key value (as a string)
   * @return the assigned target ID, or empty if not registered
   */
  public Optional<String> resolve(String tableName, String sourceId) {
    var entries = tableMap.get(tableName);
    if (entries == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(entries.get(sourceId));
  }

  /** Returns true when no mappings have been registered. */
  public boolean isEmpty() {
    return tableMap.isEmpty();
  }

  /** Returns the total number of source → target ID pairs across all tables. */
  public int totalMappings() {
    return tableMap.values().stream().mapToInt(Map::size).sum();
  }

  /**
   * Returns an immutable view of the full mapping table.
   *
   * <p>Structure: {@code tableName → {sourceId → targetId}}. Suitable for JSON serialization into
   * {@code identity-mapping.json}.
   */
  public Map<String, Map<String, String>> snapshot() {
    return tableMap;
  }

  /** Returns a new {@link Builder}. */
  public static Builder builder() {
    return new Builder();
  }

  /** Mutable accumulator used during the identity-allocation phase. */
  public static final class Builder {

    private final ConcurrentHashMap<String, Map<String, String>> tableMap =
        new ConcurrentHashMap<>();

    /**
     * Registers a source → target ID mapping for the given table.
     *
     * @param tableName table that the IDs belong to
     * @param sourceId original primary-key value from the source
     * @param targetId new primary-key value to be used in the target
     */
    public void register(String tableName, String sourceId, String targetId) {
      tableMap.computeIfAbsent(tableName, k -> new ConcurrentHashMap<>()).put(sourceId, targetId);
    }

    /** Returns true when no mappings have been registered yet. */
    public boolean isEmpty() {
      return tableMap.isEmpty();
    }

    /** Builds and returns an immutable {@link IdentityMapping}. */
    public IdentityMapping build() {
      return new IdentityMapping(tableMap);
    }
  }
}
