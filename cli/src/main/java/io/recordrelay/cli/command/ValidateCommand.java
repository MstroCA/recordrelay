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
import io.recordrelay.core.domain.ValidationSeverity;
import io.recordrelay.core.spi.MappingParserRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/** Validates a mapping file without executing a transfer. */
@Command(name = "validate", description = "Validate a mapping file (JSON, YAML, SQL, NoSQL).")
public final class ValidateCommand implements Callable<Integer> {

  @ParentCommand private RecordRelayCli parent;

  @Parameters(index = "0", description = "Path to the mapping file")
  Path mappingFile;

  @Override
  public Integer call() {
    try {
      String content = Files.readString(mappingFile);
      String formatId = detectFormat(mappingFile.toString());
      var parserOpt = MappingParserRegistry.find(formatId);
      if (parserOpt.isEmpty()) {
        parent.printer().printError("Unknown mapping format for file: " + mappingFile);
        return ExitCode.VALIDATION_ERROR;
      }
      var result = parserOpt.get().validate(content);
      if (result.valid()) {
        parent.printer().printSuccess("Mapping file is valid.");
        return ExitCode.SUCCESS;
      }
      return reportErrors(result);
    } catch (Exception e) {
      return EnvCommand.handleError(parent, e, ExitCode.VALIDATION_ERROR);
    }
  }

  private int reportErrors(io.recordrelay.core.domain.ValidationResult result) throws Exception {
    var rows = new ArrayList<List<String>>();
    for (var error : result.errors()) {
      String location = error.hasSyntaxLocation() ? error.line() + ":" + error.column() : "-";
      rows.add(
          List.of(
              error.severity().name(),
              location,
              error.field() != null ? error.field() : "",
              error.message()));
    }
    parent.printer().printTable(List.of("SEVERITY", "LINE:COL", "FIELD", "MESSAGE"), rows);
    boolean hasErrors =
        result.errors().stream().anyMatch(e -> e.severity() == ValidationSeverity.ERROR);
    return hasErrors ? ExitCode.VALIDATION_ERROR : ExitCode.SUCCESS;
  }

  static String detectFormat(String filename) {
    String lower = filename.toLowerCase();
    if (lower.endsWith(".json")) {
      return "json";
    }
    if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
      return "yaml";
    }
    if (lower.endsWith(".sql")) {
      return "sql";
    }
    return "nosql-query";
  }
}
