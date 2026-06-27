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
 * JDBC-based {@link RelationshipResolverPort} that discovers table relationships using two
 * complementary strategies run in every resolution:
 *
 * <ol>
 *   <li><b>FK constraints</b> — queries {@code information_schema} for declared foreign keys
 *       (confidence = 1.0). Many real-world databases omit constraints even when logical
 *       relationships exist (bare BIGINT columns used as FKs).
 *   <li><b>Logical FK discovery</b> — scans {@code DatabaseMetaData} for {@code *_id} columns whose
 *       inferred target tables exist, and for child tables that carry a {@code {rootTable}_id}
 *       column (confidence = 0.80). This catches relationships that are never declared as
 *       constraints.
 * </ol>
 *
 * <p>Both strategies are always executed; their results are merged (FK constraint edges take
 * precedence — logical FK edges for already-known pairs are skipped). Only falls back to {@link
 * HeuristicRelationshipResolver} when the JDBC connection itself cannot be established.
 *
 * <p>Supports PostgreSQL, MySQL, and SQL Server. Other types use the heuristic fallback.
 */
public final class JdbcRelationshipResolver implements RelationshipResolverPort, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcRelationshipResolver.class);
  private static final double LOGICAL_FK_CONFIDENCE = 0.80;

  private static final String FK_QUERY =
      """
      SELECT
          kcu.table_name        AS from_table,
          kcu.column_name       AS from_column,
          ccu.table_name        AS to_table,
          ccu.column_name       AS to_column
      FROM information_schema.table_constraints AS tc
          JOIN information_schema.key_column_usage AS kcu
              ON tc.constraint_name = kcu.constraint_name
              AND tc.table_schema   = kcu.table_schema
          JOIN information_schema.constraint_column_usage AS ccu
              ON ccu.constraint_name = tc.constraint_name
              AND ccu.table_schema   = tc.table_schema
      WHERE tc.constraint_type = 'FOREIGN KEY'
        AND tc.table_schema NOT IN ('pg_catalog', 'information_schema')
      """;

  private final HeuristicRelationshipResolver heuristicFallback =
      new HeuristicRelationshipResolver();

  private HikariDataSource dataSource;

  @Override
  public RelationshipGraph resolve(ConnectionProfile profile, String rootTable)
      throws CloneException {
    ensurePool(profile);

    var builder = RelationshipGraph.builder().addNode(rootTable);

    try (Connection conn = dataSource.getConnection()) {
      var knownEdgeKeys = resolveForeignKeys(conn, rootTable, builder);
      discoverLogicalFks(conn, rootTable, builder, knownEdgeKeys);
    } catch (SQLException e) {
      LOG.warn(
          "JDBC resolution failed for '{}'; falling back to heuristics. Cause: {}",
          rootTable,
          e.getMessage());
      return heuristicFallback.resolve(profile, rootTable);
    }

    var graph = builder.build();
    LOG.debug("Resolved {} edge(s) for table '{}'", graph.edgeCount(), rootTable);
    return graph;
  }

  // ── FK constraint discovery ───────────────────────────────────────────────

  /**
   * Queries information_schema for declared FK constraints involving rootTable.
   *
   * @return set of "fromTable.fromColumn" keys already added, used to avoid duplicates
   */
  private Set<String> resolveForeignKeys(
      Connection conn, String rootTable, RelationshipGraph.Builder builder) throws SQLException {
    var knownKeys = new HashSet<String>();
    try (var stmt = conn.prepareStatement(FK_QUERY);
        var rs = stmt.executeQuery()) {
      while (rs.next()) {
        var fromTable = rs.getString("from_table");
        var fromColumn = rs.getString("from_column");
        var toTable = rs.getString("to_table");
        var toColumn = rs.getString("to_column");
        var edge =
            new RelationshipEdge(
                new RelationshipNode(fromTable.toLowerCase(Locale.ROOT)),
                fromColumn.toLowerCase(Locale.ROOT),
                new RelationshipNode(toTable.toLowerCase(Locale.ROOT)),
                toColumn.toLowerCase(Locale.ROOT),
                RelationshipSource.FOREIGN_KEY,
                1.0);
        builder.addEdge(edge);
        knownKeys.add(
            fromTable.toLowerCase(Locale.ROOT) + "." + fromColumn.toLowerCase(Locale.ROOT));
        LOG.debug("FK edge: {}", edge.describe());
      }
    }
    return knownKeys;
  }

  // ── Logical FK discovery (schema-driven, no hardcoded patterns) ───────────

  private void discoverLogicalFks(
      Connection conn,
      String rootTable,
      RelationshipGraph.Builder builder,
      Set<String> knownEdgeKeys)
      throws SQLException {
    discoverOutgoingLogicalFks(conn, rootTable, builder, knownEdgeKeys);
    discoverIncomingLogicalFks(conn, rootTable, builder, knownEdgeKeys);
  }

  private void discoverOutgoingLogicalFks(
      Connection conn,
      String rootTable,
      RelationshipGraph.Builder builder,
      Set<String> knownEdgeKeys)
      throws SQLException {
    try (var rs = conn.getMetaData().getColumns(null, null, rootTable, null)) {
      while (rs.next()) {
        var col = rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
        if (!col.endsWith("_id")) {
          continue;
        }
        var edgeKey = rootTable.toLowerCase(Locale.ROOT) + "." + col;
        if (knownEdgeKeys.contains(edgeKey)) {
          continue;
        }
        var base = col.substring(0, col.length() - 3);
        var refTable = pickExistingTable(conn, base);
        if (refTable == null || refTable.equalsIgnoreCase(rootTable)) {
          continue;
        }
        var edge =
            new RelationshipEdge(
                new RelationshipNode(rootTable.toLowerCase(Locale.ROOT)),
                col,
                new RelationshipNode(refTable),
                "id",
                RelationshipSource.HEURISTIC,
                LOGICAL_FK_CONFIDENCE);
        builder.addEdge(edge);
        knownEdgeKeys.add(edgeKey);
        LOG.debug("Logical FK (outgoing): {}", edge.describe());
      }
    }
  }

  private void discoverIncomingLogicalFks(
      Connection conn,
      String rootTable,
      RelationshipGraph.Builder builder,
      Set<String> knownEdgeKeys)
      throws SQLException {
    var fkColName = rootTable.toLowerCase(Locale.ROOT) + "_id";
    try (var rs = conn.getMetaData().getColumns(null, null, null, fkColName)) {
      while (rs.next()) {
        var schema = rs.getString("TABLE_SCHEM");
        if (isSystemSchema(schema)) {
          continue;
        }
        var childTable = rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
        var colName = rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
        // getColumns uses SQL LIKE for column pattern; verify exact match
        if (!colName.equals(fkColName) || childTable.equalsIgnoreCase(rootTable)) {
          continue;
        }
        var edgeKey = childTable + "." + fkColName;
        if (knownEdgeKeys.contains(edgeKey)) {
          continue;
        }
        var edge =
            new RelationshipEdge(
                new RelationshipNode(childTable),
                fkColName,
                new RelationshipNode(rootTable.toLowerCase(Locale.ROOT)),
                "id",
                RelationshipSource.HEURISTIC,
                LOGICAL_FK_CONFIDENCE);
        builder.addEdge(edge);
        knownEdgeKeys.add(edgeKey);
        LOG.debug("Logical FK (incoming): {}", edge.describe());
      }
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private static boolean tableExists(Connection conn, String tableName) throws SQLException {
    try (var rs = conn.getMetaData().getTables(null, null, tableName, new String[] {"TABLE"})) {
      while (rs.next()) {
        if (rs.getString("TABLE_NAME").equalsIgnoreCase(tableName)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Tries {@code base}, {@code base+"s"}, {@code base+"es"} against the live schema and returns the
   * first match, or {@code null} if none of the candidates exist.
   */
  private static String pickExistingTable(Connection conn, String base) throws SQLException {
    for (var candidate : java.util.List.of(base, base + "s", base + "es")) {
      if (tableExists(conn, candidate)) {
        return candidate;
      }
    }
    return null;
  }

  /**
   * Derives a probable table name from a {@code *_id} column name (pluralises if needed). Used only
   * for tests — runtime resolution uses {@link #pickExistingTable} which checks the live schema.
   */
  static String deriveTableName(String columnName) {
    var lower = columnName.toLowerCase(Locale.ROOT);
    if (!lower.endsWith("_id")) {
      return lower;
    }
    var base = lower.substring(0, lower.length() - 3);
    return base.endsWith("s") ? base : base + "s";
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

  private synchronized void ensurePool(ConnectionProfile profile) {
    if (dataSource == null || dataSource.isClosed()) {
      var cfg = new HikariConfig();
      cfg.setJdbcUrl(buildJdbcUrl(profile));
      cfg.setUsername(profile.credentials().username());
      cfg.setPassword(profile.credentials().password());
      cfg.setMaximumPoolSize(2);
      cfg.setConnectionTimeout(10_000);
      cfg.setPoolName("clone-schema-" + profile.id());
      dataSource = new HikariDataSource(cfg);
    }
  }

  private String buildJdbcUrl(ConnectionProfile profile) {
    return switch (profile.type()) {
      case POSTGRESQL -> profile.jdbcUrl("postgresql");
      case MYSQL -> profile.jdbcUrl("mysql");
      case SQLSERVER -> profile.jdbcUrl("sqlserver");
      default ->
          throw new IllegalArgumentException(
              "JdbcRelationshipResolver does not support "
                  + profile.type()
                  + "; use HeuristicRelationshipResolver instead");
    };
  }

  @Override
  public void close() {
    if (dataSource != null && !dataSource.isClosed()) {
      dataSource.close();
    }
  }
}
