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

import io.recordrelay.cli.RecordRelayCli;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class EnvCommandTest {

  @TempDir Path tempDir;
  CommandLine cli;

  @BeforeEach
  void setUp() {
    cli = new CommandLine(new RecordRelayCli());
  }

  @Test
  void addEnvironmentCreatesConfigFile() throws Exception {
    int code = cli.execute("--config", tempDir.toString(), "env", "add", "staging");
    assertThat(code).isEqualTo(0);
    assertThat(Files.exists(tempDir.resolve("config.json"))).isTrue();
  }

  @Test
  void addAndListEnvironment() throws Exception {
    cli.execute("--config", tempDir.toString(), "env", "add", "prod", "--desc", "Production");
    int code = cli.execute("--config", tempDir.toString(), "env", "list");
    assertThat(code).isEqualTo(0);
  }

  @Test
  void removeNonExistentEnvironmentReturnsError() throws Exception {
    int code = cli.execute("--config", tempDir.toString(), "env", "remove", "ghost");
    assertThat(code).isNotEqualTo(0);
  }

  @Test
  void addThenRemoveEnvironment() throws Exception {
    cli.execute("--config", tempDir.toString(), "env", "add", "dev");
    int code = cli.execute("--config", tempDir.toString(), "env", "remove", "dev");
    assertThat(code).isEqualTo(0);
  }

  @Test
  void jsonOutputModeProducesValidJson() throws Exception {
    cli.execute("--config", tempDir.toString(), "env", "add", "test");
    int code = cli.execute("--json", "--config", tempDir.toString(), "env", "list");
    assertThat(code).isEqualTo(0);
  }
}
