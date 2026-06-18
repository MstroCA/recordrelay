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
package io.recordrelay.core.exception;

/**
 * Thrown by {@link io.recordrelay.core.port.in.MappingParser} implementations when a mapping
 * document cannot be parsed.
 *
 * <p>{@code line} and {@code column} are {@code -1} when no location information is available.
 */
public final class MappingParseException extends Exception {

  private final int line;
  private final int column;

  /**
   * Creates an exception with source-location information.
   *
   * @param message human-readable error description
   * @param line 1-based line number, or {@code -1} if unknown
   * @param column 1-based column number, or {@code -1} if unknown
   */
  public MappingParseException(String message, int line, int column) {
    super(message);
    this.line = line;
    this.column = column;
  }

  /**
   * Creates an exception wrapping an underlying parse error without source-location information.
   *
   * @param message human-readable error description
   * @param cause the underlying exception
   */
  public MappingParseException(String message, Throwable cause) {
    super(message, cause);
    this.line = -1;
    this.column = -1;
  }

  /**
   * Returns the 1-based line number where the error occurred, or {@code -1} if unknown.
   *
   * @return line number or {@code -1}
   */
  public int getLine() {
    return line;
  }

  /**
   * Returns the 1-based column number where the error occurred, or {@code -1} if unknown.
   *
   * @return column number or {@code -1}
   */
  public int getColumn() {
    return column;
  }
}
