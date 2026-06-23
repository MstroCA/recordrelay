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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.recordrelay.core.clone.domain.RelationshipEdge;
import io.recordrelay.core.clone.domain.RelationshipGraph;
import io.recordrelay.core.clone.domain.RelationshipNode;
import io.recordrelay.core.clone.domain.RelationshipSource;
import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.clone.port.out.RelationshipResolverPort;
import io.recordrelay.core.domain.ConnectionProfile;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Heuristic {@link RelationshipResolverPort} that infers FK relationships from column naming
 * conventions (priority 3 — lowest confidence).
 *
 * <p>When a live connection is available the resolver:
 *
 * <ol>
 *   <li>Loads the actual columns of the root table via {@code DatabaseMetaData.getColumns}.
 *   <li>Loads all user table names via {@code DatabaseMetaData.getTables}.
 *   <li>Adds outgoing edges only for {@code *_id} columns that exist in the root table AND whose
 *       inferred target table actually exists in the database.
 *   <li>Adds incoming edges by scanning for tables that have a {@code {rootTable}_id} column —
 *       these are child tables that hold a FK back to the root.
 * </ol>
 *
 * <p>Falls back to emitting all high-confidence patterns without verification when the database
 * cannot be reached or the type is not supported.
 */
public final class HeuristicRelationshipResolver implements RelationshipResolverPort {

  private static final Logger LOG = LoggerFactory.getLogger(HeuristicRelationshipResolver.class);

  private static final double HIGH_CONFIDENCE = 0.85;
  private static final double LOW_CONFIDENCE = 0.60;

  private static final List<String> HIGH_CONFIDENCE_PATTERNS =
      List.of(
          "customer_id",
          "order_id",
          "account_id",
          "user_id",
          "product_id",
          "invoice_id",
          "payment_id",
          "address_id",
          "category_id",
          "vendor_id",
          "employee_id",
          "department_id",
          "project_id",
          "ticket_id",
          "session_id",
          "tenant_id",
          "organisation_id",
          "organization_id");

  @Override
  public RelationshipGraph resolve(ConnectionProfile profile, String rootTable)
      throws CloneException {
    var builder = RelationshipGraph.builder().addNode(rootTable);

    String jdbcUrl;
    try {
      jdbcUrl = buildJdbcUrl(profile);
    } catch (IllegalArgumentException e) {
      LOG.debug("Unsupported DB type ({}); using all high-confidence patterns", profile.type());
      addAllHighConfidencePatterns(rootTable, builder);
      return builder.build();
    }

    var cfg = new HikariConfig();
    cfg.setJdbcUrl(jdbcUrl);
    cfg.setUsername(profile.credentials().username());
    cfg.setPassword(profile.credentials().password());
    cfg.setMaximumPoolSize(1);
    cfg.setConnectionTimeout(5_000);
    cfg.setPoolName("heuristic-" + profile.id());

    try (var ds = new HikariDataSource(cfg);
        var conn = ds.getConnection()) {
      var rootColumns = loadColumns(conn, rootTable);
      var existingTables = loadTables(conn);
      addOutgoingEdges(rootTable, rootColumns, existingTables, builder);
      addIncomingEdges(conn, rootTable, builder);
    } catch (Exception e) {
      LOG.debug(
          "Heuristic resolution failed for '{}': {}; using all high-confidence patterns",
          rootTable,
          e.getMessage());
      addAllHighConfidencePatterns(rootTable, builder);
    }

    var graph = builder.build();
    LOG.debug("Heuristic resolver found {} edge(s) for table '{}'", graph.edgeCount(), rootTable);
    return graph;
  }

  /**
   * Infers the referenced table name from an {@code _id} suffix column.
   *
   * <p>For example: {@code customer_id} → {@code customers}.
   */
  static String inferTableName(String columnName) {
    var lower = columnName.toLowerCase(Locale.ROOT);
    if (!lower.endsWith("_id")) {
      return lower;
    }
    var base = lower.substring(0, lower.length() - 3);
    return base.endsWith("s") ? base : base + "s";
  }

  /**
   * Returns the inferred confidence for a column name.
   *
   * @param columnName the column name to evaluate
   * @return confidence score in [0.0, 1.0]
   */
  public double confidenceFor(String columnName) {
    var lower = columnName.toLowerCase(Locale.ROOT);
    if (HIGH_CONFIDENCE_PATTERNS.contains(lower)) {
      return HIGH_CONFIDENCE;
    }
    return lower.endsWith("_id") ? LOW_CONFIDENCE : 0.0;
  }

  // ── Outgoing edges (columns in rootTable that look like FKs) ────────────────

  private void addOutgoingEdges(
      String rootTable,
      Set<String> rootColumns,
      Set<String> existingTables,
      RelationshipGraph.Builder builder) {
    addHighConfidenceOutgoing(rootTable, rootColumns, existingTables, builder);
    addLowConfidenceOutgoing(rootTable, rootColumns, existingTables, builder);
  }

  private void addHighConfidenceOutgoing(
      String rootTable,
      Set<String> rootColumns,
      Set<String> existingTables,
      RelationshipGraph.Builder builder) {
    for (String pattern : HIGH_CONFIDENCE_PATTERNS) {
      if (!rootColumns.isEmpty() && !rootColumns.contains(pattern)) {
        continue;
      }
      var refTable = inferTableName(pattern);
      if (!targetTableValid(refTable, rootTable, existingTables)) {
        continue;
      }
      var edge =
          new RelationshipEdge(
              new RelationshipNode(rootTable),
              pattern,
              new RelationshipNode(refTable),
              "id",
              RelationshipSource.HEURISTIC,
              HIGH_CONFIDENCE);
      builder.addEdge(edge);
      LOG.debug("Heuristic outgoing (high): {}", edge.describe());
    }
  }

  private void addLowConfidenceOutgoing(
      String rootTable,
      Set<String> rootColumns,
      Set<String> existingTables,
      RelationshipGraph.Builder builder) {
    for (String col : rootColumns) {
      if (!col.endsWith("_id") || HIGH_CONFIDENCE_PATTERNS.contains(col)) {
        continue;
      }
      var refTable = inferTableName(col);
      if (!targetTableValid(refTable, rootTable, existingTables)) {
        continue;
      }
      var edge =
          new RelationshipEdge(
              new RelationshipNode(rootTable),
              col,
              new RelationshipNode(refTable),
              "id",
              RelationshipSource.HEURISTIC,
              LOW_CONFIDENCE);
      builder.addEdge(edge);
      LOG.debug("Heuristic outgoing (low): {}", edge.describe());
    }
  }

  private static boolean targetTableValid(
      String refTable, String rootTable, Set<String> existingTables) {
    if (refTable.equalsIgnoreCase(rootTable)) {
      return false;
    }
    return existingTables.isEmpty() || existingTables.contains(refTable.toLowerCase(Locale.ROOT));
  }

  // ── Incoming edges (other tables with {rootTable}_id column) ────────────────

  private void addIncomingEdges(
      Connection conn, String rootTable, RelationshipGraph.Builder builder) throws SQLException {
    var fkColName = rootTable.toLowerCase(Locale.ROOT) + "_id";
    try (var rs = conn.getMetaData().getColumns(null, null, null, fkColName)) {
      while (rs.next()) {
        var schema = rs.getString("TABLE_SCHEM");
        if (isSystemSchema(schema)) {
          continue;
        }
        var childTable = rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
        var colName = rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
        // getColumns uses LIKE matching; verify exact column name to avoid false positives
        if (!colName.equals(fkColName) || childTable.equalsIgnoreCase(rootTable)) {
          continue;
        }
        var edge =
            new RelationshipEdge(
                new RelationshipNode(childTable),
                fkColName,
                new RelationshipNode(rootTable),
                "id",
                RelationshipSource.HEURISTIC,
                HIGH_CONFIDENCE);
        builder.addEdge(edge);
        LOG.debug("Heuristic incoming (high): {}", edge.describe());
      }
    }
  }

  // ── Fallback (no live DB) ────────────────────────────────────────────────────

  private static void addAllHighConfidencePatterns(
      String rootTable, RelationshipGraph.Builder builder) {
    for (String pattern : HIGH_CONFIDENCE_PATTERNS) {
      var refTable = inferTableName(pattern);
      if (refTable.equalsIgnoreCase(rootTable)) {
        continue;
      }
      builder.addEdge(
          new RelationshipEdge(
              new RelationshipNode(rootTable),
              pattern,
              new RelationshipNode(refTable),
              "id",
              RelationshipSource.HEURISTIC,
              HIGH_CONFIDENCE));
    }
  }

  // ── JDBC metadata helpers ────────────────────────────────────────────────────

  private static Set<String> loadColumns(Connection conn, String tableName) throws SQLException {
    var cols = new HashSet<String>();
    try (var rs = conn.getMetaData().getColumns(null, null, tableName, null)) {
      while (rs.next()) {
        cols.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
      }
    }
    LOG.debug("Loaded {} column(s) for table '{}'", cols.size(), tableName);
    return cols.isEmpty() ? Set.of() : Set.copyOf(cols);
  }

  private static Set<String> loadTables(Connection conn) throws SQLException {
    var tables = new HashSet<String>();
    try (var rs = conn.getMetaData().getTables(null, null, null, new String[] {"TABLE"})) {
      while (rs.next()) {
        var schema = rs.getString("TABLE_SCHEM");
        if (isSystemSchema(schema)) {
          continue;
        }
        tables.add(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
      }
    }
    LOG.debug("Loaded {} table(s) from database", tables.size());
    return Set.copyOf(tables);
  }

  private static boolean isSystemSchema(String schema) {
    if (schema == null) {
      return false;
    }
    var s = schema.toLowerCase(Locale.ROOT);
    return s.equals("information_schema")
        || s.equals("pg_catalog")
        || s.equals("performance_schema")
        || s.equals("sys")
        || s.equals("mysql")
        || s.startsWith("pg_");
  }

  private static String buildJdbcUrl(ConnectionProfile profile) {
    return switch (profile.type()) {
      case POSTGRESQL -> profile.jdbcUrl("postgresql");
      case MYSQL -> profile.jdbcUrl("mysql");
      case SQLSERVER -> profile.jdbcUrl("sqlserver");
      default ->
          throw new IllegalArgumentException(
              "HeuristicRelationshipResolver: unsupported type " + profile.type());
    };
  }
}
