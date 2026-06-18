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
 * Execution mode for a transfer job.
 *
 * <p>{@link #ASYNC} transfers report progress via {@link
 * io.recordrelay.core.port.out.TransferProgressListener} and are suitable for large datasets.
 * {@link #SYNC} transfers block until completion.
 */
public enum TransferMode {
  /** Blocking transfer; the caller waits for the result. */
  SYNC,
  /** Non-blocking transfer; progress is delivered via a listener callback. */
  ASYNC,
  /** Chunk-oriented transfer via Spring Batch with checkpoint/restart support. */
  BATCH
}
