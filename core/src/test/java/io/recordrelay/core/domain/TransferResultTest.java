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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransferResultTest {

  @Test
  void successFactoryShouldProduceCleanResult() {
    var result = TransferResult.success("job-1", 1_000L, Duration.ofSeconds(10));
    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.transferredCount()).isEqualTo(1_000L);
    assertThat(result.failedCount()).isZero();
    assertThat(result.errors()).isEmpty();
  }

  @Test
  void failedFactoryShouldCaptureErrors() {
    var errors = List.of(TransferError.batchError("flush failed", "users"));
    var result = TransferResult.failed("job-2", Duration.ofMillis(500), errors);
    assertThat(result.status()).isEqualTo(TransferStatus.FAILED);
    assertThat(result.failedCount()).isEqualTo(1L);
    assertThat(result.errors()).hasSize(1);
    assertThat(result.isSuccessful()).isFalse();
  }

  @Test
  void shouldProduceImmutableErrorList() {
    var result = TransferResult.success("job-3", 5L, Duration.ZERO);
    assertThatThrownBy(() -> result.errors().add(TransferError.batchError("x", "t")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldRejectNegativeTransferredCount() {
    assertThatThrownBy(
            () -> new TransferResult("job-4", TransferStatus.SUCCESS, -1L, 0L, Duration.ZERO, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
