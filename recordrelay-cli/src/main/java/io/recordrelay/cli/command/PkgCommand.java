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

import io.recordrelay.cli.ExitCode;
import io.recordrelay.cli.RecordRelayCli;
import io.recordrelay.cli.registry.HttpRegistryClient;
import io.recordrelay.cli.registry.LocalRegistryClient;
import io.recordrelay.cli.registry.PackageRegistryServer;
import io.recordrelay.cli.registry.RegistryClient;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * {@code rr pkg} — push, pull, list, and serve .rrpkg data packages.
 *
 * <pre>
 * rr pkg push customer-123.rrpkg --as customer-dev --tag v1
 * rr pkg pull customer-dev --tag v1 --out ./packages
 * rr pkg list
 * rr pkg serve --port 8282
 * </pre>
 */
@Command(
    name = "pkg",
    description = "Push, pull, list, and serve .rrpkg data packages.",
    subcommands = {
      PkgCommand.Push.class,
      PkgCommand.Pull.class,
      PkgCommand.List.class,
      PkgCommand.Serve.class
    })
public final class PkgCommand implements Callable<Integer> {

  @ParentCommand RecordRelayCli parent;

  @Override
  public Integer call() {
    CommandLine.usage(this, System.out);
    return ExitCode.SUCCESS;
  }

  static RegistryClient resolveClient(String registry) {
    if (registry == null || registry.isBlank() || "local".equals(registry)) {
      return LocalRegistryClient.defaultStore();
    }
    if (registry.startsWith("http://") || registry.startsWith("https://")) {
      return new HttpRegistryClient(registry);
    }
    return new LocalRegistryClient(Path.of(registry));
  }

  // ── push ──────────────────────────────────────────────────────────────────

  @Command(name = "push", description = "Push a .rrpkg file to the registry.")
  static final class Push implements Callable<Integer> {

    @ParentCommand PkgCommand pkg;

    @Parameters(index = "0", description = "Path to the .rrpkg file to push")
    Path file;

    @Option(names = "--as", required = true, description = "Package name in the registry")
    String name;

    @Option(names = "--tag", defaultValue = "latest", description = "Tag (default: latest)")
    String tag;

    @Option(names = "--registry", description = "Registry URL or path (default: local)")
    String registry;

    @Override
    public Integer call() {
      try {
        resolveClient(registry).push(name, tag, file);
        System.out.printf("Pushed %s → %s:%s%n", file.getFileName(), name, tag);
        return ExitCode.SUCCESS;
      } catch (Exception e) {
        System.err.println("Push failed: " + e.getMessage());
        return ExitCode.PACKAGE_ERROR;
      }
    }
  }

  // ── pull ──────────────────────────────────────────────────────────────────

  @Command(name = "pull", description = "Pull a .rrpkg file from the registry.")
  static final class Pull implements Callable<Integer> {

    @ParentCommand PkgCommand pkg;

    @Parameters(index = "0", description = "Package name")
    String name;

    @Option(names = "--tag", defaultValue = "latest", description = "Tag (default: latest)")
    String tag;

    @Option(names = "--out", description = "Output directory (default: current directory)")
    Path outputDir;

    @Option(names = "--registry", description = "Registry URL or path (default: local)")
    String registry;

    @Override
    public Integer call() {
      try {
        var dir = outputDir != null ? outputDir : Path.of(".");
        var dest = resolveClient(registry).pull(name, tag, dir);
        System.out.printf("Pulled %s:%s → %s%n", name, tag, dest);
        return ExitCode.SUCCESS;
      } catch (Exception e) {
        System.err.println("Pull failed: " + e.getMessage());
        return ExitCode.PACKAGE_ERROR;
      }
    }
  }

  // ── list ──────────────────────────────────────────────────────────────────

  @Command(name = "list", description = "List packages available in the registry.")
  static final class List implements Callable<Integer> {

    @ParentCommand PkgCommand pkg;

    @Option(names = "--registry", description = "Registry URL or path (default: local)")
    String registry;

    @Override
    public Integer call() {
      try {
        var packages = resolveClient(registry).list();
        if (packages.isEmpty()) {
          System.out.println("(no packages)");
        } else {
          packages.forEach(System.out::println);
        }
        return ExitCode.SUCCESS;
      } catch (Exception e) {
        System.err.println("List failed: " + e.getMessage());
        return ExitCode.PACKAGE_ERROR;
      }
    }
  }

  // ── serve ─────────────────────────────────────────────────────────────────

  @Command(name = "serve", description = "Start an HTTP package registry server.")
  static final class Serve implements Callable<Integer> {

    @ParentCommand PkgCommand pkg;

    @Option(
        names = "--port",
        defaultValue = "8282",
        description = "Port to listen on (default: 8282)")
    int port;

    @Option(
        names = "--store",
        description = "Package store directory (default: ~/.recordrelay/registry)")
    Path storeDir;

    @Override
    public Integer call() {
      try {
        var dir =
            storeDir != null
                ? storeDir
                : Path.of(System.getProperty("user.home"), ".recordrelay", "registry");
        var srv = new PackageRegistryServer(port, dir);
        srv.start();
        System.out.printf("Package registry running on http://localhost:%d%n", port);
        System.out.println("Press Ctrl+C to stop.");
        Runtime.getRuntime().addShutdownHook(new Thread(srv::stop));
        Thread.currentThread().join();
        return ExitCode.SUCCESS;
      } catch (Exception e) {
        System.err.println("Serve failed: " + e.getMessage());
        return ExitCode.PACKAGE_ERROR;
      }
    }
  }
}
