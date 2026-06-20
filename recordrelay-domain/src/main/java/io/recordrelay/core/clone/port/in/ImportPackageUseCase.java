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
import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.core.domain.ConnectionProfile;
import java.nio.file.Path;

/**
 * Driving port: reads a {@code .rrpkg} archive and imports the records into a target database.
 *
 * <p>CLI: {@code rr import-package --target env --file path/to/package.rrpkg}
 */
public interface ImportPackageUseCase {

  /**
   * Imports records from a {@code .rrpkg} file into the target database.
   *
   * @param packagePath the path to the {@code .rrpkg} file to import
   * @param target the target connection profile
   * @param listener receives progress events; use a no-op if not needed
   * @return a summary report of what was imported
   * @throws CloneException if the import fails
   */
  CloneReport importPackage(
      Path packagePath, ConnectionProfile target, CloneProgressListener listener)
      throws CloneException;
}
