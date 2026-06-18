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
 * Substitutes a default string when the column value is {@code null} or blank.
 *
 * <p>Transform spec: {@code "default-value:<default>"} (e.g., {@code "default-value:N/A"} or {@code
 * "default-value:0"}). Non-null, non-blank values are passed through unchanged.
 */
public final class DefaultValueFunction implements TransformFunction {

  @Override
  public String functionId() {
    return "default-value";
  }

  @Override
  public Object apply(Object value, String... args) throws ConnectorException {
    if (args.length == 0) {
      throw new ConnectorException("default-value: default argument is required");
    }
    if (value == null || value.toString().isBlank()) {
      return args[0];
    }
    return value;
  }
}
