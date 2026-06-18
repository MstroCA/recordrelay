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

import io.recordrelay.core.clone.domain.RelationshipEdge;
import io.recordrelay.core.clone.domain.RelationshipGraph;
import io.recordrelay.core.clone.domain.RelationshipNode;
import io.recordrelay.core.clone.domain.RelationshipSource;
import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.clone.port.out.RelationshipResolverPort;
import io.recordrelay.core.domain.ConnectionProfile;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Heuristic {@link RelationshipResolverPort} that infers relationships from column naming
 * conventions (priority 3 — lowest confidence).
 *
 * <p>A column named {@code <X>_id} in the root table is assumed to reference the primary key
 * ({@code id}) of table {@code <X>} (or its plural form). Confidence scores reflect the strength of
 * the naming match.
 *
 * <p>Known patterns and their confidence:
 *
 * <ul>
 *   <li>{@code customer_id} → {@code customers.id} — 0.85
 *   <li>{@code order_id} → {@code orders.id} — 0.85
 *   <li>{@code account_id} → {@code accounts.id} — 0.85
 *   <li>{@code user_id} → {@code users.id} — 0.85
 *   <li>Any {@code *_id} suffix → inferred table — 0.60
 * </ul>
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

    for (String pattern : HIGH_CONFIDENCE_PATTERNS) {
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
        LOG.debug("Heuristic edge: {}", edge.describe());
      }
    }

    var graph = builder.build();
    LOG.debug(
        "Heuristic resolver found {} potential edges for table '{}'", graph.edgeCount(), rootTable);
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
    // Naive pluralisation: append 's' if not already plural
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
}
