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

/**
 * Appends a literal suffix to the string representation of a value.
 *
 * <p>Transform spec: {@code "concat:<suffix>"} (e.g., {@code "concat:_v2"} or {@code "concat: "}).
 * When no suffix is given ({@code "concat"}), the value is returned as-is converted to a string.
 *
 * <p>For multi-column concatenation (e.g., first_name + last_name), use the SQL mapping format
 * which supports full SQL expressions such as {@code CONCAT(first_name, ' ', last_name)}.
 */
public final class ConcatFunction implements TransformFunction {

  @Override
  public String functionId() {
    return "concat";
  }

  @Override
  public Object apply(Object value, String... args) throws ConnectorException {
    if (value == null) {
      return null;
    }
    String suffix = args.length > 0 ? args[0] : "";
    return value.toString() + suffix;
  }
}
