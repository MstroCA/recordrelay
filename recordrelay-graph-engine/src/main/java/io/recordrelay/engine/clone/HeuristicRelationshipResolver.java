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
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schema-driven heuristic {@link RelationshipResolverPort} for databases not supported by {@link
 * JdbcRelationshipResolver} (e.g. SQLite, Oracle).
 *
 * <p>When a live connection is available:
 *
 * <ol>
 *   <li>Loads actual columns of the root table via {@code DatabaseMetaData.getColumns}.
 *   <li>Loads all user table names via {@code DatabaseMetaData.getTables}.
 *   <li>Adds outgoing edges for {@code *_id} columns whose inferred target table exists.
 *   <li>Discovers incoming child tables via {@code getColumns(null,null,null,rootTable+"_id")}.
 * </ol>
 *
 * <p>All discovery is purely schema-driven — no hardcoded column name patterns. Returns a root-only
 * graph (no edges) with a warning when the database cannot be reached.
 */
public final class HeuristicRelationshipResolver implements RelationshipResolverPort {

  private static final Logger LOG = LoggerFactory.getLogger(HeuristicRelationshipResolver.class);
  private static final double HEURISTIC_CONFIDENCE = 0.80;

  @Override
  public RelationshipGraph resolve(ConnectionProfile profile, String rootTable)
      throws CloneException {
    var builder = RelationshipGraph.builder().addNode(rootTable);

    String jdbcUrl;
    try {
      jdbcUrl = buildJdbcUrl(profile);
    } catch (IllegalArgumentException e) {
      LOG.warn(
          "Cannot discover relationships without a database connection for table '{}' ({})",
          rootTable,
          profile.type());
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
      addOutgoingLogicalFks(rootTable, rootColumns, existingTables, builder);
      addIncomingLogicalFks(conn, rootTable, builder);
    } catch (Exception e) {
      LOG.warn(
          "Cannot discover relationships without a database connection for table '{}': {}",
          rootTable,
          e.getMessage());
      return builder.build();
    }

    var graph = builder.build();
    LOG.debug("Heuristic resolver found {} edge(s) for table '{}'", graph.edgeCount(), rootTable);
    return graph;
  }

  /**
   * Returns the heuristic confidence for a column name ({@code 0.80} for {@code *_id} columns,
   * {@code 0.0} otherwise).
   */
  public double confidenceFor(String columnName) {
    return columnName.toLowerCase(Locale.ROOT).endsWith("_id") ? HEURISTIC_CONFIDENCE : 0.0;
  }

  // ── Outgoing edges ────────────────────────────────────────────────────────

  private void addOutgoingLogicalFks(
      String rootTable,
      Set<String> rootColumns,
      Set<String> existingTables,
      RelationshipGraph.Builder builder) {
    for (String col : rootColumns) {
      if (!col.endsWith("_id")) {
        continue;
      }
      var refTable = JdbcRelationshipResolver.deriveTableName(col);
      if (refTable.equalsIgnoreCase(rootTable)) {
        continue;
      }
      if (!existingTables.isEmpty()
          && !existingTables.contains(refTable.toLowerCase(Locale.ROOT))) {
        continue;
      }
      var edge =
          new RelationshipEdge(
              new RelationshipNode(rootTable),
              col,
              new RelationshipNode(refTable),
              "id",
              RelationshipSource.HEURISTIC,
              HEURISTIC_CONFIDENCE);
      builder.addEdge(edge);
      LOG.debug("Heuristic outgoing: {}", edge.describe());
    }
  }

  // ── Incoming edges ────────────────────────────────────────────────────────

  private void addIncomingLogicalFks(
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
                HEURISTIC_CONFIDENCE);
        builder.addEdge(edge);
        LOG.debug("Heuristic incoming: {}", edge.describe());
      }
    }
  }

  // ── JDBC metadata helpers ─────────────────────────────────────────────────

  private static Set<String> loadColumns(Connection conn, String tableName) throws SQLException {
    var cols = new HashSet<String>();
    try (var rs = conn.getMetaData().getColumns(null, null, tableName, null)) {
      while (rs.next()) {
        cols.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
      }
    }
    return cols.isEmpty() ? Set.of() : Set.copyOf(cols);
  }

  private static Set<String> loadTables(Connection conn) throws SQLException {
    var tables = new HashSet<String>();
    try (var rs = conn.getMetaData().getTables(null, null, null, new String[] {"TABLE"})) {
      while (rs.next()) {
        var schema = rs.getString("TABLE_SCHEM");
        if (!isSystemSchema(schema)) {
          tables.add(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
        }
      }
    }
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
