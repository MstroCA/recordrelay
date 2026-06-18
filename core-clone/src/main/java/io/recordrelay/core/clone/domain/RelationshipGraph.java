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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An immutable directed graph of table relationships.
 *
 * <p>Nodes represent tables; edges represent foreign key or heuristic relationships. Use {@link
 * Builder} to construct instances.
 */
public final class RelationshipGraph {

  private final Map<String, RelationshipNode> nodes;
  private final List<RelationshipEdge> edges;

  private RelationshipGraph(Map<String, RelationshipNode> nodes, List<RelationshipEdge> edges) {
    this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
    this.edges = List.copyOf(edges);
  }

  /** Returns all nodes in the graph. */
  public Map<String, RelationshipNode> nodes() {
    return nodes;
  }

  /** Returns all edges in the graph. */
  public List<RelationshipEdge> edges() {
    return edges;
  }

  /** Returns the node for {@code tableName}, or empty if not present. */
  public Optional<RelationshipNode> getNode(String tableName) {
    return Optional.ofNullable(nodes.get(tableName));
  }

  /**
   * Returns all edges where {@code fromNode.tableName()} equals {@code tableName}.
   *
   * <p>These are outgoing edges: {@code tableName.column → other_table.column}.
   */
  public List<RelationshipEdge> edgesFrom(String tableName) {
    return edges.stream()
        .filter(e -> e.fromNode().tableName().equals(tableName))
        .collect(Collectors.toUnmodifiableList());
  }

  /**
   * Returns all edges where {@code toNode.tableName()} equals {@code tableName}.
   *
   * <p>These are incoming edges: {@code other_table.column → tableName.column}.
   */
  public List<RelationshipEdge> edgesTo(String tableName) {
    return edges.stream()
        .filter(e -> e.toNode().tableName().equals(tableName))
        .collect(Collectors.toUnmodifiableList());
  }

  /** Returns the total number of nodes. */
  public int nodeCount() {
    return nodes.size();
  }

  /** Returns the total number of edges. */
  public int edgeCount() {
    return edges.size();
  }

  /** Returns true when the graph contains no nodes. */
  public boolean isEmpty() {
    return nodes.isEmpty();
  }

  /** Creates a new mutable builder. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns an empty graph. */
  public static RelationshipGraph empty() {
    return new RelationshipGraph(Map.of(), List.of());
  }

  /** Mutable builder for {@link RelationshipGraph}. Not thread-safe. */
  public static final class Builder {

    private final Map<String, RelationshipNode> nodes = new LinkedHashMap<>();
    private final List<RelationshipEdge> edges = new ArrayList<>();

    private Builder() {}

    /** Adds a node; silently no-ops if a node for this table already exists. */
    public Builder addNode(RelationshipNode node) {
      Objects.requireNonNull(node, "node");
      nodes.putIfAbsent(node.tableName(), node);
      return this;
    }

    /** Adds a node by table name. */
    public Builder addNode(String tableName) {
      return addNode(new RelationshipNode(tableName));
    }

    /**
     * Adds an edge and ensures both endpoint nodes are present.
     *
     * @param edge the relationship edge to add
     * @return this builder
     */
    public Builder addEdge(RelationshipEdge edge) {
      Objects.requireNonNull(edge, "edge");
      addNode(edge.fromNode());
      addNode(edge.toNode());
      edges.add(edge);
      return this;
    }

    /** Builds an immutable {@link RelationshipGraph}. */
    public RelationshipGraph build() {
      return new RelationshipGraph(nodes, edges);
    }
  }
}
