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

/**
 * Built-in data masking strategies.
 *
 * <p>All maskers are deterministic: the same input value always produces the same masked value,
 * preserving referential integrity across related records.
 */
public enum MaskerType {
  /** Masks an email address: {@code john@example.com} → {@code user_4fa82@example.test}. */
  EMAIL,
  /** Masks a phone number while preserving format length. */
  PHONE,
  /** Replaces an address with a synthetic placeholder. */
  ADDRESS,
  /** Masks an IBAN while preserving the country code prefix and character count. */
  IBAN,
  /** Masks a national ID / SSN while preserving format length. */
  NATIONAL_ID
}
