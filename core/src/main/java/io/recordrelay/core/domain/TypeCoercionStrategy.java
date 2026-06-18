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
 * Controls how the transfer engine handles type mismatches between source and target columns.
 *
 * <p>Applied by {@code MappingTransformer} before each record is written.
 */
public enum TypeCoercionStrategy {
  /**
   * Types must be compatible as-is. Any type mismatch causes the transfer to abort with an error.
   */
  STRICT,

  /**
   * The engine attempts common conversions (String↔Number, Number↔String, etc.). If a conversion is
   * not supported, the transfer aborts with an error.
   */
  LENIENT,

  /**
   * If a type conversion fails, the offending row is skipped and written to the dead-letter
   * destination instead of aborting the whole transfer.
   */
  SKIP_ROW
}
