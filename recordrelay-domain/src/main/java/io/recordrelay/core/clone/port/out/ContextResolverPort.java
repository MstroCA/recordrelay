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

import io.recordrelay.core.clone.domain.CloneRequest;
import io.recordrelay.core.clone.domain.ContextClonePlan;
import io.recordrelay.core.clone.exception.CloneException;

/** Resolves a business-context plan to a table-level clone request. */
public interface ContextResolverPort {

  /** Translates a semantic context plan into a low-level table-scoped clone request. */
  CloneRequest resolve(ContextClonePlan plan) throws CloneException;
}
