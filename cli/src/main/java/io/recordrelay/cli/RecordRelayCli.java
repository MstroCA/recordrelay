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
package io.recordrelay.cli;

import io.recordrelay.cli.command.AnalyzeCommand;
import io.recordrelay.cli.command.CloneCommand;
import io.recordrelay.cli.command.ConnCommand;
import io.recordrelay.cli.command.DiffCommand;
import io.recordrelay.cli.command.DiscoverCommand;
import io.recordrelay.cli.command.EnvCommand;
import io.recordrelay.cli.command.ExportPackageCommand;
import io.recordrelay.cli.command.ImportPackageCommand;
import io.recordrelay.cli.command.ReplayCommand;
import io.recordrelay.cli.command.StatusCommand;
import io.recordrelay.cli.config.ConfigStore;
import io.recordrelay.cli.output.OutputMode;
import io.recordrelay.cli.output.Printer;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** RecordRelay CLI root command. Global options (--json, --config) are inherited by subcommands. */
@Command(
    name = "rr",
    mixinStandardHelpOptions = true,
    version = "0.2.0-SNAPSHOT",
    description = {
      "RecordRelay — Universal Data Reproduction Platform",
      "",
      "Reproduce production issues locally in under 5 minutes.",
      "",
      "Quick start:",
      "  rr clone --customer-id 123 --source prod --target local",
      "  rr clone --order-id 987654 --source prod --export --bug-title \"Price bug\"",
      "  rr replay bug-1234.rrpkg --target local",
    },
    subcommands = {
      CommandLine.HelpCommand.class,
      // ── Reproduction commands (primary) ──
      CloneCommand.class,
      ReplayCommand.class,
      ExportPackageCommand.class,
      ImportPackageCommand.class,
      DiffCommand.class,
      // ── Environment management ──
      EnvCommand.class,
      ConnCommand.class,
      StatusCommand.class,
      // ── Discovery & analysis (secondary) ──
      DiscoverCommand.class,
      AnalyzeCommand.class
    })
public final class RecordRelayCli implements Callable<Integer> {

  @Option(
      names = {"--json"},
      description = "Emit machine-readable JSON output (for scripts and CI)")
  boolean jsonOutput;

  @Option(
      names = {"--config"},
      description = "Override the config directory (default: ~/.recordrelay)")
  Path configDir;

  /** CLI entry point. */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new RecordRelayCli()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() {
    new CommandLine(this).usage(System.out);
    return ExitCode.SUCCESS;
  }

  /** Returns a {@link Printer} configured from the global {@code --json} flag. */
  public Printer printer() {
    return new Printer(jsonOutput ? OutputMode.JSON : OutputMode.HUMAN);
  }

  /**
   * Returns a {@link ConfigStore} backed by the directory from {@code --config} or the default
   * {@code ~/.recordrelay}.
   */
  public ConfigStore configStore() {
    try {
      if (configDir != null) {
        return new ConfigStore(configDir);
      }
      return new ConfigStore();
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialise config store: " + e.getMessage(), e);
    }
  }
}
