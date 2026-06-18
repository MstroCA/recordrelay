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

import static org.assertj.core.api.Assertions.assertThat;

import io.recordrelay.cli.ExitCode;
import io.recordrelay.cli.RecordRelayCli;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class ValidateCommandTest {

  @TempDir Path tempDir;

  @Test
  void validJsonMappingReturnsSuccess() throws Exception {
    var file = tempDir.resolve("mapping.json");
    Files.writeString(
        file,
        """
        {
          "id": "test-mapping",
          "source": {"table": "customers", "dbType": "POSTGRESQL"},
          "target": {"table": "users", "dbType": "MONGODB"}
        }
        """);
    int code = new CommandLine(new RecordRelayCli()).execute("validate", file.toString());
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void invalidJsonMappingReturnsValidationError() throws Exception {
    var file = tempDir.resolve("bad.json");
    Files.writeString(file, "{broken json}");
    int code = new CommandLine(new RecordRelayCli()).execute("validate", file.toString());
    assertThat(code).isEqualTo(ExitCode.VALIDATION_ERROR);
  }

  @Test
  void validYamlMappingReturnsSuccess() throws Exception {
    var file = tempDir.resolve("mapping.yaml");
    Files.writeString(
        file,
        """
        id: m-001
        source:
          table: orders
          dbType: POSTGRESQL
        target:
          table: orders_copy
          dbType: POSTGRESQL
        """);
    int code = new CommandLine(new RecordRelayCli()).execute("validate", file.toString());
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void missingMappingIdReturnsValidationError() throws Exception {
    var file = tempDir.resolve("noid.json");
    Files.writeString(
        file,
        """
        {
          "source": {"table": "t", "dbType": "POSTGRESQL"},
          "target": {"table": "u", "dbType": "POSTGRESQL"}
        }
        """);
    int code = new CommandLine(new RecordRelayCli()).execute("validate", file.toString());
    assertThat(code).isEqualTo(ExitCode.VALIDATION_ERROR);
  }

  @Test
  void detectFormatByExtension() {
    assertThat(ValidateCommand.detectFormat("file.json")).isEqualTo("json");
    assertThat(ValidateCommand.detectFormat("file.yaml")).isEqualTo("yaml");
    assertThat(ValidateCommand.detectFormat("file.yml")).isEqualTo("yaml");
    assertThat(ValidateCommand.detectFormat("file.sql")).isEqualTo("sql");
    assertThat(ValidateCommand.detectFormat("file.pipeline")).isEqualTo("nosql-query");
  }
}
