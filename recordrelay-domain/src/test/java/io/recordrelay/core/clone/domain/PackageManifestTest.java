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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PackageManifestTest {

  @Test
  void createFactoryProducesV21Manifest() {
    var m =
        PackageManifest.create("postgresql", "customers", "123", List.of("customers"), "customer");
    assertThat(m.formatVersion()).isEqualTo(PackageManifest.CURRENT_VERSION);
    assertThat(m.sourceConnectorId()).isEqualTo("postgresql");
    assertThat(m.rootTable()).isEqualTo("customers");
    assertThat(m.rootId()).isEqualTo("123");
    assertThat(m.hasEntityContext()).isTrue();
    assertThat(m.hasBugReport()).isFalse();
    assertThat(m.hasIdentityMapping()).isFalse();
  }

  @Test
  void createWithBugReportEmbedsBugReport() {
    var bug = BugReport.of("JIRA-1", "Price bug", "orders-service", "prod", null);
    var m =
        PackageManifest.createWithBugReport(
            "mysql", "orders", "42", List.of("orders"), "order", bug);
    assertThat(m.hasBugReport()).isTrue();
    assertThat(m.bugReport().id()).isEqualTo("JIRA-1");
  }

  @Test
  void createWithIdentityMappingEmbedsMappingStats() {
    var builder = IdentityMapping.builder();
    builder.register("customers", "1", "42001");
    var mapping = builder.build();
    var m =
        PackageManifest.createWithIdentityMapping(
            "postgresql", "customers", "1", List.of("customers"), "customer", null, mapping);
    assertThat(m.hasIdentityMapping()).isTrue();
    assertThat(m.identityMapping().totalMappings()).isEqualTo(1);
  }

  @Test
  void nullTableNamesDefaultsToEmptyList() {
    var m = new PackageManifest("2.1", Instant.now(), "pg", "t", "1", null, null, null, null, null);
    assertThat(m.tableNames()).isEmpty();
  }

  @Test
  void nullIdentityMappingDefaultsToEmpty() {
    var m = PackageManifest.create("pg", "t", "1", List.of("t"), null);
    assertThat(m.identityMapping()).isNotNull();
    assertThat(m.identityMapping().isEmpty()).isTrue();
  }

  @Test
  void hasEntityContextRequiresNonBlankName() {
    var m = PackageManifest.create("pg", "t", "1", List.of(), null);
    assertThat(m.hasEntityContext()).isFalse();
    var m2 = PackageManifest.create("pg", "t", "1", List.of(), "customer");
    assertThat(m2.hasEntityContext()).isTrue();
  }

  @Test
  void businessEntityOfShortFormCreatesCorrectEntity() {
    var entity = BusinessEntity.of("customer", "customers");
    assertThat(entity.name()).isEqualTo("customer");
    assertThat(entity.tableName()).isEqualTo("customers");
    assertThat(entity.idColumn()).isEqualTo("id");
    assertThat(entity.displayName()).isEqualTo("Customer");
  }

  @Test
  void businessEntityOfFullFormCreatesCorrectEntity() {
    var entity = BusinessEntity.of("invoice", "invoices", "invoice_id", "Billing invoice");
    assertThat(entity.idColumn()).isEqualTo("invoice_id");
    assertThat(entity.description()).isEqualTo("Billing invoice");
  }

  @Test
  void cloneProgressListenerDefaultsAreNoOps() {
    var listener = new io.recordrelay.core.clone.port.out.CloneProgressListener() {};
    listener.onRootRecordLoaded("t", "1");
    listener.onRelationshipsDiscovered(5);
    listener.onIdentitiesAllocated(10);
    listener.onTableExtractionStarted("t");
    listener.onTableExtractionCompleted("t", 3);
    listener.onImportStarted("t");
    listener.onImportCompleted("t", 3);
    listener.onWarning("msg");
  }

  @Test
  void bugReportOfMinimalFormHasDefaultCapturedAt() {
    var bug = BugReport.of("ID-1", "title");
    assertThat(bug.capturedAt()).isNotNull();
    assertThat(bug.service()).isNull();
    assertThat(bug.stepsToReproduce()).isNull();
  }
}
