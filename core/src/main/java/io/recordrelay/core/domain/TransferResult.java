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

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Immutable outcome of a completed (or failed) {@link TransferJob} execution.
 *
 * <p>Factory methods {@link #success} and {@link #failed} cover the common terminal states.
 */
public record TransferResult(
    String jobId,
    TransferStatus status,
    long transferredCount,
    long failedCount,
    Duration duration,
    List<TransferError> errors) {

  /** Validates required fields and produces an immutable error list. */
  public TransferResult {
    Objects.requireNonNull(jobId, "jobId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(duration, "duration");
    if (transferredCount < 0) {
      throw new IllegalArgumentException("transferredCount cannot be negative");
    }
    if (failedCount < 0) {
      throw new IllegalArgumentException("failedCount cannot be negative");
    }
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  /**
   * Creates a fully successful result with no errors.
   *
   * @param jobId the job that completed
   * @param count the number of rows transferred
   * @param duration wall-clock time elapsed
   * @return a {@link TransferStatus#SUCCESS} result
   */
  public static TransferResult success(String jobId, long count, Duration duration) {
    return new TransferResult(jobId, TransferStatus.SUCCESS, count, 0L, duration, List.of());
  }

  /**
   * Creates a failed result where no rows were transferred.
   *
   * @param jobId the job that failed
   * @param duration wall-clock time elapsed until failure
   * @param errors the errors that caused the failure
   * @return a {@link TransferStatus#FAILED} result
   */
  public static TransferResult failed(String jobId, Duration duration, List<TransferError> errors) {
    return new TransferResult(jobId, TransferStatus.FAILED, 0L, errors.size(), duration, errors);
  }

  /** Returns true when the job completed without any errors. */
  public boolean isSuccessful() {
    return status == TransferStatus.SUCCESS && failedCount == 0;
  }
}
