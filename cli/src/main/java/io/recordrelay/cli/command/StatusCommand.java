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
import io.recordrelay.core.spi.ConnectorRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * Reports the current system status: registered connectors and saved job templates.
 *
 * <p>Transfer jobs in this phase run synchronously in-process and have no persistent runtime state.
 * This command shows configuration-level status rather than live execution monitoring.
 */
@Command(name = "status", description = "Show registered connectors and saved job templates.")
public final class StatusCommand implements Callable<Integer> {

  @ParentCommand private RecordRelayCli parent;

  @Override
  public Integer call() {
    try {
      printConnectors();
      printJobs();
      return ExitCode.SUCCESS;
    } catch (Exception e) {
      return EnvCommand.handleError(parent, e, ExitCode.CONFIG_ERROR);
    }
  }

  private void printConnectors() throws Exception {
    parent.printer().printLine("=== Registered Connectors ===");
    var connectors = ConnectorRegistry.allConnectors();
    if (connectors.isEmpty()) {
      parent.printer().printLine("(none)");
      return;
    }
    var rows = new ArrayList<List<String>>();
    for (var c : connectors) {
      rows.add(List.of(c.connectorId(), c.getClass().getSimpleName()));
    }
    parent.printer().printTable(List.of("CONNECTOR ID", "CLASS"), rows);
  }

  private void printJobs() throws Exception {
    parent.printer().printLine("=== Saved Job Templates ===");
    var config = parent.configStore().load();
    if (config.getJobs().isEmpty()) {
      parent.printer().printLine("(none)");
      return;
    }
    var rows = new ArrayList<List<String>>();
    config
        .getJobs()
        .forEach(
            (name, job) ->
                rows.add(
                    List.of(
                        name,
                        job.getSource() != null ? job.getSource() : "",
                        job.getTarget() != null ? job.getTarget() : "",
                        job.getMode() != null ? job.getMode() : "sync",
                        job.getMappingFile() != null ? job.getMappingFile() : "")));
    parent.printer().printTable(List.of("NAME", "SOURCE", "TARGET", "MODE", "MAPPING"), rows);
  }
}
