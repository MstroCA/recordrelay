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

import io.recordrelay.core.domain.ValidationResult;
import java.util.Objects;

/**
 * Thrown when {@link io.recordrelay.core.port.out.MappingValidator} returns an invalid {@link
 * ValidationResult} and the transfer cannot proceed.
 */
public final class MappingValidationException extends RuntimeException {

  private final ValidationResult result;

  /**
   * Creates an exception carrying the full validation result for inspection by callers.
   *
   * @param result the failed validation result (must be non-null)
   */
  public MappingValidationException(ValidationResult result) {
    super("Mapping validation failed with " + result.errors().size() + " error(s)");
    this.result = Objects.requireNonNull(result, "result");
  }

  /**
   * Returns the full validation result containing all errors and warnings.
   *
   * @return the invalid validation result
   */
  public ValidationResult getResult() {
    return result;
  }
}
