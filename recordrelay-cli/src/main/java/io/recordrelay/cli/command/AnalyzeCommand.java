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
package io.recordrelay.cli.command;

import io.recordrelay.cli.ExitCode;
import io.recordrelay.cli.RecordRelayCli;
import io.recordrelay.cli.engine.ConnProfileResolver;
import io.recordrelay.cli.engine.DiscoveryEngine;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.TableRef;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/** Reports schema compatibility between a source and target table pair. */
@Command(name = "analyze", description = "Analyse schema compatibility between two tables.")
public final class AnalyzeCommand implements Callable<Integer> {

  @ParentCommand private RecordRelayCli parent;

  @Option(names = "--source-conn", required = true, description = "Source connection name")
  String sourceConn;

  @Option(names = "--source-table", required = true, description = "Source table (schema.table)")
  String sourceTable;

  @Option(names = "--target-conn", required = true, description = "Target connection name")
  String targetConn;

  @Option(names = "--target-table", required = true, description = "Target table (schema.table)")
  String targetTable;

  private DiscoveryEngine discovery = new DiscoveryEngine();

  /** For testing. */
  void setDiscovery(DiscoveryEngine discovery) {
    this.discovery = discovery;
  }

  @Override
  public Integer call() {
    try {
      var store = parent.configStore();
      var resolver = new ConnProfileResolver(store);
      var srcProfile = resolver.resolve(sourceConn);
      var tgtProfile = resolver.resolve(targetConn);

      var srcRef = parseTableRef(sourceTable, srcProfile.type());
      var tgtRef = parseTableRef(targetTable, tgtProfile.type());

      var report = discovery.analyzeCompatibility(srcProfile, srcRef, tgtProfile, tgtRef);

      parent.printer().printLine(String.format("Match: %.1f%%", report.matchPercentage()));
      printCompatibilityTable(report.columnCompatibilities());
      if (!report.warnings().isEmpty()) {
        parent.printer().printLine("Warnings:");
        for (var w : report.warnings()) {
          parent.printer().printLine("  - " + w);
        }
      }
      return ExitCode.SUCCESS;
    } catch (Exception e) {
      return EnvCommand.handleError(parent, e, ExitCode.CONNECTION_ERROR);
    }
  }

  private void printCompatibilityTable(List<io.recordrelay.core.domain.ColumnCompatibility> cols)
      throws Exception {
    var rows = new ArrayList<List<String>>();
    for (var col : cols) {
      rows.add(
          List.of(
              col.sourceColumn() != null ? col.sourceColumn() : "(missing)",
              col.targetColumn() != null ? col.targetColumn() : "",
              col.typeCompatible() ? "OK" : "MISMATCH",
              col.warning() != null ? col.warning() : ""));
    }
    parent.printer().printTable(List.of("SOURCE", "TARGET", "COMPAT", "WARNING"), rows);
  }

  private TableRef parseTableRef(String spec, io.recordrelay.core.domain.DatabaseType dbType) {
    String schema = null;
    String tbl = spec;
    int dot = spec.indexOf('.');
    if (dot >= 0) {
      schema = spec.substring(0, dot);
      tbl = spec.substring(dot + 1);
    }
    return new TableRef(new DatabaseRef(null, dbType), schema, tbl);
  }
}
