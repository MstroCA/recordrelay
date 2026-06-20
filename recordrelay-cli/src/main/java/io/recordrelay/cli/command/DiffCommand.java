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
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * {@code rr diff} — compares the state of a business entity context across two environments.
 *
 * <p>A diff answers: "is customer-123 in local consistent with what's in production?"
 *
 * <p>Examples:
 *
 * <pre>
 * rr diff --customer-id 123 --left prod --right local
 * rr diff --order-id 987 --left staging --right local --depth 5
 * rr diff --table invoices --id 42 --left prod --right staging
 * </pre>
 */
@Command(name = "diff", description = "Compare a business entity context across two environments.")
public final class DiffCommand implements Callable<Integer> {

  @ParentCommand private RecordRelayCli parent;

  // ── Environment profiles ──────────────────────────────────────────────────

  @Option(
      names = {"--left"},
      required = true,
      description = "Reference (left) environment profile name")
  String left;

  @Option(
      names = {"--right"},
      required = true,
      description = "Comparison (right) environment profile name")
  String right;

  // ── Semantic entity shortcuts ─────────────────────────────────────────────

  @Option(names = "--customer-id", description = "Customer ID to compare")
  String customerId;

  @Option(names = "--order-id", description = "Order ID to compare")
  String orderId;

  @Option(names = "--user-id", description = "User ID to compare")
  String userId;

  // ── Generic form ──────────────────────────────────────────────────────────

  @Option(names = "--table", description = "Root table name (generic form)")
  String table;

  @Option(names = "--id", description = "Root record primary key value (generic form)")
  String id;

  @Option(
      names = {"--depth"},
      description = "Relationship traversal depth (default: 3)")
  int depth = 3;

  @Override
  public Integer call() {
    var printer = parent.printer();
    printer.printLine("rr diff — comparing business context across environments");
    printer.printLine("");
    printer.printLine("  Left  : " + left);
    printer.printLine("  Right : " + right);
    printer.printLine("  Depth : " + depth);
    printer.printLine("");
    printer.printLine("Full diff implementation coming in Faz 15.");
    printer.printLine("Use 'rr clone' to synchronise environments in the meantime.");
    return ExitCode.SUCCESS;
  }
}
