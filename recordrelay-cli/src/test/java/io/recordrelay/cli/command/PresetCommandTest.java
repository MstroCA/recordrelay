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
import io.recordrelay.cli.config.PresetStore;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class PresetCommandTest {

  @TempDir Path tempDir;
  CommandLine cli;

  @BeforeEach
  void setUp() {
    cli = new CommandLine(new RecordRelayCli());
    // Point PresetStore at a temp home so tests are isolated
    System.setProperty("user.home", tempDir.toString());
  }

  @Test
  void listPresetsWhenEmptySucceeds() {
    int code = cli.execute("--config", tempDir.toString(), "preset", "list");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void savePresetSucceeds() {
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "preset",
            "save",
            "my-preset",
            "--entity",
            "customer",
            "--id",
            "42",
            "--from",
            "prod",
            "--target",
            "local");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void saveAndListPreset() {
    cli.execute(
        "--config",
        tempDir.toString(),
        "preset",
        "save",
        "order-preset",
        "--entity",
        "order",
        "--id",
        "99",
        "--from",
        "prod",
        "--target",
        "staging");

    int code = cli.execute("--config", tempDir.toString(), "preset", "list");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void savePresetWithDescriptionAndMaskFlag() {
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "preset",
            "save",
            "pii-preset",
            "--entity",
            "user",
            "--id",
            "1",
            "--from",
            "prod",
            "--target",
            "local",
            "--mask",
            "--desc",
            "PII-masked preset");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void savePresetWithFieldOverrides() {
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "preset",
            "save",
            "override-preset",
            "--entity",
            "customer",
            "--id",
            "1",
            "--from",
            "prod",
            "--target",
            "local",
            "--override",
            "status=active",
            "--override",
            "orders:amount=0.0");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void savePresetThenRemoveSucceeds() {
    cli.execute(
        "--config",
        tempDir.toString(),
        "preset",
        "save",
        "temp-preset",
        "--entity",
        "invoice",
        "--id",
        "7",
        "--from",
        "src",
        "--target",
        "dst");

    int code = cli.execute("--config", tempDir.toString(), "preset", "remove", "temp-preset");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void removeNonExistentPresetReturnsError() {
    int code = cli.execute("--config", tempDir.toString(), "preset", "remove", "ghost-preset");
    assertThat(code).isNotEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void runPresetWithUnknownConnectionReturnsError() {
    cli.execute(
        "--config",
        tempDir.toString(),
        "preset",
        "save",
        "run-test",
        "--entity",
        "customer",
        "--id",
        "1",
        "--from",
        "nonexistent-src",
        "--target",
        "nonexistent-tgt");

    // Run should fail because the connections don't exist
    int code = cli.execute("--config", tempDir.toString(), "preset", "run", "run-test");
    assertThat(code).isNotEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void runNonExistentPresetReturnsError() {
    int code = cli.execute("--config", tempDir.toString(), "preset", "run", "does-not-exist");
    assertThat(code).isNotEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void presetSubcommandWithNoArgsShowsHelp() {
    int code = cli.execute("--config", tempDir.toString(), "preset");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void saveOverwritesExistingPreset() throws Exception {
    cli.execute(
        "--config",
        tempDir.toString(),
        "preset",
        "save",
        "dup",
        "--entity",
        "customer",
        "--id",
        "1",
        "--from",
        "a",
        "--target",
        "b");
    cli.execute(
        "--config",
        tempDir.toString(),
        "preset",
        "save",
        "dup",
        "--entity",
        "order",
        "--id",
        "99",
        "--from",
        "x",
        "--target",
        "y");

    var store = new PresetStore();
    var p = store.findByName("dup");
    assertThat(p).isPresent();
    assertThat(p.get().getEntityName()).isEqualTo("order");
  }

  @Test
  void listPresetsInJsonModeSucceeds() {
    cli.execute(
        "--config",
        tempDir.toString(),
        "preset",
        "save",
        "json-test",
        "--entity",
        "account",
        "--id",
        "5",
        "--from",
        "prod",
        "--target",
        "local");

    int code = cli.execute("--json", "--config", tempDir.toString(), "preset", "list");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }
}
