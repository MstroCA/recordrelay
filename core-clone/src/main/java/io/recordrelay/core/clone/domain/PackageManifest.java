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
 * <p>.rrpkg structure (v2.0):
 *
 * <pre>
 * metadata.json         — this manifest (includes optional bugReport)
 * relationships.json    — edge list of the relationship graph
 * schema.json           — inferred column schema per table
 * records/
 *   customers.jsonl
 *   orders.jsonl
 *   invoices.jsonl
 * </pre>
 *
 * <p>v1.0 packages (without {@code businessEntityName} or {@code bugReport}) are accepted by the
 * importer; the missing fields will be {@code null}.
 */
public record PackageManifest(
    String formatVersion,
    Instant createdAt,
    String sourceConnectorId,
    String rootTable,
    String rootId,
    List<String> tableNames,
    CloneReport report,
    String businessEntityName,
    BugReport bugReport,
    IdentityMapping identityMapping) {

  /** Current .rrpkg format version. */
  public static final String CURRENT_VERSION = "2.1";

  /** Oldest format version that the importer will still accept. */
  public static final String MIN_SUPPORTED_VERSION = "1.0";

  public PackageManifest {
    Objects.requireNonNull(formatVersion, "formatVersion");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(sourceConnectorId, "sourceConnectorId");
    Objects.requireNonNull(rootTable, "rootTable");
    Objects.requireNonNull(rootId, "rootId");
    tableNames = tableNames == null ? List.of() : List.copyOf(tableNames);
    identityMapping = identityMapping == null ? IdentityMapping.empty() : identityMapping;
  }

  /**
   * Creates a v2.0 manifest without bug-report context (plain clone or export).
   *
   * @param sourceConnectorId connector type id, e.g. "postgresql"
   * @param rootTable root table name
   * @param rootId root record primary key value
   * @param tableNames all tables included in this package
   * @param businessEntityName optional entity name, e.g. "customer"; null for table-level clones
   */
  public static PackageManifest create(
      String sourceConnectorId,
      String rootTable,
      String rootId,
      List<String> tableNames,
      String businessEntityName) {
    return new PackageManifest(
        CURRENT_VERSION,
        Instant.now(),
        sourceConnectorId,
        rootTable,
        rootId,
        tableNames,
        null,
        businessEntityName,
        null,
        null);
  }

  /**
   * Creates a v2.0 manifest with bug-report context (bug reproduction package).
   *
   * @param sourceConnectorId connector type id
   * @param rootTable root table name
   * @param rootId root record primary key value
   * @param tableNames all tables included in this package
   * @param businessEntityName optional entity name
   * @param bugReport bug reproduction context; may be null
   */
  public static PackageManifest createWithBugReport(
      String sourceConnectorId,
      String rootTable,
      String rootId,
      List<String> tableNames,
      String businessEntityName,
      BugReport bugReport) {
    return new PackageManifest(
        CURRENT_VERSION,
        Instant.now(),
        sourceConnectorId,
        rootTable,
        rootId,
        tableNames,
        null,
        businessEntityName,
        bugReport,
        null);
  }

  /**
   * Creates a v2.1 manifest with identity mapping embedded (produced by a live clone or export).
   *
   * @param sourceConnectorId connector type id
   * @param rootTable root table name
   * @param rootId root record primary key value
   * @param tableNames all tables included in this package
   * @param businessEntityName optional entity name
   * @param bugReport bug reproduction context; may be null
   * @param identityMapping source→target ID mapping produced during clone; may be null
   */
  public static PackageManifest createWithIdentityMapping(
      String sourceConnectorId,
      String rootTable,
      String rootId,
      List<String> tableNames,
      String businessEntityName,
      BugReport bugReport,
      IdentityMapping identityMapping) {
    return new PackageManifest(
        CURRENT_VERSION,
        Instant.now(),
        sourceConnectorId,
        rootTable,
        rootId,
        tableNames,
        null,
        businessEntityName,
        bugReport,
        identityMapping);
  }

  /** Returns true when this manifest carries bug-reproduction context. */
  public boolean hasBugReport() {
    return bugReport != null;
  }

  /** Returns true when this manifest was created from a known business entity. */
  public boolean hasEntityContext() {
    return businessEntityName != null && !businessEntityName.isBlank();
  }

  /** Returns true when this manifest carries an identity mapping (v2.1+). */
  public boolean hasIdentityMapping() {
    return identityMapping != null && !identityMapping.isEmpty();
  }
}
