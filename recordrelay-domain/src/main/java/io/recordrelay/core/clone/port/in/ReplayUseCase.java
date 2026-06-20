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
package io.recordrelay.core.clone.port.in;

import io.recordrelay.core.clone.domain.CloneReport;
import io.recordrelay.core.clone.domain.PackageManifest;
import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.core.domain.ConnectionProfile;
import java.nio.file.Path;

/**
 * Driving port: replays a {@code .rrpkg} reproduction package into a target environment.
 *
 * <p>Replay is semantically distinct from a plain import:
 *
 * <ul>
 *   <li>The package manifest is read first and the embedded {@link
 *       io.recordrelay.core.clone.domain.BugReport} (if any) is surfaced to the caller.
 *   <li>The engine prints reproduction context (bug ID, title, steps) before writing records.
 *   <li>The returned report carries replay provenance (source package path, captured-at timestamp).
 * </ul>
 *
 * <p>Usage:
 *
 * <pre>
 * CloneReport report = engine.replay(
 *     Path.of("bug-1234.rrpkg"), localProfile, listener);
 * </pre>
 */
public interface ReplayUseCase {

  /**
   * Reads the manifest from the package without importing any data.
   *
   * <p>Useful for inspecting a package before deciding whether to replay it.
   *
   * @param packagePath path to the {@code .rrpkg} file
   * @return the package manifest
   * @throws CloneException if the manifest cannot be read
   */
  PackageManifest inspect(Path packagePath) throws CloneException;

  /**
   * Imports all records from the package into the target database.
   *
   * @param packagePath path to the {@code .rrpkg} file
   * @param target target connection profile
   * @param listener progress callbacks
   * @return a summary report of what was replayed
   * @throws CloneException if replay fails
   */
  CloneReport replay(Path packagePath, ConnectionProfile target, CloneProgressListener listener)
      throws CloneException;
}
