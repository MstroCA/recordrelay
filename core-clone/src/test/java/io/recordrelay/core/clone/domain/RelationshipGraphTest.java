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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RelationshipGraphTest {

  @Test
  void emptyGraphHasNoNodesOrEdges() {
    var graph = RelationshipGraph.empty();
    assertThat(graph.isEmpty()).isTrue();
    assertThat(graph.nodeCount()).isZero();
    assertThat(graph.edgeCount()).isZero();
  }

  @Test
  void builderAddNodeMakesNodeReachable() {
    var graph = RelationshipGraph.builder().addNode("customers").build();
    assertThat(graph.getNode("customers")).isPresent();
    assertThat(graph.nodeCount()).isEqualTo(1);
  }

  @Test
  void builderAddEdgeImplicitlyAddsEndpointNodes() {
    var customers = new RelationshipNode("customers");
    var orders = new RelationshipNode("orders");
    var edge =
        new RelationshipEdge(
            orders, "customer_id", customers, "id", RelationshipSource.FOREIGN_KEY, 0.95);

    var graph = RelationshipGraph.builder().addEdge(edge).build();

    assertThat(graph.nodeCount()).isEqualTo(2);
    assertThat(graph.edgeCount()).isEqualTo(1);
    assertThat(graph.getNode("customers")).isPresent();
    assertThat(graph.getNode("orders")).isPresent();
  }

  @Test
  void edgesFromReturnsOnlyOutgoingEdges() {
    var customers = new RelationshipNode("customers");
    var orders = new RelationshipNode("orders");
    var invoices = new RelationshipNode("invoices");

    var edge1 =
        new RelationshipEdge(
            orders, "customer_id", customers, "id", RelationshipSource.FOREIGN_KEY, 1.0);
    var edge2 =
        new RelationshipEdge(
            invoices, "customer_id", customers, "id", RelationshipSource.FOREIGN_KEY, 1.0);

    var graph = RelationshipGraph.builder().addEdge(edge1).addEdge(edge2).build();

    assertThat(graph.edgesFrom("orders")).containsExactly(edge1);
    assertThat(graph.edgesFrom("invoices")).containsExactly(edge2);
    assertThat(graph.edgesFrom("customers")).isEmpty();
  }

  @Test
  void edgesToReturnsOnlyIncomingEdges() {
    var customers = new RelationshipNode("customers");
    var orders = new RelationshipNode("orders");
    var edge =
        new RelationshipEdge(
            orders, "customer_id", customers, "id", RelationshipSource.FOREIGN_KEY, 1.0);

    var graph = RelationshipGraph.builder().addEdge(edge).build();

    assertThat(graph.edgesTo("customers")).containsExactly(edge);
    assertThat(graph.edgesTo("orders")).isEmpty();
  }

  @Test
  void addNodeDuplicateNameIsIdempotent() {
    var graph = RelationshipGraph.builder().addNode("customers").addNode("customers").build();
    assertThat(graph.nodeCount()).isEqualTo(1);
  }

  @Test
  void relationshipNodeBlankNameThrowsException() {
    assertThatThrownBy(() -> new RelationshipNode("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tableName");
  }

  @Test
  void relationshipEdgeInvalidConfidenceThrowsException() {
    var a = new RelationshipNode("a");
    var b = new RelationshipNode("b");
    assertThatThrownBy(
            () -> new RelationshipEdge(a, "col", b, "id", RelationshipSource.HEURISTIC, 1.5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("confidence");
  }

  @Test
  void relationshipEdgeDescribeReturnsReadableString() {
    var orders = new RelationshipNode("orders");
    var customers = new RelationshipNode("customers");
    var edge =
        new RelationshipEdge(
            orders, "customer_id", customers, "id", RelationshipSource.FOREIGN_KEY, 0.95);

    assertThat(edge.describe()).contains("orders.customer_id -> customers.id");
    assertThat(edge.describe()).contains("FOREIGN_KEY");
  }
}
