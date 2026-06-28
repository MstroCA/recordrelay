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

class CloneCommandTest {

  @TempDir Path tempDir;
  CommandLine cli;

  @BeforeEach
  void setUp() {
    cli = new CommandLine(new RecordRelayCli());
  }

  @Test
  void cloneWithMissingRequiredOptionsReturnsUsageError() {
    // Missing --entity and --id and --from
    int code = cli.execute("--config", tempDir.toString(), "clone");
    assertThat(code).isEqualTo(ExitCode.VALIDATION_ERROR);
  }

  @Test
  void cloneWithMissingIdReturnsError() {
    // --id is not picocli-required; missing id is caught at runtime, not parse time
    int code =
        cli.execute(
            "--config", tempDir.toString(), "clone", "--entity", "customer", "--from", "prod");
    assertThat(code).isNotEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void cloneWithUnknownConnectionReturnsError() {
    // Valid syntax but connection doesn't exist → engine error
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "clone",
            "--entity",
            "customer",
            "--id",
            "42",
            "--from",
            "no-such-conn",
            "--target",
            "local");
    assertThat(code).isNotEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void cloneWithDryRunFlagAndUnknownConnectionReturnsError() {
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "clone",
            "--entity",
            "order",
            "--id",
            "99",
            "--from",
            "nonexistent",
            "--dry-run");
    assertThat(code).isNotEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void cloneWithAtFlagValidIsoInstant() {
    // Valid --at value; will fail at connection resolution (no conn exists), not at parsing
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "clone",
            "--entity",
            "order",
            "--id",
            "1",
            "--from",
            "nonexistent",
            "--at",
            "2026-01-01T00:00:00Z");
    // Should fail at conn resolution, NOT at argument parsing
    assertThat(code).isNotEqualTo(ExitCode.VALIDATION_ERROR);
  }

  @Test
  void cloneWithAtFlagLocalDateTimeFormat() {
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "clone",
            "--entity",
            "order",
            "--id",
            "2",
            "--from",
            "nonexistent",
            "--at",
            "2026-06-01T10:00:00");
    assertThat(code).isNotEqualTo(ExitCode.VALIDATION_ERROR);
  }

  @Test
  void cloneWithMultipleAlsoEntityOptions() {
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "clone",
            "--entity",
            "customer",
            "--id",
            "1",
            "--from",
            "nonexistent",
            "--also-entity",
            "account",
            "--also-id",
            "10",
            "--also-entity",
            "contract",
            "--also-id",
            "20");
    assertThat(code).isNotEqualTo(ExitCode.VALIDATION_ERROR);
  }

  @Test
  void cloneWithExportFlagAndBugTitle() {
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "clone",
            "--entity",
            "customer",
            "--id",
            "1",
            "--from",
            "nonexistent",
            "--export",
            "--bug-title",
            "NPE in checkout");
    assertThat(code).isNotEqualTo(ExitCode.VALIDATION_ERROR);
  }

  @Test
  void cloneWithDepthOption() {
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "clone",
            "--entity",
            "customer",
            "--id",
            "5",
            "--from",
            "nonexistent",
            "--depth",
            "5");
    assertThat(code).isNotEqualTo(ExitCode.VALIDATION_ERROR);
  }

  @Test
  void cloneWithMaskPiiFlag() {
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "clone",
            "--entity",
            "user",
            "--id",
            "3",
            "--from",
            "nonexistent",
            "--target",
            "local",
            "--mask");
    assertThat(code).isNotEqualTo(ExitCode.VALIDATION_ERROR);
  }

  @Test
  void cloneWithFieldOverrideOption() {
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "clone",
            "--entity",
            "order",
            "--id",
            "7",
            "--from",
            "nonexistent",
            "--target",
            "local",
            "-o",
            "status=pending",
            "-o",
            "orders:amount=0.00");
    assertThat(code).isNotEqualTo(ExitCode.VALIDATION_ERROR);
  }

  @Test
  void cloneWithJsonOutputFlag() {
    int code =
        cli.execute(
            "--json",
            "--config",
            tempDir.toString(),
            "clone",
            "--entity",
            "customer",
            "--id",
            "1",
            "--from",
            "nonexistent");
    assertThat(code).isNotEqualTo(ExitCode.VALIDATION_ERROR);
  }
}
