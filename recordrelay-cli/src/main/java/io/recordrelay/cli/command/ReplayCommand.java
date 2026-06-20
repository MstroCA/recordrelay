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
import io.recordrelay.core.clone.domain.CloneReport;
import io.recordrelay.core.clone.domain.PackageManifest;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.engine.clone.DefaultReplayEngine;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * {@code rr replay} — imports a {@code .rrpkg} reproduction package into a target environment.
 *
 * <p>If the package contains bug-reproduction metadata, it is printed before replay begins so
 * engineers know exactly which issue the data set was captured to reproduce.
 *
 * <p>Examples:
 *
 * <pre>
 * rr replay bug-1234.rrpkg --target local
 * rr replay bug-1234.rrpkg --target staging --inspect
 * </pre>
 */
@Command(
    name = "replay",
    description = "Replay a .rrpkg reproduction package into a target environment.")
public final class ReplayCommand implements Callable<Integer> {

  @ParentCommand private RecordRelayCli parent;

  @Parameters(index = "0", description = "Path to the .rrpkg file to replay")
  Path packagePath;

  @Option(
      names = {"--target", "-t"},
      description = "Target connection profile name (omit to inspect only)")
  String target;

  @Option(
      names = {"--inspect"},
      description = "Print package metadata without importing any data")
  boolean inspectOnly;

  @Override
  public Integer call() {
    try {
      var printer = parent.printer();
      var engine = DefaultReplayEngine.createDefault();

      var manifest = engine.inspect(packagePath);
      printManifest(manifest);

      if (inspectOnly || target == null) {
        if (target == null && !inspectOnly) {
          printer.printLine(
              "No --target specified. Use --target <profile> to replay, or --inspect to inspect only.");
        }
        return ExitCode.SUCCESS;
      }

      var store = parent.configStore();
      var resolver = new ConnProfileResolver(store);
      var targetProfile = resolver.resolve(target);

      printer.printLine("");
      printer.printLine("Replaying into '" + target + "'…");

      var report = engine.replay(packagePath, targetProfile, buildListener(printer));
      printReport(report);
      return ExitCode.SUCCESS;

    } catch (Exception e) {
      return EnvCommand.handleError(parent, e, ExitCode.REPLAY_FAILED);
    }
  }

  private void printManifest(PackageManifest manifest) throws Exception {
    var printer = parent.printer();
    printer.printLine("Package: " + packagePath.getFileName());
    printer.printLine("  Format       : v" + manifest.formatVersion());
    printer.printLine("  Captured     : " + manifest.createdAt());
    printer.printLine("  Connector    : " + manifest.sourceConnectorId());
    if (manifest.hasEntityContext()) {
      printer.printLine(
          "  Entity       : " + manifest.businessEntityName() + " #" + manifest.rootId());
    } else {
      printer.printLine("  Root Record  : " + manifest.rootTable() + " #" + manifest.rootId());
    }
    printer.printLine(
        "  Tables       : " + manifest.tableNames().size() + " " + manifest.tableNames());

    if (manifest.hasBugReport()) {
      var bug = manifest.bugReport();
      printer.printLine("");
      printer.printLine("Bug Reproduction Context:");
      printer.printLine("  ID           : " + bug.id());
      printer.printLine("  Title        : " + bug.title());
      if (bug.service() != null) {
        printer.printLine("  Service      : " + bug.service());
      }
      if (bug.environment() != null) {
        printer.printLine("  Environment  : " + bug.environment());
      }
      if (bug.capturedAt() != null) {
        printer.printLine("  Captured At  : " + bug.capturedAt());
      }
      if (bug.stepsToReproduce() != null) {
        printer.printLine("  Steps:");
        for (var line : bug.stepsToReproduce().split("\n")) {
          printer.printLine("    " + line);
        }
      }
    }
  }

  private CloneProgressListener buildListener(io.recordrelay.cli.output.Printer printer) {
    return new CloneProgressListener() {
      @Override
      public void onTableExtractionStarted(String tableName) {
        printer.printLine("  Importing: " + tableName);
      }

      @Override
      public void onImportCompleted(String tableName, long recordCount) {
        printer.printLine("    └─ " + recordCount + " record(s)");
      }

      @Override
      public void onWarning(String message) {
        printer.printLine("  WARN: " + message);
      }
    };
  }

  private void printReport(CloneReport report) throws Exception {
    var printer = parent.printer();
    printer.printLine("");
    printer.printSuccess("Replay complete");
    printer.printLine("  Root Record : " + report.rootTable() + ":" + report.rootId());
    printer.printLine("  Tables      : " + report.tableCount());
    printer.printLine("  Records     : " + report.totalRecords());
    printer.printLine("  Duration    : " + report.formattedDuration());
    if (!report.warnings().isEmpty()) {
      printer.printLine("  Warnings    : " + report.warnings().size());
    }
  }
}
