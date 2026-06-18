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

import java.util.Objects;

/**
 * Immutable specification for a single low-level data transfer operation (internal).
 *
 * <p>A job references the source and target {@link ConnectionProfile}s, the {@link
 * MappingDefinition}, and tuning parameters. It does not hold runtime state; execution state lives
 * in {@link TransferResult}.
 */
public record TransferJob(
    String id,
    String name,
    ConnectionProfile source,
    ConnectionProfile target,
    MappingDefinition mapping,
    TransferMode mode,
    int batchSize,
    TransactionIsolation isolation,
    TransferOptions options) {

  /** Default chunk size used when none is supplied. */
  public static final int DEFAULT_BATCH_SIZE = 1_000;

  /** Validates required fields and applies sensible defaults. */
  public TransferJob {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(mapping, "mapping");
    mode = mode == null ? TransferMode.SYNC : mode;
    isolation = isolation == null ? TransactionIsolation.READ_COMMITTED : isolation;
    if (batchSize <= 0) {
      batchSize = DEFAULT_BATCH_SIZE;
    }
    options = options == null ? TransferOptions.defaults() : options;
  }
}
