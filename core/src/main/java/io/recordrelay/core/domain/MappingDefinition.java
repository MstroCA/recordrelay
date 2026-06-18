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

import java.util.List;
import java.util.Objects;

/**
 * Describes how data from a source {@link TableRef} should be transformed and written to a target.
 *
 * <p>Either {@code columnMappings} (for {@link MappingFormat#DIRECT}) or {@code query} (for SQL /
 * NoSQL formats) is expected to be non-empty, but both are optional to allow partial definitions
 * that are validated before execution via {@link io.recordrelay.core.port.out.MappingValidator}.
 */
public record MappingDefinition(
    String id,
    TableRef source,
    TableRef target,
    List<ColumnMapping> columnMappings,
    MappingFormat format,
    String query) {

  /** Validates required fields and produces an immutable copy of {@code columnMappings}. */
  public MappingDefinition {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(target, "target");
    columnMappings = columnMappings == null ? List.of() : List.copyOf(columnMappings);
    format = format == null ? MappingFormat.DIRECT : format;
  }

  /** Returns true when no explicit column mappings or query expression have been defined. */
  public boolean isEmpty() {
    return columnMappings.isEmpty() && (query == null || query.isBlank());
  }
}
