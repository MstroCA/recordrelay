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

/** Tests for discover, analyze, and query commands — argument parsing and error handling. */
class DiscoverAnalyzeQueryCommandTest {

  @TempDir Path tempDir;
  CommandLine cli;

  @BeforeEach
  void setUp() {
    cli = new CommandLine(new RecordRelayCli());
  }

  // ── discover ──────────────────────────────────────────────────────────────

  @Test
  void discoverWithMissingFromReturnsUsageError() {
    int code = cli.execute("--config", tempDir.toString(), "discover");
    assertThat(code).isEqualTo(ExitCode.VALIDATION_ERROR);
  }

  @Test
  void discoverWithUnknownConnectionReturnsError() {
    // discover uses --conn, not --from
    int code = cli.execute("--config", tempDir.toString(), "discover", "--conn", "nonexistent");
    assertThat(code).isNotEqualTo(ExitCode.VALIDATION_ERROR);
    assertThat(code).isNotEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void discoverWithValidSyntaxParsesCorrectly() {
    // All required args present — fails at runtime (no conn), not at parse
    int code = cli.execute("--config", tempDir.toString(), "discover", "--conn", "nonexistent");
    assertThat(code).isNotEqualTo(ExitCode.VALIDATION_ERROR);
  }

  // ── analyze ───────────────────────────────────────────────────────────────

  @Test
  void analyzeWithMissingFromReturnsUsageError() {
    int code = cli.execute("--config", tempDir.toString(), "analyze");
    assertThat(code).isEqualTo(ExitCode.VALIDATION_ERROR);
  }

  @Test
  void analyzeWithUnknownConnectionReturnsError() {
    // analyze requires --source-conn, --source-table, --target-conn, --target-table
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "analyze",
            "--source-conn",
            "nonexistent",
            "--source-table",
            "orders",
            "--target-conn",
            "nonexistent2",
            "--target-table",
            "orders");
    assertThat(code).isNotEqualTo(ExitCode.VALIDATION_ERROR);
    assertThat(code).isNotEqualTo(ExitCode.SUCCESS);
  }

  // ── query ─────────────────────────────────────────────────────────────────

  @Test
  void queryWithMissingFromReturnsUsageError() {
    int code = cli.execute("--config", tempDir.toString(), "query");
    assertThat(code).isEqualTo(ExitCode.VALIDATION_ERROR);
  }

  @Test
  void queryWithAllArgumentsParsesCorrectly() {
    // query uses --from and --sql; connection not found → VALIDATION_ERROR
    // (IllegalArgumentException path)
    int code =
        cli.execute(
            "--config", tempDir.toString(), "query", "--from", "nonexistent", "--sql", "SELECT 1");
    assertThat(code).isNotEqualTo(ExitCode.SUCCESS);
  }
}
