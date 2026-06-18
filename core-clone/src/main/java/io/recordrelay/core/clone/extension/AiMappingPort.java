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
 * Extension point: AI-assisted column mapping suggestion.
 *
 * <p><strong>Not yet implemented.</strong> Future implementations will use language models to
 * suggest column mappings between source and target schemas when names or types do not match.
 */
public interface AiMappingPort {
  // Reserved for future AI mapping integration.
}
