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

class SyncCommandTest {

  @TempDir Path tempDir;
  CommandLine cli;

  @BeforeEach
  void setUp() {
    cli = new CommandLine(new RecordRelayCli());
    System.setProperty("user.home", tempDir.toString());
  }

  @Test
  void syncWithNoSubcommandShowsHelp() {
    int code = cli.execute("--config", tempDir.toString(), "sync");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void addSyncEntrySucceeds() {
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "sync",
            "add",
            "daily-customers",
            "--entity",
            "customer",
            "--id",
            "1",
            "--from",
            "prod",
            "--target",
            "local",
            "--every",
            "60");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void addSyncEntryWithMaskSucceeds() {
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "sync",
            "add",
            "masked-orders",
            "--entity",
            "order",
            "--id",
            "99",
            "--from",
            "prod",
            "--target",
            "staging",
            "--mask",
            "--every",
            "120");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void listSyncEntriesWhenEmptySucceeds() {
    int code = cli.execute("--config", tempDir.toString(), "sync", "list");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void addAndListSyncEntry() {
    cli.execute(
        "--config",
        tempDir.toString(),
        "sync",
        "add",
        "weekly-invoice",
        "--entity",
        "invoice",
        "--id",
        "7",
        "--from",
        "prod",
        "--target",
        "local",
        "--every",
        "1440");

    int code = cli.execute("--config", tempDir.toString(), "sync", "list");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void removeExistingSyncEntrySucceeds() {
    cli.execute(
        "--config",
        tempDir.toString(),
        "sync",
        "add",
        "to-remove",
        "--entity",
        "customer",
        "--id",
        "5",
        "--from",
        "prod",
        "--target",
        "local",
        "--every",
        "30");

    int code = cli.execute("--config", tempDir.toString(), "sync", "remove", "to-remove");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void removeNonExistentSyncEntryReturnsError() {
    int code = cli.execute("--config", tempDir.toString(), "sync", "remove", "ghost");
    assertThat(code).isNotEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void runSyncWithUnknownConnectionReturnsError() {
    cli.execute(
        "--config",
        tempDir.toString(),
        "sync",
        "add",
        "bad-conn",
        "--entity",
        "customer",
        "--id",
        "1",
        "--from",
        "nonexistent",
        "--target",
        "also-nonexistent",
        "--every",
        "60");

    int code = cli.execute("--config", tempDir.toString(), "sync", "run", "bad-conn");
    assertThat(code).isNotEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void runNonExistentSyncEntryReturnsError() {
    int code = cli.execute("--config", tempDir.toString(), "sync", "run", "no-such-entry");
    assertThat(code).isNotEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void listSyncInJsonModeSucceeds() {
    cli.execute(
        "--config",
        tempDir.toString(),
        "sync",
        "add",
        "json-sync",
        "--entity",
        "project",
        "--id",
        "3",
        "--from",
        "prod",
        "--target",
        "local",
        "--every",
        "360");

    int code = cli.execute("--json", "--config", tempDir.toString(), "sync", "list");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }
}
