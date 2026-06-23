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
package io.recordrelay.engine.clone;

import static org.assertj.core.api.Assertions.assertThat;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DatabaseType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HeuristicRelationshipResolverTest {

  private final HeuristicRelationshipResolver resolver = new HeuristicRelationshipResolver();

  private static ConnectionProfile profile() {
    return new ConnectionProfile(
        "p1",
        "test",
        "env",
        DatabaseType.POSTGRESQL,
        "localhost",
        5432,
        "testdb",
        new Credentials("u", "p"),
        Map.of());
  }

  @Test
  void resolveReturnsGraphWithRootNode() throws Exception {
    var graph = resolver.resolve(profile(), "orders");
    assertThat(graph.getNode("orders")).isPresent();
  }

  @Test
  void resolveReturnsRootOnlyGraphWhenDatabaseUnreachable() throws Exception {
    // No real database is available in unit test — resolver should degrade gracefully
    var graph = resolver.resolve(profile(), "orders");
    assertThat(graph.getNode("orders")).isPresent();
    assertThat(graph.edgeCount()).isZero();
  }

  @Test
  void resolveDoesNotAddSelfReferentialEdge() throws Exception {
    var graph = resolver.resolve(profile(), "customers");
    var selfEdges =
        graph.edges().stream()
            .filter(
                e ->
                    e.fromNode().tableName().equals("customers")
                        && e.toNode().tableName().equals("customers"))
            .toList();
    assertThat(selfEdges).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "customer_id, customers",
    "order_id, orders",
    "account_id, accounts",
    "user_id, users",
    "product_id, products",
    "invoice_id, invoices"
  })
  void deriveTableNamePluralisesCorrectly(String column, String expectedTable) {
    assertThat(JdbcRelationshipResolver.deriveTableName(column)).isEqualTo(expectedTable);
  }

  @Test
  void confidenceForIdColumnReturnsHighConfidence() {
    assertThat(resolver.confidenceFor("customer_id")).isGreaterThanOrEqualTo(0.8);
  }

  @Test
  void confidenceForAnyIdSuffixReturnsHighConfidence() {
    // All *_id columns get the same schema-driven confidence — no hardcoded patterns
    assertThat(resolver.confidenceFor("widget_id")).isGreaterThanOrEqualTo(0.8);
  }

  @Test
  void confidenceForNonIdColumnReturnsZero() {
    assertThat(resolver.confidenceFor("name")).isZero();
  }
}
