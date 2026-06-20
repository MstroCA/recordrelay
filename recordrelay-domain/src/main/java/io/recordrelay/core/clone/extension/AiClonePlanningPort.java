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
package io.recordrelay.core.clone.extension;

/**
 * Extension point: AI-assisted clone planning.
 *
 * <p><strong>Not yet implemented.</strong> Future implementations will use language models to
 * generate optimal {@link io.recordrelay.core.clone.domain.ContextClonePlan} configurations based
 * on a natural-language description of the issue to reproduce.
 *
 * <p>Example future usage:
 *
 * <pre>
 * // "Reproduce the payment processing bug for customer 123 from last Tuesday"
 * ContextClonePlan plan = aiPlanner.plan(description, sourceProfile);
 * </pre>
 */
public interface AiClonePlanningPort {
  // Reserved for future AI-assisted clone plan generation.
}
