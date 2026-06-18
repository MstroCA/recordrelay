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
import java.util.List;
import java.util.Objects;

/**
 * Metadata stored in {@code metadata.json} inside a {@code .rrpkg} archive.
 *
 * <p>.rrpkg structure:
 *
 * <pre>
 * metadata.json
 * relationships.json
 * records/
 *   customers.jsonl
 *   orders.jsonl
 *   invoices.jsonl
 * </pre>
 */
public record PackageManifest(
    String formatVersion,
    Instant createdAt,
    String sourceConnectorId,
    String rootTable,
    String rootId,
    List<String> tableNames,
    CloneReport report) {

  /** Current .rrpkg format version. */
  public static final String CURRENT_VERSION = "1.0";

  /** Validates required fields and copies the table name list. */
  public PackageManifest {
    Objects.requireNonNull(formatVersion, "formatVersion");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(sourceConnectorId, "sourceConnectorId");
    Objects.requireNonNull(rootTable, "rootTable");
    Objects.requireNonNull(rootId, "rootId");
    tableNames = tableNames == null ? List.of() : List.copyOf(tableNames);
  }
}
