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

import io.recordrelay.core.clone.domain.RelationshipEdge;
import io.recordrelay.core.clone.domain.RelationshipGraph;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Breadth-first traversal of a {@link RelationshipGraph}.
 *
 * <p>Starting from a root {@link TraversalNode}, the traverser enqueues all reachable {@link
 * RelationshipEdge}s up to a configurable max depth. Visited nodes (identified by {@code
 * tableName:idValue}) are tracked to prevent infinite loops in circular FK graphs.
 *
 * <p>This class is stateless and safe for reuse across traversals.
 */
public final class GraphTraverser {

  /**
   * Produces an ordered list of traversal nodes to process, using BFS from {@code root}.
   *
   * <p>The root node is always the first element. Nodes beyond {@code maxDepth} are not enqueued.
   * The same {@code visitKey()} is never returned twice.
   *
   * @param graph the relationship graph
   * @param root the starting traversal node
   * @param maxDepth maximum hops from the root (inclusive)
   * @return ordered traversal sequence; never null
   */
  public List<TraversalNode> traverse(RelationshipGraph graph, TraversalNode root, int maxDepth) {
    var result = new ArrayList<TraversalNode>();
    var visited = new HashSet<String>();
    var queue = new ArrayDeque<TraversalNode>();

    queue.add(root);

    while (!queue.isEmpty()) {
      var current = queue.poll();
      var key = current.visitKey();

      if (visited.contains(key)) {
        continue;
      }
      visited.add(key);
      result.add(current);

      if (current.depth() >= maxDepth) {
        continue;
      }

      enqueueChildren(graph, current, queue, visited);
    }

    return Collections.unmodifiableList(result);
  }

  /**
   * Returns only the set of table names reachable from {@code rootTable} within {@code maxDepth}
   * hops, without requiring a concrete record ID (useful for pre-flight analysis).
   *
   * @param graph the relationship graph
   * @param rootTable the starting table name
   * @param maxDepth maximum traversal depth
   * @return an unmodifiable set of reachable table names (including the root)
   */
  public Set<String> reachableTables(RelationshipGraph graph, String rootTable, int maxDepth) {
    var reachable = new HashSet<String>();
    var queue = new ArrayDeque<String>();
    var depthMap = new java.util.HashMap<String, Integer>();

    queue.add(rootTable);
    depthMap.put(rootTable, 0);

    while (!queue.isEmpty()) {
      var table = queue.poll();
      reachable.add(table);
      int depth = depthMap.getOrDefault(table, 0);

      if (depth >= maxDepth) {
        continue;
      }

      for (var edge : graph.edgesFrom(table)) {
        var next = edge.toNode().tableName();
        if (!reachable.contains(next)) {
          depthMap.put(next, depth + 1);
          queue.add(next);
        }
      }
    }

    return Collections.unmodifiableSet(reachable);
  }

  private void enqueueChildren(
      RelationshipGraph graph,
      TraversalNode current,
      ArrayDeque<TraversalNode> queue,
      Set<String> visited) {
    // Outgoing: current table references another (e.g. orders.customer_id -> customers.id)
    // When processing orders, follow FK value to find the referenced customer.
    for (RelationshipEdge edge : graph.edgesFrom(current.tableName())) {
      var nextTable = edge.toNode().tableName();
      var placeholderKey = nextTable + ":__pending__";
      if (!visited.contains(placeholderKey)) {
        queue.add(
            new TraversalNode(nextTable, edge.toColumn(), current.idValue(), current.depth() + 1));
      }
    }
    // Incoming: another table references current (e.g. orders.customer_id -> customers.id)
    // When processing customers, find all orders where customer_id = customers.id.
    for (RelationshipEdge edge : graph.edgesTo(current.tableName())) {
      var nextTable = edge.fromNode().tableName();
      var placeholderKey = nextTable + ":__pending__";
      if (!visited.contains(placeholderKey)) {
        queue.add(
            new TraversalNode(
                nextTable, edge.fromColumn(), current.idValue(), current.depth() + 1));
      }
    }
  }
}
