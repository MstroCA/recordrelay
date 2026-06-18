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
package io.recordrelay.core.clone.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.recordrelay.core.clone.domain.RelationshipEdge;
import io.recordrelay.core.clone.domain.RelationshipGraph;
import io.recordrelay.core.clone.domain.RelationshipNode;
import io.recordrelay.core.clone.domain.RelationshipSource;
import org.junit.jupiter.api.Test;

class GraphTraverserTest {

  private final GraphTraverser traverser = new GraphTraverser();

  @Test
  void traverseEmptyGraphReturnsOnlyRoot() {
    var graph = RelationshipGraph.empty();
    var root = new TraversalNode("customers", "id", "42", 0);

    var result = traverser.traverse(graph, root, 3);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).tableName()).isEqualTo("customers");
  }

  @Test
  void traverseFollowsEdgesWithinDepthLimit() {
    var customers = new RelationshipNode("customers");
    var orders = new RelationshipNode("orders");
    var edge =
        new RelationshipEdge(
            orders, "customer_id", customers, "id", RelationshipSource.FOREIGN_KEY, 1.0);

    var graph = RelationshipGraph.builder().addEdge(edge).build();
    var root = new TraversalNode("customers", "id", "42", 0);

    var result = traverser.traverse(graph, root, 3);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).tableName()).isEqualTo("customers");
  }

  @Test
  void traverseRespectsMaxDepth() {
    var a = new RelationshipNode("a");
    var b = new RelationshipNode("b");
    var c = new RelationshipNode("c");
    var edge1 = new RelationshipEdge(a, "b_id", b, "id", RelationshipSource.HEURISTIC, 0.8);
    var edge2 = new RelationshipEdge(b, "c_id", c, "id", RelationshipSource.HEURISTIC, 0.8);

    var graph = RelationshipGraph.builder().addEdge(edge1).addEdge(edge2).build();
    var root = new TraversalNode("a", "id", "1", 0);

    var result = traverser.traverse(graph, root, 1);

    var tableNames = result.stream().map(TraversalNode::tableName).toList();
    assertThat(tableNames).contains("a");
    assertThat(tableNames).doesNotContain("c");
  }

  @Test
  void traversePreventsInfiniteLoops() {
    var a = new RelationshipNode("a");
    var b = new RelationshipNode("b");
    var edgeAtoB = new RelationshipEdge(a, "b_id", b, "id", RelationshipSource.HEURISTIC, 0.7);
    var edgeBtoA = new RelationshipEdge(b, "a_id", a, "id", RelationshipSource.HEURISTIC, 0.7);

    var graph = RelationshipGraph.builder().addEdge(edgeAtoB).addEdge(edgeBtoA).build();
    var root = new TraversalNode("a", "id", "1", 0);

    // Must terminate — visited-node tracking prevents infinite loops
    var result = traverser.traverse(graph, root, 5);
    assertThat(result).isNotEmpty();
  }

  @Test
  void reachableTablesExcludesTablesBeyondDepth() {
    var a = new RelationshipNode("a");
    var b = new RelationshipNode("b");
    var c = new RelationshipNode("c");
    var edge1 = new RelationshipEdge(a, "b_id", b, "id", RelationshipSource.FOREIGN_KEY, 1.0);
    var edge2 = new RelationshipEdge(b, "c_id", c, "id", RelationshipSource.FOREIGN_KEY, 1.0);

    var graph = RelationshipGraph.builder().addEdge(edge1).addEdge(edge2).build();

    var depth1 = traverser.reachableTables(graph, "a", 1);
    assertThat(depth1).containsExactlyInAnyOrder("a", "b");

    var depth2 = traverser.reachableTables(graph, "a", 2);
    assertThat(depth2).containsExactlyInAnyOrder("a", "b", "c");
  }

  @Test
  void traversalNodeVisitKeyCombinesTableAndId() {
    var node = new TraversalNode("orders", "id", "99", 2);
    assertThat(node.visitKey()).isEqualTo("orders:99");
  }
}
