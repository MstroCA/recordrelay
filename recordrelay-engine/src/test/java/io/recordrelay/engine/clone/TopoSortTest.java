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

import io.recordrelay.core.clone.domain.RelationshipEdge;
import io.recordrelay.core.clone.domain.RelationshipGraph;
import io.recordrelay.core.clone.domain.RelationshipNode;
import io.recordrelay.core.clone.domain.RelationshipSource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultCloneEngine#topoSortForWrite}.
 *
 * <p>Verifies that referenced tables (FK targets) are placed before tables that reference them,
 * matching the constraint that the referenced row must exist before the referencing row is
 * inserted.
 */
class TopoSortTest {

  private static RelationshipEdge fk(
      String fromTable, String fromCol, String toTable, String toCol) {
    return new RelationshipEdge(
        new RelationshipNode(fromTable),
        fromCol,
        new RelationshipNode(toTable),
        toCol,
        RelationshipSource.FOREIGN_KEY,
        1.0);
  }

  private static Set<String> ordered(String... tables) {
    // LinkedHashSet preserves insertion order, which matches BFS discovery order
    return new LinkedHashSet<>(List.of(tables));
  }

  @Test
  void referencedTableWrittenBeforeReferencingTable() {
    // customers.account_manager_id → users.id  (BFS discovered customers first)
    var graph =
        RelationshipGraph.builder()
            .addEdge(fk("customers", "account_manager_id", "users", "id"))
            .build();

    var result = DefaultCloneEngine.topoSortForWrite(ordered("customers", "users"), graph);

    assertThat(result.indexOf("users")).isLessThan(result.indexOf("customers"));
  }

  @Test
  void deepChainOrderedCorrectly() {
    // invoices → orders → customers → users
    var graph =
        RelationshipGraph.builder()
            .addEdge(fk("invoices", "order_id", "orders", "id"))
            .addEdge(fk("orders", "customer_id", "customers", "id"))
            .addEdge(fk("customers", "manager_id", "users", "id"))
            .build();

    var tables = ordered("invoices", "orders", "customers", "users");
    var result = DefaultCloneEngine.topoSortForWrite(tables, graph);

    assertThat(result.indexOf("users")).isLessThan(result.indexOf("customers"));
    assertThat(result.indexOf("customers")).isLessThan(result.indexOf("orders"));
    assertThat(result.indexOf("orders")).isLessThan(result.indexOf("invoices"));
  }

  @Test
  void tableWithNoFkEdgesIncluded() {
    var graph = RelationshipGraph.empty();
    var result = DefaultCloneEngine.topoSortForWrite(ordered("standalone"), graph);
    assertThat(result).containsExactly("standalone");
  }

  @Test
  void unreferencedTableFromWriteSetIsIgnoredAsDependency() {
    // orders → coupons, but coupons is NOT in the write set
    var graph =
        RelationshipGraph.builder().addEdge(fk("orders", "coupon_id", "coupons", "id")).build();

    // coupons not included → orders should still appear in result
    var result = DefaultCloneEngine.topoSortForWrite(ordered("orders"), graph);
    assertThat(result).containsExactly("orders");
  }

  @Test
  void multipleTablesReferencingSameParent() {
    // addresses → customers, payment_methods → customers (BFS order: customers, addresses,
    // payment_methods)
    var graph =
        RelationshipGraph.builder()
            .addEdge(fk("addresses", "customer_id", "customers", "id"))
            .addEdge(fk("payment_methods", "customer_id", "customers", "id"))
            .build();

    var tables = ordered("customers", "addresses", "payment_methods");
    var result = DefaultCloneEngine.topoSortForWrite(tables, graph);

    assertThat(result.indexOf("customers")).isLessThan(result.indexOf("addresses"));
    assertThat(result.indexOf("customers")).isLessThan(result.indexOf("payment_methods"));
  }

  @Test
  void allTablesPresent() {
    var graph =
        RelationshipGraph.builder().addEdge(fk("orders", "customer_id", "customers", "id")).build();

    var tables = ordered("customers", "orders");
    var result = DefaultCloneEngine.topoSortForWrite(tables, graph);
    assertThat(result).containsExactlyInAnyOrder("customers", "orders");
    assertThat(result).hasSize(2);
  }
}
