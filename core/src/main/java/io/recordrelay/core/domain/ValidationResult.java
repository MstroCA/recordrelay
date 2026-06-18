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

/**
 * The outcome of a {@link io.recordrelay.core.port.out.MappingValidator} run.
 *
 * <p>A result is valid when it contains no {@link ValidationSeverity#ERROR}-severity entries.
 * Warnings are allowed in a valid result.
 */
public record ValidationResult(boolean valid, List<ValidationError> errors, List<String> warnings) {

  /** Validates inputs and produces immutable copies of the error and warning lists. */
  public ValidationResult {
    errors = errors == null ? List.of() : List.copyOf(errors);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  /** Returns a fully valid result with no errors or warnings. */
  public static ValidationResult ok() {
    return new ValidationResult(true, List.of(), List.of());
  }

  /**
   * Returns a valid result that carries informational warnings.
   *
   * @param warnings warning messages for the user
   * @return a valid result with warnings
   */
  public static ValidationResult ok(List<String> warnings) {
    return new ValidationResult(true, List.of(), warnings);
  }

  /**
   * Returns an invalid result from a list of errors.
   *
   * @param errors the errors that make the mapping invalid
   * @return an invalid result
   */
  public static ValidationResult failed(List<ValidationError> errors) {
    return new ValidationResult(false, errors, List.of());
  }
}
