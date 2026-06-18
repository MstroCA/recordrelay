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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A single row of data flowing through the transfer pipeline.
 *
 * <p>Field values are stored as {@link Object} to accommodate any DB-native type. Connectors are
 * responsible for type coercion during read and write operations.
 */
public record DataRecord(Map<String, Object> fields) {

  /** Produces an immutable copy of the provided field map. Null values are permitted. */
  public DataRecord {
    Objects.requireNonNull(fields, "fields");
    fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
  }

  /**
   * Returns the value associated with {@code fieldName}, or {@code null} if absent.
   *
   * @param fieldName the field/column name
   * @return the field value, or {@code null}
   */
  public Object get(String fieldName) {
    return fields.get(fieldName);
  }

  /**
   * Returns true when the field exists in this record (value may still be {@code null}).
   *
   * @param fieldName the field/column name to check
   * @return true if the field is present
   */
  public boolean hasField(String fieldName) {
    return fields.containsKey(fieldName);
  }

  /** Returns all field names present in this record. */
  public Set<String> fieldNames() {
    return fields.keySet();
  }

  /**
   * Factory method for concise record creation.
   *
   * @param fields key-value pairs of field names to values
   * @return an immutable DataRecord
   */
  public static DataRecord of(Map<String, Object> fields) {
    return new DataRecord(fields);
  }
}
