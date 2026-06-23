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
 * <p>When a live connection is available the resolver first queries {@code
 * DatabaseMetaData.getColumns} to obtain the actual column set for the root table, then only emits
 * edges for columns that genuinely exist. Unknown or unreachable databases fall back to treating
 * every {@code *_id} column as a potential FK candidate.
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
    var actualColumns = loadActualColumns(profile, rootTable);
    var builder = RelationshipGraph.builder().addNode(rootTable);

    // High-confidence well-known patterns — only add if column actually exists
    for (String pattern : HIGH_CONFIDENCE_PATTERNS) {
      if (!actualColumns.isEmpty() && !actualColumns.contains(pattern)) {
        continue;
      }
      var referencedTable = inferTableName(pattern);
      if (!referencedTable.equalsIgnoreCase(rootTable)) {
        var edge =
            new RelationshipEdge(
                new RelationshipNode(rootTable),
                pattern,
                new RelationshipNode(referencedTable),
                "id",
                RelationshipSource.HEURISTIC,
                HIGH_CONFIDENCE);
        builder.addEdge(edge);
        LOG.debug("Heuristic edge (high): {}", edge.describe());
      }
    }

    // Low-confidence: any *_id column in the actual schema not already covered
    for (String col : actualColumns) {
      if (col.endsWith("_id") && !HIGH_CONFIDENCE_PATTERNS.contains(col)) {
        var referencedTable = inferTableName(col);
        if (!referencedTable.equalsIgnoreCase(rootTable)) {
          var edge =
              new RelationshipEdge(
                  new RelationshipNode(rootTable),
                  col,
                  new RelationshipNode(referencedTable),
                  "id",
                  RelationshipSource.HEURISTIC,
                  LOW_CONFIDENCE);
          builder.addEdge(edge);
          LOG.debug("Heuristic edge (low): {}", edge.describe());
        }
      }
    }

    var graph = builder.build();
    LOG.debug("Heuristic resolver found {} edges for table '{}'", graph.edgeCount(), rootTable);
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

  /**
   * Attempts to load the actual column names of {@code rootTable} from the live database. Returns
   * an empty set when the connection cannot be established or the database type is not supported,
   * causing the caller to treat all heuristic patterns as candidates.
   */
  private Set<String> loadActualColumns(ConnectionProfile profile, String rootTable) {
    String jdbcUrl;
    try {
      jdbcUrl = buildJdbcUrl(profile);
    } catch (IllegalArgumentException e) {
      LOG.debug("Unsupported DB type for column check ({}); using all patterns", profile.type());
      return Set.of();
    }

    var cfg = new HikariConfig();
    cfg.setJdbcUrl(jdbcUrl);
    cfg.setUsername(profile.credentials().username());
    cfg.setPassword(profile.credentials().password());
    cfg.setMaximumPoolSize(1);
    cfg.setConnectionTimeout(5_000);
    cfg.setPoolName("heuristic-cols-" + profile.id());

    try (var ds = new HikariDataSource(cfg);
        var conn = ds.getConnection();
        var rs = conn.getMetaData().getColumns(null, null, rootTable, null)) {
      var cols = new HashSet<String>();
      while (rs.next()) {
        cols.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
      }
      LOG.debug("Loaded {} actual columns for table '{}'", cols.size(), rootTable);
      return cols.isEmpty() ? Set.of() : Set.copyOf(cols);
    } catch (Exception e) {
      LOG.debug(
          "Could not load columns for '{}': {}; falling back to all heuristic patterns",
          rootTable,
          e.getMessage());
      return Set.of();
    }
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
