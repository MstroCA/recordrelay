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

import io.recordrelay.core.clone.domain.CloneJob;
import io.recordrelay.core.clone.exception.CloneException;
import java.nio.file.Path;

/**
 * Driving port: extracts a root record with all related data from the source and writes a portable
 * {@code .rrpkg} archive to disk.
 *
 * <p>CLI: {@code rr export-package --source env --table table --id id --output dir}
 */
public interface ExportPackageUseCase {

  /**
   * Exports cloned data as a {@code .rrpkg} file in {@code outputDirectory}.
   *
   * @param job the clone job describing what to export
   * @param outputDirectory the directory where the package file will be created
   * @return the path to the created {@code .rrpkg} file
   * @throws CloneException if the export fails
   */
  Path exportPackage(CloneJob job, Path outputDirectory) throws CloneException;
}
