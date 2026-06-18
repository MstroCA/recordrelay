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
 * {@code rr export-package} — extracts a root record and all related data from the source and
 * writes a portable {@code .rrpkg} archive.
 *
 * <p>Example:
 *
 * <pre>
 * rr export-package --source staging --table customer --id 12345 --output ./exports
 * </pre>
 */
@Command(
    name = "export-package",
    description = "Export a root record and its dependencies as a portable .rrpkg archive.")
public final class ExportPackageCommand implements Callable<Integer> {

  @ParentCommand private RecordRelayCli parent;

  @Option(
      names = {"--source", "-s"},
      required = true,
      description = "Source connection profile name")
  String source;

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
      var srcProfile = resolver.resolve(source);

      // Export does not require a real target — use source as placeholder target
      var request = CloneRequest.builder(srcProfile, srcProfile, table, id).depth(depth).build();
      var job = CloneJob.of(request);

      printer.printLine(
          String.format("Exporting %s:%s from '%s' to %s", table, id, source, outputDir));

      var engine = DefaultCloneEngine.createDefault();
      var pkgPath = engine.exportPackage(job, outputDir);

      printer.printSuccess("Package exported: " + pkgPath.toAbsolutePath());
      return ExitCode.SUCCESS;
    } catch (Exception e) {
      return EnvCommand.handleError(parent, e, ExitCode.CLONE_FAILED);
    }
  }
}
