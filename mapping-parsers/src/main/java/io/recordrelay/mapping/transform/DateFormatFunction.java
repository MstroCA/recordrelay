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
package io.recordrelay.mapping.transform;

import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.TransformFunction;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Formats a date or date-time value as a string using a {@link DateTimeFormatter} pattern.
 *
 * <p>Transform spec: {@code "date-format:<pattern>"} (e.g., {@code "date-format:yyyy-MM-dd"}).
 *
 * <p>Accepted input types: {@link java.sql.Date}, {@link java.sql.Timestamp}, {@link LocalDate},
 * {@link LocalDateTime}, or {@link String} (parsed as ISO-8601 date or date-time).
 */
public final class DateFormatFunction implements TransformFunction {

  @Override
  public String functionId() {
    return "date-format";
  }

  @Override
  public Object apply(Object value, String... args) throws ConnectorException {
    if (value == null) {
      return null;
    }
    if (args.length == 0 || args[0].isBlank()) {
      throw new ConnectorException("date-format: output pattern argument is required");
    }
    DateTimeFormatter formatter;
    try {
      formatter = DateTimeFormatter.ofPattern(args[0]);
    } catch (IllegalArgumentException e) {
      throw new ConnectorException("date-format: invalid pattern '" + args[0] + "'", e);
    }
    return formatValue(value, formatter);
  }

  private String formatValue(Object value, DateTimeFormatter formatter) throws ConnectorException {
    if (value instanceof java.sql.Timestamp ts) {
      return ts.toLocalDateTime().format(formatter);
    }
    if (value instanceof java.sql.Date sd) {
      return sd.toLocalDate().format(formatter);
    }
    if (value instanceof LocalDateTime ldt) {
      return ldt.format(formatter);
    }
    if (value instanceof LocalDate ld) {
      return ld.format(formatter);
    }
    if (value instanceof String str) {
      return parseAndFormat(str, formatter);
    }
    throw new ConnectorException(
        "date-format: unsupported value type " + value.getClass().getSimpleName());
  }

  private String parseAndFormat(String str, DateTimeFormatter formatter) throws ConnectorException {
    try {
      return LocalDateTime.parse(str).format(formatter);
    } catch (DateTimeParseException ignored) {
      // fall through to LocalDate
    }
    try {
      return LocalDate.parse(str).format(formatter);
    } catch (DateTimeParseException e) {
      throw new ConnectorException("date-format: cannot parse '" + str + "' as date or date-time");
    }
  }
}
