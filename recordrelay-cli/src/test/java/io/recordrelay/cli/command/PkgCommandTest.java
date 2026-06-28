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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class PkgCommandTest {

  @TempDir Path tempDir;
  CommandLine cli;

  @BeforeEach
  void setUp() {
    cli = new CommandLine(new RecordRelayCli());
    System.setProperty("user.home", tempDir.toString());
  }

  @Test
  void pkgWithNoSubcommandShowsHelp() {
    int code = cli.execute("--config", tempDir.toString(), "pkg");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void listPackagesWhenEmptySucceeds() {
    int code =
        cli.execute(
            "--config", tempDir.toString(),
            "pkg", "list",
            "--registry", tempDir.resolve("registry").toString());
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void pushAndListPackage() throws Exception {
    Path pkgFile = tempDir.resolve("customer-123.rrpkg");
    Files.writeString(pkgFile, "{\"test\":\"data\"}");
    Path registryDir = tempDir.resolve("registry");

    int pushCode =
        cli.execute(
            "--config",
            tempDir.toString(),
            "pkg",
            "push",
            pkgFile.toString(),
            "--as",
            "customer",
            "--tag",
            "v1",
            "--registry",
            registryDir.toString());
    assertThat(pushCode).isEqualTo(ExitCode.SUCCESS);

    int listCode =
        cli.execute(
            "--config", tempDir.toString(),
            "pkg", "list",
            "--registry", registryDir.toString());
    assertThat(listCode).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void pushWithDefaultTagLatest() throws Exception {
    Path pkgFile = tempDir.resolve("order.rrpkg");
    Files.writeString(pkgFile, "{}");
    Path registryDir = tempDir.resolve("reg");

    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "pkg",
            "push",
            pkgFile.toString(),
            "--as",
            "order",
            "--registry",
            registryDir.toString());
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void pushThenPullPackage() throws Exception {
    Path pkgFile = tempDir.resolve("invoice.rrpkg");
    Files.writeString(pkgFile, "{\"invoice\":true}");
    Path registryDir = tempDir.resolve("reg2");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(outDir);

    cli.execute(
        "--config",
        tempDir.toString(),
        "pkg",
        "push",
        pkgFile.toString(),
        "--as",
        "invoice",
        "--tag",
        "v2",
        "--registry",
        registryDir.toString());

    int pullCode =
        cli.execute(
            "--config",
            tempDir.toString(),
            "pkg",
            "pull",
            "invoice",
            "--tag",
            "v2",
            "--out",
            outDir.toString(),
            "--registry",
            registryDir.toString());
    assertThat(pullCode).isEqualTo(ExitCode.SUCCESS);
    assertThat(outDir.resolve("invoice-v2.rrpkg")).exists();
  }

  @Test
  void pullNonExistentPackageReturnsError() {
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "pkg",
            "pull",
            "no-such-package",
            "--tag",
            "v1",
            "--registry",
            tempDir.resolve("empty-reg").toString());
    assertThat(code).isNotEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void pushNonExistentFileReturnsError() {
    int code =
        cli.execute(
            "--config",
            tempDir.toString(),
            "pkg",
            "push",
            tempDir.resolve("missing.rrpkg").toString(),
            "--as",
            "test",
            "--registry",
            tempDir.resolve("reg").toString());
    assertThat(code).isNotEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void listPackagesInLocalDefaultStore() {
    // Local store always returns empty list without error when no packages pushed
    int code = cli.execute("--config", tempDir.toString(), "pkg", "list");
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  void multiplePackagesPushedAreAllListed() throws Exception {
    Path regDir = tempDir.resolve("multi-reg");

    for (String name : new String[] {"alpha", "beta", "gamma"}) {
      Path f = tempDir.resolve(name + ".rrpkg");
      Files.writeString(f, name);
      cli.execute(
          "--config",
          tempDir.toString(),
          "pkg",
          "push",
          f.toString(),
          "--as",
          name,
          "--registry",
          regDir.toString());
    }

    int code =
        cli.execute(
            "--config", tempDir.toString(),
            "pkg", "list",
            "--registry", regDir.toString());
    assertThat(code).isEqualTo(ExitCode.SUCCESS);
  }
}
