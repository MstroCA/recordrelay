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
package io.recordrelay.mapping;

import io.recordrelay.core.domain.ColumnCompatibility;
import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.SchemaMatchReport;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Compares source and target column sets and produces a {@link SchemaMatchReport} with
 * compatibility analysis and actionable recommendations.
 *
 * <p>Matching is case-insensitive. When a target column has no matching source column, the analyzer
 * suggests similarly-named source columns (case-insensitive substring match).
 */
public final class SchemaMatchingAnalyzer {

  private SchemaMatchingAnalyzer() {}

  /**
   * Analyses the structural compatibility between {@code source} and {@code target} columns.
   *
   * @param source columns available in the source table
   * @param target columns expected by the target table
   * @return a report with match percentage, per-column details, and recommendations
   */
  public static SchemaMatchReport analyze(List<ColumnMeta> source, List<ColumnMeta> target) {
    var sourceByName = new TreeMap<String, ColumnMeta>(String.CASE_INSENSITIVE_ORDER);
    source.forEach(c -> sourceByName.put(c.name(), c));

    var compatibilities = new ArrayList<ColumnCompatibility>();
    var warnings = new ArrayList<String>();
    int matches = 0;

    for (var tgtCol : target) {
      var srcCol = sourceByName.get(tgtCol.name());
      if (srcCol == null) {
        matches += handleMissingColumn(tgtCol, source, compatibilities, warnings);
      } else {
        matches += handleFoundColumn(srcCol, tgtCol, compatibilities, warnings);
      }
    }

    double pct = target.isEmpty() ? 100.0 : (matches * 100.0) / target.size();
    return new SchemaMatchReport(pct, List.copyOf(compatibilities), List.copyOf(warnings));
  }

  private static int handleMissingColumn(
      ColumnMeta tgtCol,
      List<ColumnMeta> source,
      List<ColumnCompatibility> compatibilities,
      List<String> warnings) {
    String suggestion = findSuggestion(tgtCol.name(), source);
    String msg =
        suggestion == null
            ? "Target column '" + tgtCol.name() + "' has no matching source column"
            : "Target column '"
                + tgtCol.name()
                + "' has no matching source column — did you mean '"
                + suggestion
                + "'?";
    warnings.add(msg);
    compatibilities.add(new ColumnCompatibility(null, tgtCol.name(), false, msg));
    return 0;
  }

  private static int handleFoundColumn(
      ColumnMeta srcCol,
      ColumnMeta tgtCol,
      List<ColumnCompatibility> compatibilities,
      List<String> warnings) {
    boolean typeOk = srcCol.nativeType().equalsIgnoreCase(tgtCol.nativeType());
    String warning = buildTypeWarning(srcCol, tgtCol, typeOk);
    if (warning != null) {
      warnings.add(warning);
    }
    compatibilities.add(new ColumnCompatibility(srcCol.name(), tgtCol.name(), typeOk, warning));
    return typeOk ? 1 : 0;
  }

  private static String buildTypeWarning(ColumnMeta src, ColumnMeta tgt, boolean typeOk) {
    if (typeOk) {
      return null;
    }
    return "Type mismatch for '"
        + tgt.name()
        + "': "
        + src.nativeType()
        + " → "
        + tgt.nativeType()
        + " — consider adding transform 'cast:"
        + tgt.nativeType().toLowerCase()
        + "'";
  }

  private static String findSuggestion(String targetName, List<ColumnMeta> source) {
    String lower = targetName.toLowerCase();
    for (var s : source) {
      String srcLower = s.name().toLowerCase();
      if (srcLower.contains(lower) || lower.contains(srcLower)) {
        return s.name();
      }
    }
    return null;
  }
}
