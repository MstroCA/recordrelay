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
import io.recordrelay.core.clone.domain.CloneReport;
import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.clone.port.out.CloneProgressListener;

/**
 * Driving port: clones a root record and all its transitive dependencies into a target database.
 *
 * <p>The engine traverses the relationship graph breadth-first, fetches all related records from
 * the source, optionally masks sensitive fields, and imports everything into the target.
 */
public interface CloneUseCase {

  /**
   * Executes the clone job and returns a summary report.
   *
   * @param job the clone job specification
   * @param listener receives progress events during execution; use a no-op implementation if
   *     progress reporting is not needed
   * @return a summary report of what was cloned
   * @throws CloneException if the clone cannot be completed
   */
  CloneReport clone(CloneJob job, CloneProgressListener listener) throws CloneException;
}
