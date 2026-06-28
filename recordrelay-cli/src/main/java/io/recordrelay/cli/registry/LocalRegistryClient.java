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
package io.recordrelay.cli.registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Filesystem-backed registry stored at {@code ~/.recordrelay/registry/<name>/<tag>.rrpkg}.
 *
 * <p>This is the default backend when no {@code --registry} URL is supplied.
 */
public final class LocalRegistryClient implements RegistryClient {

  private final Path store;

  public LocalRegistryClient(Path store) {
    this.store = store;
  }

  /** Returns a client backed by {@code ~/.recordrelay/registry}. */
  public static LocalRegistryClient defaultStore() {
    return new LocalRegistryClient(
        Path.of(System.getProperty("user.home"), ".recordrelay", "registry"));
  }

  @Override
  public void push(String name, String tag, Path pkgFile) throws IOException {
    var dir = store.resolve(name);
    Files.createDirectories(dir);
    Files.copy(pkgFile, dir.resolve(tag + ".rrpkg"), StandardCopyOption.REPLACE_EXISTING);
  }

  @Override
  public Path pull(String name, String tag, Path outputDir) throws IOException {
    var src = store.resolve(name).resolve(tag + ".rrpkg");
    if (!Files.exists(src)) throw new IOException("Package not found: " + name + ":" + tag);
    Files.createDirectories(outputDir);
    var dest = outputDir.resolve(name + "-" + tag + ".rrpkg");
    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
    return dest;
  }

  @Override
  public List<String> list() throws IOException {
    if (!Files.exists(store)) return List.of();
    var result = new ArrayList<String>();
    try (var names = Files.list(store)) {
      names
          .filter(Files::isDirectory)
          .forEach(
              nameDir -> {
                try (var tags = Files.list(nameDir)) {
                  tags.filter(f -> f.getFileName().toString().endsWith(".rrpkg"))
                      .forEach(
                          f -> {
                            String t = f.getFileName().toString().replace(".rrpkg", "");
                            result.add(nameDir.getFileName() + ":" + t);
                          });
                } catch (IOException ignored) {
                }
              });
    }
    result.sort(String::compareTo);
    return result;
  }
}
