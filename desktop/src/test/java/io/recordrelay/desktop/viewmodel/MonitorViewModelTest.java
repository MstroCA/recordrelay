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
package io.recordrelay.desktop.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MonitorViewModelTest {

  @Test
  void initialCountersAreZero() {
    var vm = new MonitorViewModel();
    assertThat(vm.transferredCountProperty().get()).isEqualTo(0L);
    assertThat(vm.failedCountProperty().get()).isEqualTo(0L);
    assertThat(vm.throughputNowProperty().get()).isEqualTo(0L);
    assertThat(vm.durationProperty().get()).isEqualTo("—");
    assertThat(vm.latencyP50Property().get()).isEqualTo("—");
    assertThat(vm.latencyP95Property().get()).isEqualTo("—");
    assertThat(vm.latencyP99Property().get()).isEqualTo("—");
  }

  @Test
  void recordThroughputAddsDataPoint() {
    var vm = new MonitorViewModel();
    vm.recordThroughput(42L);
    assertThat(vm.throughputNowProperty().get()).isEqualTo(42L);
    assertThat(vm.throughputSeries().getData()).hasSize(1);
    assertThat(vm.throughputSeries().getData().get(0).getYValue()).isEqualTo(42L);
  }

  @Test
  void updateCountersSetsValues() {
    var vm = new MonitorViewModel();
    vm.updateCounters(1500L, 3L, "00:02:30");
    assertThat(vm.transferredCountProperty().get()).isEqualTo(1500L);
    assertThat(vm.failedCountProperty().get()).isEqualTo(3L);
    assertThat(vm.durationProperty().get()).isEqualTo("00:02:30");
  }

  @Test
  void updateLatencyFormatsLabels() {
    var vm = new MonitorViewModel();
    vm.updateLatency(12L, 45L, 120L);
    assertThat(vm.latencyP50Property().get()).isEqualTo("12 ms");
    assertThat(vm.latencyP95Property().get()).isEqualTo("45 ms");
    assertThat(vm.latencyP99Property().get()).isEqualTo("120 ms");
  }

  @Test
  void slidingWindowCapsAt60Points() {
    var vm = new MonitorViewModel();
    for (int i = 0; i < 70; i++) {
      vm.recordThroughput(i);
    }
    assertThat(vm.throughputSeries().getData()).hasSize(60);
  }
}
