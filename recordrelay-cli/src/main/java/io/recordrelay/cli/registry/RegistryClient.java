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

import java.nio.file.Path;
import java.util.List;

/** Client interface for a .rrpkg package registry (local filesystem or HTTP). */
public interface RegistryClient {

  /** Uploads {@code pkgFile} to the registry under {@code name:tag}. */
  void push(String name, String tag, Path pkgFile) throws Exception;

  /** Downloads {@code name:tag} from the registry into {@code outputDir} and returns the file. */
  Path pull(String name, String tag, Path outputDir) throws Exception;

  /** Returns all {@code name:tag} entries available in the registry. */
  List<String> list() throws Exception;
}
