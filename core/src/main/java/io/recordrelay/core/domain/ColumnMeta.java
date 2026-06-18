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

import java.util.Objects;

/**
 * Metadata for a single column (SQL) or field (NoSQL document sampling).
 *
 * <p>{@code nativeType} carries the DB-specific type string exactly as reported by the driver
 * (e.g., {@code "character varying"}, {@code "int4"}, {@code "ObjectId"}). Cross-DB type mapping is
 * handled by the {@link io.recordrelay.core.port.out.SchemaInspector#analyzeCompatibility}
 * operation.
 */
public record ColumnMeta(
    String name,
    String nativeType,
    boolean nullable,
    boolean primaryKey,
    boolean indexed,
    int ordinalPosition,
    String defaultValue) {

  /** Validates required fields. {@code defaultValue} may be null. */
  public ColumnMeta {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(nativeType, "nativeType");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (ordinalPosition < 0) {
      throw new IllegalArgumentException("ordinalPosition must be >= 0");
    }
  }
}
