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
 * Extension point: AI-assisted masking column suggestions.
 *
 * <p><strong>Not yet implemented.</strong> Future implementations will analyse column names,
 * sampled values, and table semantics to automatically suggest which columns should be masked and
 * with which {@link io.recordrelay.core.clone.domain.MaskerType}.
 *
 * <p>This complements the deterministic built-in heuristics (email / phone / address / iban /
 * national_id column name matching) with model-inferred suggestions for non-obvious PII columns.
 */
public interface AiMaskingSuggestionsPort {
  // Reserved for future AI-assisted masking suggestion.
}
