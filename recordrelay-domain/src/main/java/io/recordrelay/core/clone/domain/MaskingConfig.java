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

import java.util.List;
import java.util.Optional;

/**
 * Collects the set of {@link MaskingRule}s to apply during a clone operation.
 *
 * <p>Use {@link #none()} for a no-op masking configuration.
 */
public record MaskingConfig(List<MaskingRule> rules) {

  /** Validates rules and produces an immutable copy. */
  public MaskingConfig {
    rules = rules == null ? List.of() : List.copyOf(rules);
  }

  /** Returns a masking configuration with no rules applied. */
  public static MaskingConfig none() {
    return new MaskingConfig(List.of());
  }

  /** Returns true when no masking rules are configured. */
  public boolean isEmpty() {
    return rules.isEmpty();
  }

  /** Returns the masking rule for {@code column}, if one exists. */
  public Optional<MaskingRule> ruleFor(String column) {
    return rules.stream().filter(r -> r.column().equalsIgnoreCase(column)).findFirst();
  }
}
