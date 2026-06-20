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
import io.recordrelay.core.clone.domain.CloneRequest;
import io.recordrelay.engine.clone.DefaultCloneEngine;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * {@code rr export} — extracts a root entity and its full relationship graph from an environment
 * and writes a portable {@code .rrpkg} reproduction package.
 *
 * <p>Examples:
 *
 * <pre>
 * rr export --entity customer --id 12345 --from staging --output ./exports
 * rr export --entity order --id 99 --from prod --output ./exports
 * </pre>
 */
@Command(
    name = "export",
    description =
        "Export an entity and its full context graph as a portable .rrpkg reproduction package.")
public final class ExportContextCommand implements Callable<Integer> {

  @ParentCommand private RecordRelayCli parent;

  @Option(
      names = {"--from", "-f"},
      required = true,
      description = "Environment connection profile name")
  String from;

  @Option(
      names = {"--entity", "-e"},
      required = true,
      description = "Entity type (e.g. customer, order, user)")
  String entity;

  @Option(
      names = {"--id"},
      required = true,
      description = "Root entity primary key value")
  String id;

  @Option(
      names = {"--depth"},
      description = "Relationship traversal depth (default: 3, max: 10)")
  int depth = CloneRequest.DEFAULT_DEPTH;

  @Option(
      names = {"--output", "-o"},
      required = true,
      description = "Output directory for the .rrpkg file")
  Path outputDir;

  @Override
  public Integer call() {
    try {
      var printer = parent.printer();
      var store = parent.configStore();
      var resolver = new ConnProfileResolver(store);
      var profile = resolver.resolve(from);

      var request = CloneRequest.builder(profile, profile, entity, id).depth(depth).build();
      var job = CloneJob.of(request);

      printer.printLine(
          String.format("Exporting context: %s #%s from '%s' → %s", entity, id, from, outputDir));

      var engine = DefaultCloneEngine.createDefault();
      var pkgPath = engine.exportPackage(job, outputDir);

      printer.printSuccess("Context exported: " + pkgPath.toAbsolutePath());
      return ExitCode.SUCCESS;
    } catch (Exception e) {
      return EnvCommand.handleError(parent, e, ExitCode.CLONE_FAILED);
    }
  }
}
