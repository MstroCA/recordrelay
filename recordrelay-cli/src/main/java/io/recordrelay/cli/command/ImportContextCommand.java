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
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.engine.clone.DefaultCloneEngine;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * {@code rr import} — reads a {@code .rrpkg} archive and reproduces all context records into the
 * target environment.
 *
 * <p>Examples:
 *
 * <pre>
 * rr import customer-12345-1234567890.rrpkg --target local
 * rr import ./exports/order-99.rrpkg --target dev
 * </pre>
 */
@Command(name = "import", description = "Import a .rrpkg reproduction package into an environment.")
public final class ImportContextCommand implements Callable<Integer> {

  @ParentCommand private RecordRelayCli parent;

  @Parameters(
      index = "0",
      paramLabel = "<file>",
      description = "Path to the .rrpkg archive to import")
  Path packageFile;

  @Option(
      names = {"--target", "-t"},
      required = true,
      description = "Target environment connection profile name")
  String target;

  @Override
  public Integer call() {
    try {
      var printer = parent.printer();
      var store = parent.configStore();
      var resolver = new ConnProfileResolver(store);
      var tgtProfile = resolver.resolve(target);

      printer.printLine(String.format("Importing %s into '%s'", packageFile.getFileName(), target));

      var engine = DefaultCloneEngine.createDefault();
      var report = engine.importPackage(packageFile, tgtProfile, buildListener(printer));

      printer.printSuccess("Import complete");
      printer.printLine("  Tables  : " + report.tableCount());
      printer.printLine("  Records : " + report.totalRecords());
      printer.printLine("  Duration: " + report.formattedDuration());
      if (!report.warnings().isEmpty()) {
        printer.printLine("  Warnings: " + report.warnings().size());
        report.warnings().forEach(w -> printer.printLine("    - " + w));
      }
      return ExitCode.SUCCESS;
    } catch (Exception e) {
      return EnvCommand.handleError(parent, e, ExitCode.CLONE_FAILED);
    }
  }

  private CloneProgressListener buildListener(io.recordrelay.cli.output.Printer printer) {
    return new CloneProgressListener() {
      @Override
      public void onImportStarted(String tableName) {
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
}
