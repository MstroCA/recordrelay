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

class TransferViewModelTest {

  @Test
  void initialStateIsReady() {
    var vm = new TransferViewModel();
    assertThat(vm.statusTextProperty().get()).isEqualTo("Ready");
    assertThat(vm.progressProperty().get()).isEqualTo(0.0);
    assertThat(vm.busyProperty().get()).isFalse();
    assertThat(vm.errorProperty().get()).isEmpty();
  }

  @Test
  void resetProgressSetsStartingState() {
    var vm = new TransferViewModel();
    vm.resetProgress();
    assertThat(vm.statusTextProperty().get()).isEqualTo("Starting…");
    assertThat(vm.progressProperty().get()).isEqualTo(0.0);
    assertThat(vm.busyProperty().get()).isTrue();
    assertThat(vm.transferredProperty().get()).isEqualTo(0L);
    assertThat(vm.totalProperty().get()).isEqualTo(-1L);
  }

  @Test
  void sourceAndTargetTablePropertiesExist() {
    var vm = new TransferViewModel();
    assertThat(vm.sourceTableProperty().get()).isEmpty();
    assertThat(vm.targetTableProperty().get()).isEmpty();
    vm.sourceTableProperty().set("mydb.users");
    vm.targetTableProperty().set("dw.dim_users");
    assertThat(vm.sourceTableProperty().get()).isEqualTo("mydb.users");
    assertThat(vm.targetTableProperty().get()).isEqualTo("dw.dim_users");
  }

  @Test
  void defaultModeAndIsolationAreSet() {
    var vm = new TransferViewModel();
    assertThat(vm.modeProperty().get()).isEqualTo("SYNC");
    assertThat(vm.isolationProperty().get()).isEqualTo("READ_COMMITTED");
    assertThat(vm.chunkSizeProperty().get()).isEqualTo(1000L);
  }
}
