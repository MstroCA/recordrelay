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
import io.recordrelay.cli.engine.MaskingCoverageEngine;
import io.recordrelay.cli.engine.MaskingCoverageEngine.CoverageStatus;
import io.recordrelay.core.clone.domain.MaskerType;
import io.recordrelay.core.clone.domain.MaskingConfig;
import io.recordrelay.core.clone.domain.MaskingRule;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * {@code rr mask} — masking analysis commands.
 *
 * <pre>
 * rr mask coverage --from prod
 * rr mask coverage --from prod --exposed-only
 * rr mask coverage --from prod --json
 * </pre>
 */
@Command(
    name = "mask",
    description = "Masking analysis commands.",
    subcommands = {MaskCommand.CoverageCommand.class})
public final class MaskCommand implements Callable<Integer> {

  @ParentCommand RecordRelayCli parent;

  @Override
  public Integer call() {
    new CommandLine(this).usage(System.out);
    return ExitCode.SUCCESS;
  }

  @Command(
      name = "coverage",
      description =
          "Scan a source database and report which columns contain PII and whether they are covered"
              + " by masking rules.")
  static final class CoverageCommand implements Callable<Integer> {

    @ParentCommand MaskCommand mask;

    @Option(names = "--from", required = true, description = "Source connection profile name")
    String source;

    @Option(
        names = "--exposed-only",
        description = "Show only columns with detected PII that are NOT covered by masking rules")
    boolean exposedOnly;

    @Override
    public Integer call() {
      try {
        var store = mask.parent.configStore();
        var resolver = new ConnProfileResolver(store);
        var srcProfile = resolver.resolve(source);
        var printer = mask.parent.printer();

        printer.printLine("Scanning schema of '" + source + "' for PII coverage…");

        // Use the full built-in PII masking config for coverage checking
        var builtinMasking =
            new MaskingConfig(
                List.of(
                    new MaskingRule("email", MaskerType.EMAIL),
                    new MaskingRule("phone", MaskerType.PHONE),
                    new MaskingRule("phone_number", MaskerType.PHONE),
                    new MaskingRule("mobile", MaskerType.PHONE),
                    new MaskingRule("address", MaskerType.ADDRESS),
                    new MaskingRule("street_address", MaskerType.ADDRESS),
                    new MaskingRule("national_id", MaskerType.NATIONAL_ID),
                    new MaskingRule("ssn", MaskerType.NATIONAL_ID),
                    new MaskingRule("iban", MaskerType.IBAN)));

        var report = MaskingCoverageEngine.analyse(srcProfile, builtinMasking, source);

        var rows = new ArrayList<List<String>>();
        for (var entry : report.entries()) {
          if (exposedOnly && entry.status() != CoverageStatus.EXPOSED) continue;
          if (entry.status() == CoverageStatus.CLEAN && !exposedOnly) continue;
          rows.add(
              List.of(
                  entry.tableName(),
                  entry.columnName(),
                  entry.columnType(),
                  entry.status().name(),
                  entry.piiCategory()));
        }

        if (rows.isEmpty()) {
          printer.printLine(
              exposedOnly ? "No exposed PII columns found." : "No PII-like columns detected.");
        } else {
          printer.printTable(List.of("TABLE", "COLUMN", "TYPE", "STATUS", "PII CATEGORY"), rows);
        }

        printer.printLine("");
        printer.printLine(
            String.format(
                "Summary: %d total columns  |  %d EXPOSED  |  %d MASKED  |  %d LOW-RISK",
                report.totalColumns(),
                report.exposedCount(),
                report.maskedCount(),
                report.lowRiskCount()));
        if (report.exposedCount() > 0) {
          printer.printLine(
              String.format(
                  "Masking coverage: %.1f%% of PII-like columns are covered (add --mask to clone commands to cover them)",
                  report.coveragePct()));
          return ExitCode.SUCCESS;
        } else {
          printer.printSuccess("All detected PII columns are covered.");
          return ExitCode.SUCCESS;
        }
      } catch (Exception e) {
        return EnvCommand.handleError(mask.parent, e, ExitCode.VALIDATION_ERROR);
      }
    }
  }
}
