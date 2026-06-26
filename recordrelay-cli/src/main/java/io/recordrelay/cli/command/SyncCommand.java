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
import io.recordrelay.cli.config.ScheduledSync;
import io.recordrelay.cli.config.SyncStore;
import io.recordrelay.cli.engine.ConnProfileResolver;
import io.recordrelay.cli.engine.WebhookNotifier;
import io.recordrelay.core.clone.domain.BusinessEntity;
import io.recordrelay.core.clone.domain.ContextClonePlan;
import io.recordrelay.core.clone.domain.MaskerType;
import io.recordrelay.core.clone.domain.MaskingConfig;
import io.recordrelay.core.clone.domain.MaskingRule;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.engine.clone.BuiltinEntityRegistry;
import io.recordrelay.engine.clone.DefaultContextCloneEngine;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * {@code rr sync} — manage and run scheduled clone configurations.
 *
 * <p>Usage:
 *
 * <pre>
 * rr sync add daily-customer --entity customer --id 123 --from prod --target local --every 60
 * rr sync list
 * rr sync run daily-customer
 * rr sync remove daily-customer
 * </pre>
 */
@Command(
    name = "sync",
    description = "Manage and run scheduled clone configurations.",
    subcommands = {
      SyncCommand.AddCommand.class,
      SyncCommand.ListCommand.class,
      SyncCommand.RunCommand.class,
      SyncCommand.RemoveCommand.class
    })
public final class SyncCommand implements Callable<Integer> {

  @ParentCommand RecordRelayCli parent;

  @Override
  public Integer call() {
    new CommandLine(this).usage(System.out);
    return ExitCode.SUCCESS;
  }

  // ── rr sync add ───────────────────────────────────────────────────────────

  @Command(name = "add", description = "Add or replace a scheduled sync entry.")
  static final class AddCommand implements Callable<Integer> {

    @ParentCommand SyncCommand sync;

    @Parameters(index = "0", description = "Sync name (e.g. daily-customer)")
    String name;

    @Option(names = "--entity", required = true, description = "Entity name (customer, order, …)")
    String entityName;

    @Option(names = "--id", required = true, description = "Root entity primary key value")
    String entityId;

    @Option(names = "--from", required = true, description = "Source connection profile name")
    String sourceConn;

    @Option(names = "--target", required = true, description = "Target connection profile name")
    String targetConn;

    @Option(names = "--depth", description = "Traversal depth (default: 3)")
    int depth = 3;

    @Option(names = "--mask", description = "Mask PII columns")
    boolean maskPii;

    @Option(
        names = "--cron",
        description = "5-field cron expression (e.g. \"0 8 * * *\" for daily at 08:00)")
    String cronExpression;

    @Option(names = "--every", description = "Run every N minutes (alternative to --cron)")
    Integer intervalMinutes;

    @Option(
        names = "--webhook",
        description = "POST a JSON notification to this URL after each run (Slack, Teams, custom)")
    String webhookUrl;

    @Override
    public Integer call() {
      try {
        var entry = new ScheduledSync();
        entry.setName(name);
        entry.setEntityName(entityName);
        entry.setEntityId(entityId);
        entry.setSourceConn(sourceConn);
        entry.setTargetConn(targetConn);
        entry.setDepth(depth);
        entry.setMaskPii(maskPii);
        entry.setCronExpression(cronExpression);
        entry.setIntervalMinutes(intervalMinutes);
        entry.setWebhookUrl(webhookUrl);

        new SyncStore().addOrReplace(entry);
        sync.parent.printer().printSuccess("Sync '" + name + "' saved.");
        return ExitCode.SUCCESS;
      } catch (Exception e) {
        return EnvCommand.handleError(sync.parent, e, ExitCode.CONFIG_ERROR);
      }
    }
  }

  // ── rr sync list ──────────────────────────────────────────────────────────

  @Command(name = "list", description = "List all scheduled sync entries.")
  static final class ListCommand implements Callable<Integer> {

    @ParentCommand SyncCommand sync;

    @Override
    public Integer call() {
      try {
        var all = new SyncStore().loadAll();
        if (all.isEmpty()) {
          sync.parent.printer().printLine("No scheduled syncs configured.");
          return ExitCode.SUCCESS;
        }
        var rows = new ArrayList<List<String>>();
        for (var s : all) {
          rows.add(
              List.of(
                  s.getName(),
                  s.getEntityName() + " #" + s.getEntityId(),
                  s.getSourceConn() + " → " + s.getTargetConn(),
                  "depth=" + s.getDepth(),
                  s.scheduleLabel(),
                  s.getLastRunAt() != null ? s.getLastRunAt() : "—",
                  s.getLastStatus()));
        }
        sync.parent
            .printer()
            .printTable(
                List.of("NAME", "ENTITY", "CONNECTIONS", "OPTS", "SCHEDULE", "LAST RUN", "STATUS"),
                rows);
        return ExitCode.SUCCESS;
      } catch (Exception e) {
        return EnvCommand.handleError(sync.parent, e, ExitCode.CONFIG_ERROR);
      }
    }
  }

  // ── rr sync run ───────────────────────────────────────────────────────────

  @Command(name = "run", description = "Run a scheduled sync immediately.")
  static final class RunCommand implements Callable<Integer> {

    @ParentCommand SyncCommand sync;

    @Parameters(index = "0", description = "Sync name")
    String name;

    @Override
    public Integer call() {
      try {
        var store = new SyncStore();
        var entry =
            store
                .findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Sync '" + name + "' not found."));
        return executeSync(store, entry);
      } catch (Exception e) {
        markFailed(name, e);
        return EnvCommand.handleError(sync.parent, e, ExitCode.CLONE_FAILED);
      }
    }

    private Integer executeSync(SyncStore store, ScheduledSync entry) throws Exception {
      var printer = sync.parent.printer();
      printer.printLine(
          String.format(
              "Running sync '%s': %s #%s  %s → %s  depth=%d",
              entry.getName(),
              entry.getEntityName(),
              entry.getEntityId(),
              entry.getSourceConn(),
              entry.getTargetConn(),
              entry.getDepth()));

      var resolver = new ConnProfileResolver(sync.parent.configStore());
      var srcProfile = resolver.resolve(entry.getSourceConn());
      var tgtProfile = resolver.resolve(entry.getTargetConn());
      var plan =
          ContextClonePlan.liveClone(
              resolveEntity(entry),
              entry.getEntityId(),
              srcProfile,
              tgtProfile,
              entry.getDepth(),
              buildMasking(entry));

      var report =
          DefaultContextCloneEngine.createDefault()
              .cloneContext(
                  plan,
                  new CloneProgressListener() {
                    @Override
                    public void onTableExtractionCompleted(String tableName, long recordCount) {
                      try {
                        printer.printLine("  Fetched " + tableName + ": " + recordCount + " rows");
                      } catch (Exception ignored) {
                      }
                    }

                    @Override
                    public void onWarning(String message) {
                      try {
                        printer.printLine("  WARN: " + message);
                      } catch (Exception ignored) {
                      }
                    }
                  });

      store.updateRunResult(entry.getName(), Instant.now().toString(), "OK");
      WebhookNotifier.notify(
          entry.getWebhookUrl(),
          WebhookNotifier.WebhookPayload.success(
              "sync.completed",
              entry.getEntityName(),
              entry.getEntityId(),
              entry.getSourceConn(),
              entry.getTargetConn(),
              report.totalRecords(),
              report.durationMillis()));

      printer.printLine("");
      printer.printSuccess(
          String.format(
              "Sync '%s' complete — %d records in %s",
              entry.getName(), report.totalRecords(), report.formattedDuration()));
      return ExitCode.SUCCESS;
    }

    private void markFailed(String syncName, Exception cause) {
      try {
        new SyncStore().updateRunResult(syncName, Instant.now().toString(), "FAILED");
        new SyncStore()
            .findByName(syncName)
            .ifPresent(
                fe ->
                    WebhookNotifier.notify(
                        fe.getWebhookUrl(),
                        WebhookNotifier.WebhookPayload.failure(
                            "sync.failed",
                            fe.getEntityName(),
                            fe.getEntityId(),
                            fe.getSourceConn(),
                            fe.getTargetConn(),
                            cause.getMessage())));
      } catch (Exception ignored) {
      }
    }

    private BusinessEntity resolveEntity(ScheduledSync entry) {
      return BuiltinEntityRegistry.INSTANCE
          .findByName(entry.getEntityName())
          .orElseGet(
              () ->
                  BusinessEntity.of(entry.getEntityName(), entry.getEntityName() + "s", "id", ""));
    }

    private MaskingConfig buildMasking(ScheduledSync entry) {
      if (!entry.isMaskPii()) {
        return MaskingConfig.none();
      }
      return new MaskingConfig(
          List.of(
              new MaskingRule("email", MaskerType.EMAIL),
              new MaskingRule("phone", MaskerType.PHONE),
              new MaskingRule("phone_number", MaskerType.PHONE),
              new MaskingRule("address", MaskerType.ADDRESS),
              new MaskingRule("national_id", MaskerType.NATIONAL_ID),
              new MaskingRule("iban", MaskerType.IBAN)));
    }
  }

  // ── rr sync remove ────────────────────────────────────────────────────────

  @Command(name = "remove", description = "Remove a scheduled sync entry.")
  static final class RemoveCommand implements Callable<Integer> {

    @ParentCommand SyncCommand sync;

    @Parameters(index = "0", description = "Sync name")
    String name;

    @Override
    public Integer call() {
      try {
        boolean removed = new SyncStore().remove(name);
        if (removed) {
          sync.parent.printer().printSuccess("Sync '" + name + "' removed.");
        } else {
          sync.parent.printer().printError("Sync '" + name + "' not found.");
        }
        return removed ? ExitCode.SUCCESS : ExitCode.CONFIG_ERROR;
      } catch (Exception e) {
        return EnvCommand.handleError(sync.parent, e, ExitCode.CONFIG_ERROR);
      }
    }
  }
}
