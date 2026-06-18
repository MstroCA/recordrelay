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
package io.recordrelay.core.domain;

/**
 * The language/format used to express a {@link MappingDefinition} query or transformation.
 *
 * <p>The active format determines which validator is selected before transfer execution.
 */
public enum MappingFormat {
  /** Plain column-to-column mappings with no custom query. */
  DIRECT,
  /** Source query expressed as SQL SELECT. */
  SQL,
  /** Source query expressed as a MongoDB aggregation pipeline (JSON). */
  NOSQL_QUERY,
  /** Full mapping expressed as a YAML document. */
  YAML,
  /** Full mapping expressed as a JSON document. */
  JSON
}
