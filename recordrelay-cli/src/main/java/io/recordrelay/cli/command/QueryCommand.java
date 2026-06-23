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
import io.recordrelay.cli.engine.QueryRunner;
import io.recordrelay.core.i18n.Messages;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * {@code rr query} — executes a read-only SQL SELECT against a saved connection.
 *
 * <pre>
 * rr query --from prod --sql "SELECT id, name FROM customers LIMIT 10"
 * rr query --from prod --sql "SELECT COUNT(*) FROM orders"
 * </pre>
 *
 * <p>Only {@code SELECT} statements are accepted; DML and DDL are rejected without connecting.
 */
@Command(
    name = "query",
    description = "Run a read-only SELECT query against a saved connection profile.")
public final class QueryCommand implements Callable<Integer> {

  @ParentCommand private RecordRelayCli parent;

  @Option(
      names = {"--from", "-f"},
      required = true,
      description = "Connection profile name to query")
  String source;

  @Option(
      names = {"--sql", "-q"},
      required = true,
      description = "SELECT query to execute")
  String sql;

  @Override
  public Integer call() {
    try {
      var printer = parent.printer();
      var resolver = new ConnProfileResolver(parent.configStore());
      var profile = resolver.resolve(source);

      printer.printLine("Executing query on '" + source + "'…");
      var result = new QueryRunner().run(profile, sql);

      if (result.columns().isEmpty()) {
        printer.printLine("(no columns returned)");
        return ExitCode.SUCCESS;
      }

      printTable(result.columns(), result.rows());
      printer.printLine(Messages.get("query.rows", result.rowCount()));
      return ExitCode.SUCCESS;

    } catch (IllegalArgumentException e) {
      try {
        parent.printer().printError(e.getMessage());
      } catch (Exception ignored) {
        // best-effort print
      }
      return ExitCode.VALIDATION_ERROR;
    } catch (Exception e) {
      return EnvCommand.handleError(parent, e, ExitCode.CLONE_FAILED);
    }
  }

  private void printTable(java.util.List<String> columns, java.util.List<java.util.List<String>> rows) {
    var printer = parent.printer();
    int[] widths = new int[columns.size()];
    for (int i = 0; i < columns.size(); i++) {
      widths[i] = columns.get(i).length();
    }
    for (var row : rows) {
      for (int i = 0; i < row.size() && i < widths.length; i++) {
        widths[i] = Math.max(widths[i], row.get(i).length());
      }
    }

    var sb = new StringBuilder();
    for (int i = 0; i < columns.size(); i++) {
      if (i > 0) {
        sb.append(" | ");
      }
      sb.append(pad(columns.get(i), widths[i]));
    }
    printer.printLine(sb.toString());

    sb.setLength(0);
    for (int i = 0; i < columns.size(); i++) {
      if (i > 0) {
        sb.append("-+-");
      }
      sb.append("-".repeat(widths[i]));
    }
    printer.printLine(sb.toString());

    for (var row : rows) {
      sb.setLength(0);
      for (int i = 0; i < row.size() && i < columns.size(); i++) {
        if (i > 0) {
          sb.append(" | ");
        }
        sb.append(pad(row.get(i), widths[i]));
      }
      printer.printLine(sb.toString());
    }
  }

  private static String pad(String s, int width) {
    if (s.length() >= width) {
      return s;
    }
    return s + " ".repeat(width - s.length());
  }
}
