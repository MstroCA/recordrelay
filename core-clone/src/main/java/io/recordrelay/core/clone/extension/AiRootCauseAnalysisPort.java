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
 * Extension point: AI-assisted root cause analysis on replayed data.
 *
 * <p><strong>Not yet implemented.</strong> Future implementations will inspect replayed records and
 * the associated {@link io.recordrelay.core.clone.domain.BugReport} to suggest likely root causes,
 * anomalous field values, and data integrity issues.
 */
public interface AiRootCauseAnalysisPort {
  // Reserved for future AI-assisted root cause analysis.
}
