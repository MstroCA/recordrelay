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
 * Represents the structural compatibility of a single column between a source and target schema.
 *
 * @param sourceColumn name of the matching source column, or {@code null} if absent
 * @param targetColumn name of the expected target column
 * @param compatible {@code true} when the column exists in the source with a compatible type
 * @param warning human-readable description of any mismatch, or {@code null} when compatible
 */
public record ColumnCompatibility(
    String sourceColumn, String targetColumn, boolean typeCompatible, String warning) {}
