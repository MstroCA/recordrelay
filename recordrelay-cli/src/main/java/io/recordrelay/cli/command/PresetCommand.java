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
import io.recordrelay.cli.config.ClonePreset;
import io.recordrelay.cli.config.PresetStore;
import io.recordrelay.cli.engine.ConnProfileResolver;
import io.recordrelay.core.clone.domain.BusinessEntity;
import io.recordrelay.core.clone.domain.ContextClonePlan;
import io.recordrelay.core.clone.domain.FieldOverride;
import io.recordrelay.core.clone.domain.FieldOverrideConfig;
import io.recordrelay.core.clone.domain.MaskerType;
import io.recordrelay.core.clone.domain.MaskingConfig;
import io.recordrelay.core.clone.domain.MaskingRule;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.engine.clone.BuiltinEntityRegistry;
import io.recordrelay.engine.clone.DefaultContextCloneEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * {@code rr preset} — save and run named clone configurations.
 *
 * <pre>
 * rr preset save prod-customer --entity customer --id 123 --from prod --target local
 * rr preset list
 * rr preset run prod-customer
 * rr preset remove prod-customer
 * </pre>
 */
@Command(
    name = "preset",
    description = "Save and run named clone configurations (bookmarks).",
    subcommands = {
      PresetCommand.SaveCommand.class,
      PresetCommand.ListCommand.class,
      PresetCommand.RunCommand.class,
      PresetCommand.RemoveCommand.class
    })
public final class PresetCommand implements Callable<Integer> {

  @ParentCommand RecordRelayCli parent;

  @Override
  public Integer call() {
    new CommandLine(this).usage(System.out);
    return ExitCode.SUCCESS;
  }

  // ── rr preset save ────────────────────────────────────────────────────────

  @Command(name = "save", description = "Save a clone configuration as a named preset.")
  static final class SaveCommand implements Callable<Integer> {

    @ParentCommand PresetCommand preset;

    @Parameters(index = "0", description = "Preset name")
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

    @Option(names = "--desc", description = "Optional description shown in preset list")
    String description;

    @Option(
        names = {"--override", "-o"},
        description = "Field overrides (column=value or table:column=value). Repeatable.",
        arity = "0..*")
    List<String> fieldOverrides;

    @Override
    public Integer call() {
      try {
        var p = new ClonePreset();
        p.setName(name);
        p.setEntityName(entityName);
        p.setEntityId(entityId);
        p.setSourceConn(sourceConn);
        p.setTargetConn(targetConn);
        p.setDepth(depth);
        p.setMaskPii(maskPii);
        p.setDescription(description);
        p.setFieldOverrides(fieldOverrides);
        new PresetStore().addOrReplace(p);
        preset.parent.printer().printSuccess("Preset '" + name + "' saved.");
        return ExitCode.SUCCESS;
      } catch (Exception e) {
        return EnvCommand.handleError(preset.parent, e, ExitCode.CONFIG_ERROR);
      }
    }
  }

  // ── rr preset list ────────────────────────────────────────────────────────

  @Command(name = "list", description = "List all saved presets.")
  static final class ListCommand implements Callable<Integer> {

    @ParentCommand PresetCommand preset;

    @Override
    public Integer call() {
      try {
        var all = new PresetStore().loadAll();
        if (all.isEmpty()) {
          preset.parent.printer().printLine("No presets saved yet.");
          return ExitCode.SUCCESS;
        }
        var rows = new ArrayList<List<String>>();
        for (var p : all) {
          rows.add(
              List.of(
                  p.getName(),
                  p.getEntityName() + " #" + p.getEntityId(),
                  p.getSourceConn() + " → " + p.getTargetConn(),
                  "depth=" + p.getDepth() + (p.isMaskPii() ? " +mask" : ""),
                  p.getDescription() != null ? p.getDescription() : ""));
        }
        preset
            .parent
            .printer()
            .printTable(List.of("NAME", "ENTITY", "CONNECTIONS", "OPTIONS", "DESCRIPTION"), rows);
        return ExitCode.SUCCESS;
      } catch (Exception e) {
        return EnvCommand.handleError(preset.parent, e, ExitCode.CONFIG_ERROR);
      }
    }
  }

  // ── rr preset run ─────────────────────────────────────────────────────────

  @Command(name = "run", description = "Run a saved preset immediately.")
  static final class RunCommand implements Callable<Integer> {

    @ParentCommand PresetCommand preset;

    @Parameters(index = "0", description = "Preset name")
    String name;

    @Override
    public Integer call() {
      try {
        var p =
            new PresetStore()
                .findByName(name)
                .orElseThrow(
                    () -> new IllegalArgumentException("Preset '" + name + "' not found."));

        var printer = preset.parent.printer();
        printer.printLine(
            String.format(
                "Running preset '%s': %s #%s  %s → %s  depth=%d%s",
                p.getName(),
                p.getEntityName(),
                p.getEntityId(),
                p.getSourceConn(),
                p.getTargetConn(),
                p.getDepth(),
                p.isMaskPii() ? "  (PII masked)" : ""));

        var connStore = preset.parent.configStore();
        var resolver = new ConnProfileResolver(connStore);
        var srcProfile = resolver.resolve(p.getSourceConn());
        var tgtProfile = resolver.resolve(p.getTargetConn());
        var entity = resolveEntity(p);
        var masking = buildMasking(p);
        var overrides = buildOverrides(p);

        var plan =
            ContextClonePlan.liveCloneWithOverrides(
                entity, p.getEntityId(), srcProfile, tgtProfile, p.getDepth(), masking, overrides);

        var report =
            DefaultContextCloneEngine.createDefault()
                .cloneContext(
                    plan,
                    new CloneProgressListener() {
                      @Override
                      public void onTableExtractionCompleted(String tableName, long recordCount) {
                        try {
                          printer.printLine("  Fetched " + tableName + ": " + recordCount);
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

        printer.printLine("");
        printer.printSuccess(
            String.format(
                "Preset '%s' complete — %d records in %s",
                name, report.totalRecords(), report.formattedDuration()));
        return ExitCode.SUCCESS;
      } catch (Exception e) {
        return EnvCommand.handleError(preset.parent, e, ExitCode.CLONE_FAILED);
      }
    }

    private BusinessEntity resolveEntity(ClonePreset p) {
      return BuiltinEntityRegistry.INSTANCE
          .findByName(p.getEntityName())
          .orElseGet(() -> BusinessEntity.of(p.getEntityName(), p.getEntityName() + "s", "id", ""));
    }

    private MaskingConfig buildMasking(ClonePreset p) {
      if (!p.isMaskPii()) return MaskingConfig.none();
      return new MaskingConfig(
          List.of(
              new MaskingRule("email", MaskerType.EMAIL),
              new MaskingRule("phone", MaskerType.PHONE),
              new MaskingRule("phone_number", MaskerType.PHONE),
              new MaskingRule("address", MaskerType.ADDRESS),
              new MaskingRule("national_id", MaskerType.NATIONAL_ID),
              new MaskingRule("iban", MaskerType.IBAN)));
    }

    private FieldOverrideConfig buildOverrides(ClonePreset p) {
      if (p.getFieldOverrides() == null || p.getFieldOverrides().isEmpty()) {
        return FieldOverrideConfig.none();
      }
      var list = new ArrayList<FieldOverride>();
      for (var raw : p.getFieldOverrides()) {
        int colonIdx = raw.indexOf(':');
        int eqIdx = raw.indexOf('=');
        if (eqIdx < 0) continue;
        if (colonIdx > 0 && colonIdx < eqIdx) {
          list.add(
              FieldOverride.forTable(
                  raw.substring(0, colonIdx).trim(),
                  raw.substring(colonIdx + 1, eqIdx).trim(),
                  raw.substring(eqIdx + 1)));
        } else {
          list.add(FieldOverride.global(raw.substring(0, eqIdx).trim(), raw.substring(eqIdx + 1)));
        }
      }
      return new FieldOverrideConfig(list);
    }
  }

  // ── rr preset remove ──────────────────────────────────────────────────────

  @Command(name = "remove", description = "Remove a saved preset.")
  static final class RemoveCommand implements Callable<Integer> {

    @ParentCommand PresetCommand preset;

    @Parameters(index = "0", description = "Preset name")
    String name;

    @Override
    public Integer call() {
      try {
        boolean removed = new PresetStore().remove(name);
        if (removed) {
          preset.parent.printer().printSuccess("Preset '" + name + "' removed.");
        } else {
          preset.parent.printer().printError("Preset '" + name + "' not found.");
        }
        return removed ? ExitCode.SUCCESS : ExitCode.CONFIG_ERROR;
      } catch (Exception e) {
        return EnvCommand.handleError(preset.parent, e, ExitCode.CONFIG_ERROR);
      }
    }
  }
}
