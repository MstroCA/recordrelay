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
import java.util.regex.PatternSyntaxException;

/**
 * Applies a regular-expression replacement to the string representation of a value.
 *
 * <p>Transform spec: {@code "regex-replace:<pattern>:<replacement>"} (e.g., {@code
 * "regex-replace:\\s+: "}). The replacement may be empty. Note that {@code ":"} within the pattern
 * is not supported; use a JSON/YAML mapping for complex patterns.
 */
public final class RegexReplaceFunction implements TransformFunction {

  @Override
  public String functionId() {
    return "regex-replace";
  }

  @Override
  public Object apply(Object value, String... args) throws ConnectorException {
    if (value == null) {
      return null;
    }
    if (args.length < 2) {
      throw new ConnectorException(
          "regex-replace: requires two arguments: <pattern> and <replacement>");
    }
    String pattern = args[0];
    String replacement = args[1];
    try {
      return value.toString().replaceAll(pattern, replacement);
    } catch (PatternSyntaxException e) {
      throw new ConnectorException(
          "regex-replace: invalid pattern '" + pattern + "': " + e.getMessage(), e);
    }
  }
}
