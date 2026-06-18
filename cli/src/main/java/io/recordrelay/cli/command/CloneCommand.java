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
import io.recordrelay.core.clone.domain.CloneJob;
import io.recordrelay.core.clone.domain.CloneReport;
import io.recordrelay.core.clone.domain.CloneRequest;
import io.recordrelay.core.clone.domain.MaskerType;
import io.recordrelay.core.clone.domain.MaskingConfig;
import io.recordrelay.core.clone.domain.MaskingRule;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.engine.clone.DefaultCloneEngine;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * {@code rr clone} — clones a root record and all its transitive dependencies into a target
 * database.
 *
 * <p>Example:
 *
 * <pre>
 * rr clone --source staging --target local --table customer --id 12345
 * rr clone --source test --target local --table orders --id 99 --depth 5 --mask
 * </pre>
 */
@Command(
    name = "clone",
    description = "Clone a root record and all related data from source to target.")
public final class CloneCommand implements Callable<Integer> {

  @ParentCommand private RecordRelayCli parent;

  @Option(
      names = {"--source", "-s"},
      required = true,
      description = "Source connection profile name")
  String source;

  @Option(
      names = {"--target", "-t"},
      required = true,
      description = "Target connection profile name")
  String target;

  @Option(
      names = {"--table"},
      required = true,
      description = "Root table name")
  String table;

  @Option(
      names = {"--id"},
      required = true,
      description = "Root record primary key value")
  String id;

  @Option(
      names = {"--depth"},
      description = "Relationship traversal depth (default: 3, max: 10)")
  int depth = CloneRequest.DEFAULT_DEPTH;

  @Option(
      names = {"--mask"},
      description = "Enable automatic masking of common PII columns (email, phone)")
  boolean mask;

  @Override
  public Integer call() {
    try {
      var printer = parent.printer();
      var store = parent.configStore();
      var resolver = new ConnProfileResolver(store);
      var srcProfile = resolver.resolve(source);
      var tgtProfile = resolver.resolve(target);

      var masking = buildMaskingConfig();
      var request =
          CloneRequest.builder(srcProfile, tgtProfile, table, id)
              .depth(depth)
              .masking(masking)
              .build();
      var job = CloneJob.of(request);

      printer.printLine(
          String.format(
              "Cloning %s:%s from '%s' → '%s' (depth=%d)", table, id, source, target, depth));

      var engine = DefaultCloneEngine.createDefault();
      var report = engine.clone(job, buildListener(printer));

      printReport(report);
      return ExitCode.SUCCESS;
    } catch (Exception e) {
      return EnvCommand.handleError(parent, e, ExitCode.CLONE_FAILED);
    }
  }

  private MaskingConfig buildMaskingConfig() {
    if (!mask) {
      return MaskingConfig.none();
    }
    return new MaskingConfig(
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
  }

  private CloneProgressListener buildListener(io.recordrelay.cli.output.Printer printer) {
    return new CloneProgressListener() {
      @Override
      public void onRelationshipsDiscovered(int edgeCount) {
        printer.printLine("  Discovered " + edgeCount + " relationship edge(s)");
      }

      @Override
      public void onTableExtractionStarted(String tableName) {
        printer.printLine("  Extracting: " + tableName);
      }

      @Override
      public void onTableExtractionCompleted(String tableName, long recordCount) {
        printer.printLine("    └─ " + recordCount + " record(s)");
      }

      @Override
      public void onImportStarted(String tableName) {
        printer.printLine("  Importing:  " + tableName);
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
    printer.printSuccess("Clone complete");
    printer.printLine("  Root Record   : " + report.rootTable() + ":" + report.rootId());
    printer.printLine("  Tables        : " + report.tableCount());
    printer.printLine("  Records       : " + report.totalRecords());
    printer.printLine("  Duration      : " + report.formattedDuration());
    if (!report.warnings().isEmpty()) {
      printer.printLine("  Warnings      : " + report.warnings().size());
    }
    if (report.maskedFieldCount() > 0) {
      printer.printLine("  Masked Fields : " + report.maskedFieldCount());
    }
  }
}
