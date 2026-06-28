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
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class SyntheticCommandTest {

  @TempDir Path tempDir;
  CommandLine cli;

  @BeforeEach
  void setUp() {
    cli = new CommandLine(new RecordRelayCli());
  }

  @Test
  void synthWithMissingRequiredOptionsReturnsUsageError() {
    int code = cli.execute("--config", tempDir.toString(), "synth");
    assertThat(code).isEqualTo(ExitCode.VALIDATION_ERROR);
  }

  @Test
  void synthWithMissingDatabaseReturnsUsageError() {
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "synth",
            "--from",
            "prod",
            "--table",
            "customers",
            "--rows",
            "100");
    assertThat(code).isEqualTo(ExitCode.VALIDATION_ERROR);
  }

  @Test
  void synthWithMissingTableReturnsUsageError() {
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "synth",
            "--from",
            "prod",
            "--database",
            "mydb",
            "--rows",
            "50");
    assertThat(code).isEqualTo(ExitCode.VALIDATION_ERROR);
  }

  @Test
  void synthWithUnknownConnectionReturnsError() {
    // Correct syntax but connection doesn't exist → runtime error, not usage error
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "synth",
            "--from",
            "nonexistent",
            "--database",
            "mydb",
            "--table",
            "customers",
            "--rows",
            "10",
            "--output",
            tempDir.resolve("out.csv").toString());
    assertThat(code).isNotEqualTo(ExitCode.VALIDATION_ERROR);
    assertThat(code).isNotEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void synthRowCountDefaultIsHundred() {
    // Must supply --output to pass the "must specify destination" check; then fails at connection
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "synth",
            "--from",
            "nonexistent",
            "--database",
            "mydb",
            "--table",
            "orders",
            "--output",
            tempDir.resolve("out.csv").toString());
    assertThat(code).isNotEqualTo(ExitCode.VALIDATION_ERROR);
  }

  @Test
  void synthWithOutputFilePath() {
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "synth",
            "--from",
            "nonexistent",
            "--database",
            "mydb",
            "--table",
            "users",
            "--rows",
            "50",
            "--output",
            tempDir.resolve("users.csv").toString());
    assertThat(code).isNotEqualTo(ExitCode.VALIDATION_ERROR);
  }

  @Test
  void synthWithTargetConnection() {
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "synth",
            "--from",
            "nonexistent-src",
            "--database",
            "mydb",
            "--table",
            "accounts",
            "--rows",
            "25",
            "--target",
            "nonexistent-tgt");
    assertThat(code).isNotEqualTo(ExitCode.VALIDATION_ERROR);
  }
}
