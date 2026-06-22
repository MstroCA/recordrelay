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
package io.recordrelay.core.clone.port.out;

import io.recordrelay.core.clone.domain.PackageManifest;
import io.recordrelay.core.clone.domain.RelationshipGraph;
import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.domain.DataRecord;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Serializes a context package to a portable archive file. */
public interface PackageExporterPort {

  /**
   * Serialises the context snapshot to an archive under {@code outputDirectory} and returns the
   * archive path.
   */
  Path export(
      PackageManifest manifest,
      RelationshipGraph graph,
      Map<String, List<DataRecord>> records,
      Path outputDirectory)
      throws CloneException;
}
