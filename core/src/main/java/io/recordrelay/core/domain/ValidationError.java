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
 * A single error or warning produced by {@link io.recordrelay.core.port.out.MappingValidator} or
 * {@link io.recordrelay.core.port.in.MappingParser}.
 *
 * <p>{@code line} and {@code column} are {@code -1} when the error is field-level rather than
 * syntax-location-based.
 */
public record ValidationError(
    String field, String message, ValidationSeverity severity, int line, int column) {

  /** Validates required fields. */
  public ValidationError {
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(severity, "severity");
    field = field == null ? "" : field;
  }

  /** Backwards-compatible 3-arg constructor; line and column default to {@code -1}. */
  public ValidationError(String field, String message, ValidationSeverity severity) {
    this(field, message, severity, -1, -1);
  }

  /** Returns {@code true} when this error carries source-location information. */
  public boolean hasSyntaxLocation() {
    return line >= 0;
  }

  /**
   * Creates a field-level error.
   *
   * @param field the column or mapping property that triggered the error
   * @param message human-readable description
   * @return a new error-severity validation error
   */
  public static ValidationError error(String field, String message) {
    return new ValidationError(field, message, ValidationSeverity.ERROR);
  }

  /**
   * Creates a field-level warning.
   *
   * @param field the column or mapping property that triggered the warning
   * @param message human-readable description
   * @return a new warning-severity validation error
   */
  public static ValidationError warning(String field, String message) {
    return new ValidationError(field, message, ValidationSeverity.WARNING);
  }

  /**
   * Creates a syntax-location error with line and column from a parser.
   *
   * @param line 1-based line number where the error occurred
   * @param column 1-based column number where the error occurred
   * @param message human-readable description
   * @return a new error-severity validation error with location
   */
  public static ValidationError syntaxError(int line, int column, String message) {
    return new ValidationError("", message, ValidationSeverity.ERROR, line, column);
  }

  /**
   * Creates a syntax-location warning with line and column from a parser.
   *
   * @param line 1-based line number where the warning occurred
   * @param column 1-based column number where the warning occurred
   * @param message human-readable description
   * @return a new warning-severity validation error with location
   */
  public static ValidationError syntaxWarning(int line, int column, String message) {
    return new ValidationError("", message, ValidationSeverity.WARNING, line, column);
  }
}
