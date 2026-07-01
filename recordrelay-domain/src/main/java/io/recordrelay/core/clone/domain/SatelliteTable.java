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

import io.recordrelay.core.domain.ConnectionProfile;
import java.util.Objects;

/**
 * A satellite (companion) table that lives in a <em>separate</em> database from the primary clone
 * and is linked to the cloned root entity by a logical foreign key.
 *
 * <p>Motivating example: an event-driven "user module" keeps a {@code read_model} projection in its
 * own database, normally populated by a Kafka event when a {@code beyanname} is created in the core
 * database. When RecordRelay clones the core {@code beyanname} into a test environment there is no
 * Kafka event, so the projection is missing and the record is invisible in the UI. Declaring {@code
 * read_model} as a satellite tells the engine to copy the matching projection rows from the
 * secondary source into the secondary target, remapping {@code linkColumn} to the newly allocated
 * root id and applying the same field overrides.
 *
 * <p>The mechanism is intentionally generic: any table in any database that references the primary
 * root by a single column can be declared a satellite. Nothing here is specific to one schema.
 *
 * @param source secondary database to read companion rows from (e.g. production user module)
 * @param target secondary database to write companion rows into (e.g. test user module)
 * @param table companion table name (e.g. {@code read_model})
 * @param linkColumn column in {@code table} that holds the primary root id (e.g. {@code
 *     beyanname_id})
 * @param pkColumn own primary-key column to regenerate to avoid collisions; {@code null} lets the
 *     engine auto-detect it ({@code id} → {@code <table>_id} → first column)
 */
public record SatelliteTable(
    ConnectionProfile source,
    ConnectionProfile target,
    String table,
    String linkColumn,
    String pkColumn) {

  public SatelliteTable {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(table, "table");
    Objects.requireNonNull(linkColumn, "linkColumn");
    if (table.isBlank()) {
      throw new IllegalArgumentException("table must not be blank");
    }
    if (linkColumn.isBlank()) {
      throw new IllegalArgumentException("linkColumn must not be blank");
    }
    if (pkColumn != null && pkColumn.isBlank()) {
      pkColumn = null;
    }
  }

  /** Creates a satellite with auto-detected primary-key column. */
  public static SatelliteTable of(
      ConnectionProfile source, ConnectionProfile target, String table, String linkColumn) {
    return new SatelliteTable(source, target, table, linkColumn, null);
  }
}
