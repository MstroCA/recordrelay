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
package io.recordrelay.cli.flow;

import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.TableRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Observable data model for the visual flow query builder.
 *
 * <p>Holds table nodes (with selected columns), JOIN edges, WHERE filters, ORDER BY clauses, and a
 * row limit. Generates a SQL SELECT statement from the current state via {@link #toSql()}.
 *
 * <p>This class is UI-framework agnostic — used by both the IntelliJ plugin (Swing) and the
 * desktop application (JavaFX).
 */
public final class QueryFlowModel {

  // ── Inner types ─────────────────────────────────────────────────────────────

  /** SQL join type used when connecting two table nodes. */
  public enum JoinType {
    /** {@code INNER JOIN}: only matching rows. */
    INNER("INNER JOIN"),
    /** {@code LEFT JOIN}: all rows from the left table. */
    LEFT("LEFT JOIN"),
    /** {@code RIGHT JOIN}: all rows from the right table. */
    RIGHT("RIGHT JOIN");

    /** The SQL keyword phrase for this join type, e.g. {@code "INNER JOIN"}. */
    public final String keyword;

    JoinType(String keyword) {
      this.keyword = keyword;
    }
  }

  /** A table node in the query flow. {@code selectedColumns} is intentionally mutable. */
  public static final class NodeEntry {
    private final String alias;
    private final TableRef table;
    private final List<ColumnMeta> columns;
    private final LinkedHashSet<String> selectedColumns;

    NodeEntry(String alias, TableRef table, List<ColumnMeta> columns) {
      this.alias = alias;
      this.table = table;
      this.columns = List.copyOf(columns);
      this.selectedColumns = new LinkedHashSet<>();
      columns.forEach(c -> selectedColumns.add(c.name()));
    }

    /** Short SQL alias for this node (e.g. {@code u1}, {@code o2}). */
    public String alias() {
      return alias;
    }

    /** The database table this node represents. */
    public TableRef table() {
      return table;
    }

    /** All columns available in this table, in ordinal order. */
    public List<ColumnMeta> columns() {
      return columns;
    }

    /** Mutable set of column names included in the SELECT list. */
    public LinkedHashSet<String> selectedColumns() {
      return selectedColumns;
    }

    /** Returns {@code true} when {@code colName} is included in the SELECT list. */
    public boolean isSelected(String colName) {
      return selectedColumns.contains(colName);
    }
  }

  /** An edge connecting two column ports — rendered as a JOIN clause. */
  public record JoinEntry(
      String fromAlias,
      String fromColumn,
      String toAlias,
      String toColumn,
      JoinType joinType) {}

  /** A WHERE predicate: {@code tableAlias.column operator 'value'}. */
  public record WhereFilter(String tableAlias, String column, String operator, String value) {}

  /** An ORDER BY clause with direction. */
  public record OrderByEntry(String tableAlias, String column, boolean ascending) {}

  // ── State ────────────────────────────────────────────────────────────────────

  private final List<NodeEntry> nodes = new ArrayList<>();
  private final List<JoinEntry> joins = new ArrayList<>();
  private final List<WhereFilter> filters = new ArrayList<>();
  private final List<OrderByEntry> orderBys = new ArrayList<>();
  private int limit = 100;
  private int aliasSeq = 0;

  private final List<Runnable> changeListeners = new ArrayList<>();

  // ── Listener support ─────────────────────────────────────────────────────────

  /** Registers a listener called after every model mutation. */
  public void addChangeListener(Runnable r) {
    changeListeners.add(r);
  }

  private void fire() {
    changeListeners.forEach(Runnable::run);
  }

  // ── Node operations ──────────────────────────────────────────────────────────

  /**
   * Adds a table node to the model.
   *
   * @return the new entry (alias already assigned)
   */
  public NodeEntry addNode(TableRef table, List<ColumnMeta> columns) {
    String alias = table.tableName().substring(0, 1).toLowerCase() + (++aliasSeq);
    var entry = new NodeEntry(alias, table, columns);
    nodes.add(entry);
    fire();
    return entry;
  }

  /** Removes the node with {@code alias} and all associated joins / filters / order-bys. */
  public void removeNode(String alias) {
    nodes.removeIf(n -> n.alias().equals(alias));
    joins.removeIf(j -> j.fromAlias().equals(alias) || j.toAlias().equals(alias));
    filters.removeIf(f -> f.tableAlias().equals(alias));
    orderBys.removeIf(o -> o.tableAlias().equals(alias));
    fire();
  }

  /** Toggles {@code colName} in/out of the SELECT list for the node identified by {@code alias}. */
  public void toggleColumn(String alias, String colName) {
    nodes.stream()
        .filter(n -> n.alias().equals(alias))
        .findFirst()
        .ifPresent(
            n -> {
              if (n.selectedColumns().contains(colName)) {
                n.selectedColumns().remove(colName);
              } else {
                n.selectedColumns().add(colName);
              }
              fire();
            });
  }

  // ── Join operations ──────────────────────────────────────────────────────────

  /** Adds a JOIN edge between two column ports. */
  public void addJoin(
      String fromAlias, String fromCol, String toAlias, String toCol, JoinType type) {
    joins.add(new JoinEntry(fromAlias, fromCol, toAlias, toCol, type));
    fire();
  }

  /** Removes the join at {@code index} (0-based). */
  public void removeJoin(int index) {
    if (index >= 0 && index < joins.size()) {
      joins.remove(index);
      fire();
    }
  }

  // ── Filter / order operations ─────────────────────────────────────────────────

  /** Appends a WHERE filter row. */
  public void addFilter(String alias, String col, String op, String val) {
    filters.add(new WhereFilter(alias, col, op, val));
    fire();
  }

  /** Removes the WHERE filter at {@code index}. */
  public void removeFilter(int index) {
    if (index >= 0 && index < filters.size()) {
      filters.remove(index);
      fire();
    }
  }

  /** Appends an ORDER BY clause. */
  public void addOrderBy(String alias, String col, boolean asc) {
    orderBys.add(new OrderByEntry(alias, col, asc));
    fire();
  }

  /** Removes the ORDER BY clause at {@code index}. */
  public void removeOrderBy(int index) {
    if (index >= 0 && index < orderBys.size()) {
      orderBys.remove(index);
      fire();
    }
  }

  /** Sets the row limit ({@code LIMIT} clause). Use {@code 0} to omit the clause. */
  public void setLimit(int limit) {
    this.limit = limit;
    fire();
  }

  /** Returns the current row limit. */
  public int getLimit() {
    return limit;
  }

  // ── Accessors ────────────────────────────────────────────────────────────────

  /** Returns an unmodifiable view of all table nodes. */
  public List<NodeEntry> nodes() {
    return Collections.unmodifiableList(nodes);
  }

  /** Returns an unmodifiable view of all JOIN edges. */
  public List<JoinEntry> joins() {
    return Collections.unmodifiableList(joins);
  }

  /** Returns an unmodifiable view of all WHERE filters. */
  public List<WhereFilter> filters() {
    return Collections.unmodifiableList(filters);
  }

  /** Returns an unmodifiable view of all ORDER BY clauses. */
  public List<OrderByEntry> orderBys() {
    return Collections.unmodifiableList(orderBys);
  }

  /** Clears all nodes, joins, filters, and order-bys and resets the alias counter. */
  public void clear() {
    nodes.clear();
    joins.clear();
    filters.clear();
    orderBys.clear();
    aliasSeq = 0;
    fire();
  }

  // ── SQL generation ───────────────────────────────────────────────────────────

  /**
   * Generates a SELECT statement from the current model state.
   *
   * @return a formatted SQL string, or a comment placeholder when no tables are present
   */
  public String toSql() {
    if (nodes.isEmpty()) {
      return "-- Add tables to the canvas to build a query";
    }
    var sb = new StringBuilder();
    var ordered = orderedNodes();
    appendSelect(sb, ordered);
    appendFrom(sb, ordered);
    appendJoins(sb, ordered);
    appendWhere(sb);
    appendOrderBy(sb);
    appendLimit(sb);
    return sb.toString().trim();
  }

  private void appendSelect(StringBuilder sb, List<NodeEntry> ordered) {
    sb.append("SELECT\n");
    var parts = new ArrayList<String>();
    for (var node : ordered) {
      for (var col : node.columns()) {
        if (node.isSelected(col.name())) {
          parts.add("  " + node.alias() + "." + col.name());
        }
      }
    }
    sb.append(parts.isEmpty() ? "  *" : String.join(",\n", parts));
    sb.append("\n");
  }

  private static void appendFrom(StringBuilder sb, List<NodeEntry> ordered) {
    var root = ordered.get(0);
    sb.append("FROM ").append(root.table().qualifiedName())
        .append(" AS ").append(root.alias()).append("\n");
  }

  private void appendJoins(StringBuilder sb, List<NodeEntry> ordered) {
    var joined = new LinkedHashSet<String>();
    joined.add(ordered.get(0).alias());
    for (int i = 1; i < ordered.size(); i++) {
      var node = ordered.get(i);
      var join = joins.stream()
          .filter(j -> isConnectionBetween(j, node.alias(), joined))
          .findFirst();
      if (join.isPresent()) {
        var j = join.get();
        sb.append(j.joinType().keyword).append(" ")
            .append(node.table().qualifiedName()).append(" AS ").append(node.alias())
            .append(" ON ").append(j.fromAlias()).append(".").append(j.fromColumn())
            .append(" = ").append(j.toAlias()).append(".").append(j.toColumn()).append("\n");
      } else {
        sb.append("CROSS JOIN ").append(node.table().qualifiedName())
            .append(" AS ").append(node.alias()).append("\n");
      }
      joined.add(node.alias());
    }
  }

  private static boolean isConnectionBetween(
      JoinEntry j, String alias, LinkedHashSet<String> joined) {
    return (j.toAlias().equals(alias) && joined.contains(j.fromAlias()))
        || (j.fromAlias().equals(alias) && joined.contains(j.toAlias()));
  }

  private void appendWhere(StringBuilder sb) {
    if (filters.isEmpty()) {
      return;
    }
    sb.append("WHERE\n");
    var parts = new ArrayList<String>();
    for (var f : filters) {
      parts.add(filterClause(f));
    }
    sb.append(String.join("\n  AND ", parts)).append("\n");
  }

  private static String filterClause(WhereFilter f) {
    String col = f.tableAlias() + "." + f.column();
    if (f.operator().startsWith("IS")) {
      return "  " + col + " " + f.operator();
    }
    return "  " + col + " " + f.operator() + " '" + f.value() + "'";
  }

  private void appendOrderBy(StringBuilder sb) {
    if (orderBys.isEmpty()) {
      return;
    }
    sb.append("ORDER BY ");
    var parts = new ArrayList<String>();
    for (var ob : orderBys) {
      parts.add(ob.tableAlias() + "." + ob.column() + " " + (ob.ascending() ? "ASC" : "DESC"));
    }
    sb.append(String.join(", ", parts)).append("\n");
  }

  private void appendLimit(StringBuilder sb) {
    if (limit > 0) {
      sb.append("LIMIT ").append(limit);
    }
  }

  /** BFS traversal starting from the first node, following JOIN edges. */
  private List<NodeEntry> orderedNodes() {
    if (nodes.isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<NodeEntry>();
    var visited = new LinkedHashSet<String>();
    var queue = new ArrayList<String>();
    queue.add(nodes.get(0).alias());

    while (!queue.isEmpty()) {
      var alias = queue.remove(0);
      if (!visited.add(alias)) {
        continue;
      }
      nodes.stream().filter(n -> n.alias().equals(alias)).findFirst().ifPresent(result::add);
      enqueueConnected(alias, visited, queue);
    }

    nodes.stream().filter(n -> !visited.contains(n.alias())).forEach(result::add);
    return result;
  }

  private void enqueueConnected(
      String alias, LinkedHashSet<String> visited, List<String> queue) {
    for (var j : joins) {
      if (j.fromAlias().equals(alias) && !visited.contains(j.toAlias())) {
        queue.add(j.toAlias());
      } else if (j.toAlias().equals(alias) && !visited.contains(j.fromAlias())) {
        queue.add(j.fromAlias());
      }
    }
  }
}
