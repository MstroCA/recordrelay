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

import org.junit.jupiter.api.Test;

class RelationshipSourceTest {

  @Test
  void manualEnumValueExists() {
    // MANUAL was added to support user-drawn FK edges in the graph editor
    var manual = RelationshipSource.valueOf("MANUAL");
    assertThat(manual).isEqualTo(RelationshipSource.MANUAL);
  }

  @Test
  void allExpectedValuesPresent() {
    var values = RelationshipSource.values();
    assertThat(values)
        .extracting(Enum::name)
        .contains("FOREIGN_KEY", "UNIQUE_KEY", "HEURISTIC", "MANUAL");
  }

  @Test
  void manualEdgeCanBeBuiltIntoRelationshipGraph() {
    var nodeA = new RelationshipNode("orders");
    var nodeB = new RelationshipNode("customers");
    var edge =
        new RelationshipEdge(nodeA, "customer_id", nodeB, "id", RelationshipSource.MANUAL, 1.0);

    assertThat(edge.source()).isEqualTo(RelationshipSource.MANUAL);
    assertThat(edge.fromNode().tableName()).isEqualTo("orders");
    assertThat(edge.toNode().tableName()).isEqualTo("customers");
    assertThat(edge.fromColumn()).isEqualTo("customer_id");
    assertThat(edge.toColumn()).isEqualTo("id");
  }

  @Test
  void manualEdgeAddedToGraphIsRetained() {
    var nodeA = new RelationshipNode("orders");
    var nodeB = new RelationshipNode("customers");
    var nodeC = new RelationshipNode("invoices");
    var normalEdge =
        new RelationshipEdge(
            nodeA, "customer_id", nodeB, "id", RelationshipSource.FOREIGN_KEY, 1.0);
    var manualEdge =
        new RelationshipEdge(nodeC, "order_id", nodeA, "id", RelationshipSource.MANUAL, 1.0);

    var graph =
        RelationshipGraph.builder()
            .addNode(nodeA)
            .addNode(nodeB)
            .addNode(nodeC)
            .addEdge(normalEdge)
            .addEdge(manualEdge)
            .build();

    assertThat(graph.edgeCount()).isEqualTo(2);
    long manualCount =
        graph.edges().stream().filter(e -> e.source() == RelationshipSource.MANUAL).count();
    assertThat(manualCount).isEqualTo(1);
  }

  @Test
  void declaredEdgeIsDistinctFromManual() {
    assertThat(RelationshipSource.FOREIGN_KEY).isNotEqualTo(RelationshipSource.MANUAL);
    assertThat(RelationshipSource.HEURISTIC).isNotEqualTo(RelationshipSource.MANUAL);
  }
}
