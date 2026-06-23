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
import io.recordrelay.core.clone.domain.BugReport;
import io.recordrelay.core.clone.domain.CloneJob;
import io.recordrelay.core.clone.domain.CloneReport;
import io.recordrelay.core.clone.domain.CloneRequest;
import io.recordrelay.core.clone.domain.ContextClonePlan;
import io.recordrelay.core.clone.domain.FieldOverride;
import io.recordrelay.core.clone.domain.FieldOverrideConfig;
import io.recordrelay.core.clone.domain.MaskerType;
import io.recordrelay.core.clone.domain.MaskingConfig;
import io.recordrelay.core.clone.domain.MaskingRule;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.core.i18n.Messages;
import io.recordrelay.engine.clone.BuiltinEntityRegistry;
import io.recordrelay.engine.clone.DefaultCloneEngine;
import io.recordrelay.engine.clone.DefaultContextCloneEngine;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * {@code rr clone} — reproduces a production business context locally.
 *
 * <p>Entity form (recommended) — resolves entity names to the correct table and ID column:
 *
 * <pre>
 * rr clone --entity customer --id 123 --from prod --target local
 * rr clone --entity order --id 987654 --from staging --target local --mask
 * rr clone --entity user --id 42 --from prod --export --bug-title "Login fails"
 * </pre>
 *
 * <p>Semantic shortcuts (convenience aliases):
 *
 * <pre>
 * rr clone --customer-id 123 --from prod --target local
 * rr clone --order-id 987654 --from staging --target local --mask
 * </pre>
 */
@Command(
    name = "clone",
    description = "Reproduce a production business context (customer, order, user…) locally.")
public final class CloneCommand implements Callable<Integer> {

  @ParentCommand private RecordRelayCli parent;

  // ── Connection profiles ───────────────────────────────────────────────────

  @Option(
      names = {"--from", "-f"},
      required = true,
      description = "Environment connection profile name to clone from")
  String source;

  @Option(
      names = {"--target", "-t"},
      description = "Target connection profile name (omit when --export is used)")
  String target;

  // ── Semantic entity shortcuts ─────────────────────────────────────────────

  @Option(names = "--customer-id", description = "Customer ID (resolves to the customers table)")
  String customerId;

  @Option(names = "--order-id", description = "Order ID (resolves to the orders table)")
  String orderId;

  @Option(names = "--user-id", description = "User ID (resolves to the users table)")
  String userId;

  @Option(names = "--product-id", description = "Product ID (resolves to the products table)")
  String productId;

  @Option(names = "--invoice-id", description = "Invoice ID (resolves to the invoices table)")
  String invoiceId;

  @Option(names = "--account-id", description = "Account ID (resolves to the accounts table)")
  String accountId;

  // ── Entity / generic form ─────────────────────────────────────────────────

  @Option(
      names = {"--entity", "--table"},
      description = "Entity or table name (e.g. customer, order, invoices)")
  String table;

  @Option(names = "--id", description = "Root entity primary key value")
  String id;

  // ── Clone options ─────────────────────────────────────────────────────────

  @Option(
      names = {"--depth"},
      description = "Relationship traversal depth (default: 3, max: 10)")
  int depth = CloneRequest.DEFAULT_DEPTH;

  @Option(
      names = {"--mask"},
      description = "Enable automatic masking of common PII columns (email, phone, address, iban)")
  boolean mask;

  // ── Export / bug capture options ──────────────────────────────────────────

  @Option(
      names = {"--export"},
      description = "Export to .rrpkg instead of writing to a live target")
  boolean export;

  @Option(
      names = {"--output-dir"},
      description = "Output directory for .rrpkg export (default: current directory)")
  Path outputDir;

  @Option(names = "--bug-id", description = "Bug / ticket ID to embed in the package")
  String bugId;

  @Option(names = "--bug-title", description = "Bug title to embed in the package")
  String bugTitle;

  @Option(names = "--bug-service", description = "Service name affected by the bug")
  String bugService;

  @Option(names = "--bug-env", description = "Source environment (e.g. production, staging)")
  String bugEnv;

  // ── Field overrides ───────────────────────────────────────────────────────

  @Option(
      names = {"--override", "-o"},
      description =
          "Override a field in the target. Format: 'column=value' (global) or 'table:column=value'."
              + " Can be specified multiple times.",
      arity = "0..*")
  List<String> fieldOverrides;

  @Override
  public Integer call() {
    try {
      var printer = parent.printer();
      var store = parent.configStore();
      var resolver = new ConnProfileResolver(store);
      var srcProfile = resolver.resolve(source);
      var masking = buildMaskingConfig();
      var overrides = buildFieldOverrideConfig();
      var resolved = resolveEntityAndId();
      var entityName = resolved[0];
      var rootId = resolved[1];

      if (export || target == null) {
        return performExport(printer, srcProfile, entityName, rootId, masking);
      }
      return performLiveClone(
          printer, resolver, srcProfile, entityName, rootId, masking, overrides);

    } catch (Exception e) {
      return EnvCommand.handleError(parent, e, ExitCode.CLONE_FAILED);
    }
  }

  private Integer performExport(
      io.recordrelay.cli.output.Printer printer,
      io.recordrelay.core.domain.ConnectionProfile srcProfile,
      String entityName,
      String rootId,
      MaskingConfig masking)
      throws Exception {
    var outDir = outputDir != null ? outputDir : Path.of(".");
    var fallbackTable = table != null ? table : entityName + "s";
    var entity =
        BuiltinEntityRegistry.INSTANCE
            .findByName(entityName)
            .orElseGet(
                () ->
                    io.recordrelay.core.clone.domain.BusinessEntity.of(entityName, fallbackTable));
    var bugReport = buildBugReport();
    var plan = ContextClonePlan.bugCapture(entity, rootId, srcProfile, outDir, masking, bugReport);
    printer.printLine(
        String.format("Exporting %s #%s from '%s'…", entity.displayName(), rootId, source));
    var contextEngine = DefaultContextCloneEngine.createDefault();
    var pkgPath = contextEngine.exportContext(plan);
    printer.printSuccess("Exported → " + pkgPath.toAbsolutePath());
    return ExitCode.SUCCESS;
  }

  private Integer performLiveClone(
      io.recordrelay.cli.output.Printer printer,
      ConnProfileResolver resolver,
      io.recordrelay.core.domain.ConnectionProfile srcProfile,
      String entityName,
      String rootId,
      MaskingConfig masking,
      FieldOverrideConfig overrides)
      throws Exception {
    var tgtProfile = resolver.resolve(target);
    printer.printLine(
        String.format(
            "Cloning %s #%s from '%s' → '%s' (depth=%d)",
            entityName, rootId, source, target, depth));

    CloneReport report;
    var registryEntity = BuiltinEntityRegistry.INSTANCE.findByName(entityName);
    if (registryEntity.isPresent()) {
      var plan =
          ContextClonePlan.liveCloneWithOverrides(
              registryEntity.get(), rootId, srcProfile, tgtProfile, depth, masking, overrides);
      report = DefaultContextCloneEngine.createDefault().cloneContext(plan, buildListener(printer));
    } else {
      var request =
          CloneRequest.builder(srcProfile, tgtProfile, table, id)
              .depth(depth)
              .masking(masking)
              .fieldOverrides(overrides)
              .build();
      report =
          DefaultCloneEngine.createDefault().clone(CloneJob.of(request), buildListener(printer));
    }

    printReport(report);
    return ExitCode.SUCCESS;
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  /** Returns [entityName, entityId] from semantic shortcuts or generic --table/--id. */
  private String[] resolveEntityAndId() {
    if (customerId != null) {
      return new String[] {"customer", customerId};
    }
    if (orderId != null) {
      return new String[] {"order", orderId};
    }
    if (userId != null) {
      return new String[] {"user", userId};
    }
    if (productId != null) {
      return new String[] {"product", productId};
    }
    if (invoiceId != null) {
      return new String[] {"invoice", invoiceId};
    }
    if (accountId != null) {
      return new String[] {"account", accountId};
    }
    if (table != null && id != null) {
      return new String[] {table, id};
    }
    throw new IllegalArgumentException(
        "Specify an entity (--entity <type> --id <value>), "
            + "a shortcut (--customer-id, --order-id, etc.), "
            + "or a table directly (--table <name> --id <value>)");
  }

  private BugReport buildBugReport() {
    if (bugTitle == null && bugId == null) {
      return null;
    }
    var id = bugId != null ? bugId : "UNKNOWN";
    var title = bugTitle != null ? bugTitle : "Bug reproduction";
    return BugReport.of(id, title, bugService, bugEnv, null);
  }

  private FieldOverrideConfig buildFieldOverrideConfig() {
    if (fieldOverrides == null || fieldOverrides.isEmpty()) {
      return FieldOverrideConfig.none();
    }
    var list = new ArrayList<FieldOverride>();
    for (var raw : fieldOverrides) {
      int colonIdx = raw.indexOf(':');
      int eqIdx = raw.indexOf('=');
      if (eqIdx < 0) {
        continue; // malformed — skip
      }
      if (colonIdx > 0 && colonIdx < eqIdx) {
        // table:column=value
        var tableName = raw.substring(0, colonIdx).trim();
        var column = raw.substring(colonIdx + 1, eqIdx).trim();
        var value = raw.substring(eqIdx + 1);
        list.add(FieldOverride.forTable(tableName, column, value));
      } else {
        // column=value (global)
        var column = raw.substring(0, eqIdx).trim();
        var value = raw.substring(eqIdx + 1);
        list.add(FieldOverride.global(column, value));
      }
    }
    return new FieldOverrideConfig(list);
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
      public void onTableExtractionCompleted(String tableName, long recordCount) {
        printer.printLine(Messages.get("clone.fetched", tableName, recordCount));
      }

      @Override
      public void onImportCompleted(String tableName, long recordCount) {
        printer.printLine(Messages.get("clone.written", tableName, recordCount));
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
