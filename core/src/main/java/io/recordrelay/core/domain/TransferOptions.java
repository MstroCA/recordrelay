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
package io.recordrelay.core.domain;

/**
 * Tuning and error-handling options for a {@link TransferJob}.
 *
 * <p>Use {@link #defaults()} to obtain a sensible out-of-the-box configuration.
 *
 * @param skipLimit maximum number of rows that may be skipped before the transfer aborts; 0 means
 *     fail on the first error
 * @param retryLimit maximum retry attempts for a transient error within a single chunk; only
 *     meaningful for {@link TransferMode#BATCH}
 * @param parallelism number of concurrent reader/writer threads; 1 means sequential
 * @param coercionStrategy how to handle type mismatches between source and target columns
 * @param missingColumnStrategy how to handle target columns with no matching source field
 * @param deadLetterPath filesystem path for dead-letter CSV output; {@code null} disables
 *     dead-letter capture
 */
public record TransferOptions(
    int skipLimit,
    int retryLimit,
    int parallelism,
    TypeCoercionStrategy coercionStrategy,
    MissingColumnStrategy missingColumnStrategy,
    String deadLetterPath) {

  /** Validates and applies defaults. */
  public TransferOptions {
    if (skipLimit < 0) {
      throw new IllegalArgumentException("skipLimit must be >= 0");
    }
    if (retryLimit < 0) {
      throw new IllegalArgumentException("retryLimit must be >= 0");
    }
    if (parallelism < 1) {
      throw new IllegalArgumentException("parallelism must be >= 1");
    }
    coercionStrategy = coercionStrategy == null ? TypeCoercionStrategy.LENIENT : coercionStrategy;
    missingColumnStrategy =
        missingColumnStrategy == null ? MissingColumnStrategy.NULL_FILL : missingColumnStrategy;
  }

  /**
   * Returns a sensible default configuration: lenient coercion, null-fill missing columns, no
   * dead-letter output, fail fast on errors (skipLimit=0).
   *
   * @return default transfer options
   */
  public static TransferOptions defaults() {
    return new TransferOptions(
        0, 0, 1, TypeCoercionStrategy.LENIENT, MissingColumnStrategy.NULL_FILL, null);
  }
}
