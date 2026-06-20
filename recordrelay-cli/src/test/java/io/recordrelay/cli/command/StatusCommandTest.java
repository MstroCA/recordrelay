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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class StatusCommandTest {

  @TempDir Path tempDir;

  @Test
  void statusCommandSucceeds() {
    int code =
        new CommandLine(new RecordRelayCli()).execute("--config", tempDir.toString(), "status");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void statusCommandInJsonModeSucceeds() {
    int code =
        new CommandLine(new RecordRelayCli())
            .execute("--json", "--config", tempDir.toString(), "status");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }
}
