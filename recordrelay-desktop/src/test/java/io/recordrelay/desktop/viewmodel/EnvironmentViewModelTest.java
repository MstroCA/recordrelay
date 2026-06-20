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

import io.recordrelay.cli.config.ConfigStore;
import io.recordrelay.cli.config.EnvironmentEntry;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvironmentViewModelTest {

  @Test
  void loadOnEmptyStoreReturnsEmpty(@TempDir Path dir) throws Exception {
    var vm = new EnvironmentViewModel(new ConfigStore(dir));
    vm.load();
    assertThat(vm.environmentsProperty()).isEmpty();
  }

  @Test
  void addThenLoadShowsEntry(@TempDir Path dir) throws Exception {
    var vm = new EnvironmentViewModel(new ConfigStore(dir));
    vm.add("prod", "Production");
    vm.load();
    assertThat(vm.environmentsProperty())
        .extracting(EnvironmentEntry::getName)
        .containsExactly("prod");
  }

  @Test
  void removeEntry(@TempDir Path dir) throws Exception {
    var vm = new EnvironmentViewModel(new ConfigStore(dir));
    vm.add("dev", "Development");
    vm.add("staging", "Staging");
    vm.remove("dev");
    assertThat(vm.environmentsProperty())
        .extracting(EnvironmentEntry::getName)
        .containsExactly("staging");
  }

  @Test
  void errorClearedAfterSuccessfulLoad(@TempDir Path dir) throws Exception {
    var vm = new EnvironmentViewModel(new ConfigStore(dir));
    vm.load();
    assertThat(vm.errorProperty().get()).isEmpty();
  }
}
