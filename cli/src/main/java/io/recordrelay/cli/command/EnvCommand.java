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
import io.recordrelay.cli.config.EnvironmentEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/** Manages RecordRelay environments (logical groupings for connections). */
@Command(
    name = "env",
    description = "Manage environments.",
    subcommands = {
      EnvCommand.AddCommand.class,
      EnvCommand.ListCommand.class,
      EnvCommand.RemoveCommand.class
    })
public final class EnvCommand implements Callable<Integer> {

  @ParentCommand RecordRelayCli parent;

  @Override
  public Integer call() {
    new CommandLine(this).usage(System.out);
    return ExitCode.SUCCESS;
  }

  @Command(name = "add", description = "Add a new environment.")
  static final class AddCommand implements Callable<Integer> {

    @ParentCommand EnvCommand env;

    @Parameters(index = "0", description = "Environment name")
    String name;

    @Option(names = "--desc", description = "Description")
    String description = "";

    @Override
    public Integer call() {
      try {
        var store = env.parent.configStore();
        store.addEnvironment(name, new EnvironmentEntry(name, description));
        env.parent.printer().printSuccess("Environment '" + name + "' added.");
        return ExitCode.SUCCESS;
      } catch (Exception e) {
        return handleError(env.parent, e, ExitCode.CONFIG_ERROR);
      }
    }
  }

  @Command(name = "list", description = "List all environments.")
  static final class ListCommand implements Callable<Integer> {

    @ParentCommand EnvCommand env;

    @Override
    public Integer call() {
      try {
        var config = env.parent.configStore().load();
        var rows = new ArrayList<List<String>>();
        config
            .getEnvironments()
            .forEach(
                (n, e) ->
                    rows.add(List.of(n, e.getDescription() != null ? e.getDescription() : "")));
        env.parent.printer().printTable(List.of("NAME", "DESCRIPTION"), rows);
        return ExitCode.SUCCESS;
      } catch (Exception e) {
        return handleError(env.parent, e, ExitCode.CONFIG_ERROR);
      }
    }
  }

  @Command(name = "remove", description = "Remove an environment.")
  static final class RemoveCommand implements Callable<Integer> {

    @ParentCommand EnvCommand env;

    @Parameters(index = "0", description = "Environment name")
    String name;

    @Override
    public Integer call() {
      try {
        boolean removed = env.parent.configStore().removeEnvironment(name);
        if (removed) {
          env.parent.printer().printSuccess("Environment '" + name + "' removed.");
        } else {
          env.parent.printer().printError("Environment '" + name + "' not found.");
        }
        return removed ? ExitCode.SUCCESS : ExitCode.CONFIG_ERROR;
      } catch (Exception e) {
        return handleError(env.parent, e, ExitCode.CONFIG_ERROR);
      }
    }
  }

  static int handleError(RecordRelayCli parent, Exception e, int code) {
    try {
      parent.printer().printError(e.getMessage());
    } catch (Exception ignored) {
      System.err.println("[ERROR] " + e.getMessage());
    }
    return code;
  }
}
