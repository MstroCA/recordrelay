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
import io.recordrelay.core.clone.domain.ContextClonePlan;
import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import java.nio.file.Path;

/**
 * Driving port: reproduces a business context (customer, order, user…) from one environment to
 * another, expressed in entity terms rather than table terms.
 *
 * <p>The engine resolves the {@link ContextClonePlan} to the correct table, ID column, and
 * dependency graph without the caller needing to know database internals.
 *
 * <p>Usage:
 *
 * <pre>
 * ContextClonePlan plan = ContextClonePlan.liveClone(
 *     BuiltinEntityRegistry.CUSTOMER, "123", prodProfile, localProfile, 3, MaskingConfig.pii());
 * CloneReport report = engine.cloneContext(plan, listener);
 * </pre>
 */
public interface ContextCloneUseCase {

  /**
   * Clones the business context directly into a live target database.
   *
   * @param plan the entity-level clone plan; must have a non-null target profile
   * @param listener progress callbacks; use a no-op implementation if not needed
   * @return a summary report of what was cloned
   * @throws CloneException if the context cannot be reproduced
   * @throws IllegalArgumentException if the plan has no target (i.e. it is an export-only plan)
   */
  CloneReport cloneContext(ContextClonePlan plan, CloneProgressListener listener)
      throws CloneException;

  /**
   * Exports the business context as a portable {@code .rrpkg} package.
   *
   * <p>The package includes all related records, the relationship graph, and the inferred schema.
   * If the plan carries a {@link io.recordrelay.core.clone.domain.BugReport}, it is embedded in the
   * package manifest.
   *
   * @param plan the entity-level clone plan; {@code outputDirectory} must be non-null
   * @return the path to the created {@code .rrpkg} file
   * @throws CloneException if the package cannot be written
   */
  Path exportContext(ContextClonePlan plan) throws CloneException;
}
