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
package io.recordrelay.core.clone.domain;

import java.util.Objects;

/** Associates a column name with a {@link MaskerType} to apply during cloning. */
public record MaskingRule(String column, MaskerType maskerType) {

  /** Validates required fields. */
  public MaskingRule {
    Objects.requireNonNull(column, "column");
    Objects.requireNonNull(maskerType, "maskerType");
    if (column.isBlank()) {
      throw new IllegalArgumentException("column must not be blank");
    }
  }
}
