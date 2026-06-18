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
package io.recordrelay.core.port.in;

import io.recordrelay.core.domain.TransferJob;
import io.recordrelay.core.domain.TransferResult;
import io.recordrelay.core.port.out.TransferProgressListener;

/**
 * Driving port: initiates and monitors ETL transfer jobs.
 *
 * <p>The synchronous overload blocks until the transfer completes. The asynchronous overload
 * returns immediately and delivers progress updates via the supplied listener; the returned {@link
 * TransferResult} reflects the final state once the listener's {@link
 * TransferProgressListener#onComplete} has been invoked.
 */
public interface TransferUseCase {

  /**
   * Executes a synchronous transfer and blocks until it completes.
   *
   * @param job the fully configured transfer job
   * @return the final result
   */
  TransferResult transfer(TransferJob job);

  /**
   * Initiates a transfer and delivers progress events to {@code listener}.
   *
   * <p>For {@link io.recordrelay.core.domain.TransferMode#SYNC} jobs the listener is still called
   * but the method still blocks. For {@link io.recordrelay.core.domain.TransferMode#ASYNC} jobs the
   * method returns after the transfer has been submitted to the execution thread.
   *
   * @param job the fully configured transfer job
   * @param listener the progress listener; use {@link TransferProgressListener#noop()} to ignore
   * @return the final result (may not be complete yet for ASYNC jobs at the time of return)
   */
  TransferResult transfer(TransferJob job, TransferProgressListener listener);
}
