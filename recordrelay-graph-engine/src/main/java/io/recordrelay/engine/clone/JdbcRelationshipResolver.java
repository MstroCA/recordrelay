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
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC-based {@link RelationshipResolverPort} that reads foreign key metadata from {@code
 * information_schema}.
 *
 * <p>Resolution priorities:
 *
 * <ol>
 *   <li>Foreign key constraints (confidence = 1.0)
 *   <li>Heuristic column naming, delegated to {@link HeuristicRelationshipResolver} after FK scan
 * </ol>
 *
 * <p>Currently supports PostgreSQL, MySQL, and SQL Server (all expose {@code information_schema}).
 * SQLite and Oracle use the heuristic fallback.
 */
public final class JdbcRelationshipResolver implements RelationshipResolverPort, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcRelationshipResolver.class);

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
      resolveForeignKeys(conn, rootTable, builder);
    } catch (SQLException e) {
      LOG.warn(
          "FK resolution failed for {}; falling back to heuristics. Cause: {}",
          rootTable,
          e.getMessage());
      return heuristicFallback.resolve(profile, rootTable);
    }

    var graph = builder.build();

    // Augment with heuristic discovery for tables that have no FK coverage
    if (graph.edgeCount() == 0) {
      LOG.debug("No FK edges found for {}; using heuristic resolver", rootTable);
      return heuristicFallback.resolve(profile, rootTable);
    }

    return graph;
  }

  private void resolveForeignKeys(
      Connection conn, String rootTable, RelationshipGraph.Builder builder) throws SQLException {
    try (var stmt = conn.prepareStatement(FK_QUERY);
        var rs = stmt.executeQuery()) {
      while (rs.next()) {
        var fromTable = rs.getString("from_table");
        var fromColumn = rs.getString("from_column");
        var toTable = rs.getString("to_table");
        var toColumn = rs.getString("to_column");

        // Include edges where root table is either the FK owner or the referenced table
        if (fromTable.equalsIgnoreCase(rootTable) || toTable.equalsIgnoreCase(rootTable)) {
          var edge =
              new RelationshipEdge(
                  new RelationshipNode(fromTable.toLowerCase(Locale.ROOT)),
                  fromColumn.toLowerCase(Locale.ROOT),
                  new RelationshipNode(toTable.toLowerCase(Locale.ROOT)),
                  toColumn.toLowerCase(Locale.ROOT),
                  RelationshipSource.FOREIGN_KEY,
                  1.0);
          builder.addEdge(edge);
          LOG.debug("FK edge: {}", edge.describe());
        }
      }
    }
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
