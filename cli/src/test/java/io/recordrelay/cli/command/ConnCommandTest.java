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
import io.recordrelay.cli.config.ConfigStore;
import io.recordrelay.cli.config.ConnectionEntry;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class ConnCommandTest {

  @TempDir Path tempDir;

  @Test
  void listConnectionsWithNoEntries() throws Exception {
    int code =
        new CommandLine(new RecordRelayCli())
            .execute("--config", tempDir.toString(), "conn", "list");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void removeNonExistentConnectionReturnsError() throws Exception {
    int code =
        new CommandLine(new RecordRelayCli())
            .execute("--config", tempDir.toString(), "conn", "remove", "ghost");
    assertThat(code).isNotEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void listConnectionsShowsStoredEntry() throws Exception {
    var store = new ConfigStore(tempDir);
    var entry = buildEntry();
    store.addConnection("mydb", entry);

    int code =
        new CommandLine(new RecordRelayCli())
            .execute("--config", tempDir.toString(), "conn", "list");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void removeExistingConnectionReturnsSuccess() throws Exception {
    var store = new ConfigStore(tempDir);
    store.addConnection("mydb", buildEntry());

    int code =
        new CommandLine(new RecordRelayCli())
            .execute("--config", tempDir.toString(), "conn", "remove", "mydb");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  private ConnectionEntry buildEntry() throws Exception {
    var store = new ConfigStore(tempDir);
    var entry = new ConnectionEntry();
    entry.setEnvironment("dev");
    entry.setType("POSTGRESQL");
    entry.setHost("localhost");
    entry.setPort(5432);
    entry.setDatabase("testdb");
    entry.setUser("admin");
    entry.setEncryptedPassword(store.encryptor().encrypt("secret"));
    return entry;
  }
}
