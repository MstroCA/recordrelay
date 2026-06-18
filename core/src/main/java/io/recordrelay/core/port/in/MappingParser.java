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
package io.recordrelay.core.port.in;

import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.ValidationResult;
import io.recordrelay.core.exception.MappingParseException;

/**
 * Driving port: parses a raw mapping document (JSON, YAML, SQL, or NoSQL) into a {@link
 * MappingDefinition}.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} and identified by {@link
 * #formatId()}. The two-phase API — {@link #validate} then {@link #parse} — allows callers to
 * surface syntax errors before attempting full parse.
 */
public interface MappingParser {

  /**
   * A short identifier for the format handled by this parser (e.g., {@code "json"}, {@code "yaml"},
   * {@code "sql"}, {@code "nosql-query"}).
   *
   * @return the format identifier
   */
  String formatId();

  /**
   * Validates the syntax and required fields of {@code content} without constructing a domain
   * object.
   *
   * <p>This method never throws; all problems are reported through the returned {@link
   * ValidationResult}. Syntax errors carry line and column information via {@link
   * io.recordrelay.core.domain.ValidationError#syntaxError(int, int, String)}.
   *
   * @param content raw mapping document
   * @return the validation outcome; {@link ValidationResult#ok()} when the content is fully valid
   */
  ValidationResult validate(String content);

  /**
   * Parses {@code content} into a {@link MappingDefinition}.
   *
   * <p>Callers should invoke {@link #validate} first to obtain field-level errors before calling
   * this method.
   *
   * @param content raw mapping document
   * @return the parsed mapping definition
   * @throws MappingParseException if the content cannot be parsed
   */
  MappingDefinition parse(String content) throws MappingParseException;
}
