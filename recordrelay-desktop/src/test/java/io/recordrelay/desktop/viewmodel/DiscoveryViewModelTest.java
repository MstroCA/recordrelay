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
import io.recordrelay.cli.config.ConnectionEntry;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiscoveryViewModelTest {

  private static ConnectionEntry pgEntry() {
    var e = new ConnectionEntry();
    e.setType("POSTGRESQL");
    e.setHost("localhost");
    e.setPort(5432);
    e.setDatabase("mydb");
    e.setUser("admin");
    e.setEncryptedPassword("ENC(AES256:dummy)");
    return e;
  }

  @Test
  void loadConnectionsOnEmptyStoreReturnsEmpty(@TempDir Path dir) throws Exception {
    var vm = new DiscoveryViewModel(new ConfigStore(dir));
    vm.loadConnections();
    assertThat(vm.connNamesProperty()).isEmpty();
  }

  @Test
  void loadConnectionsPopulatesConnNames(@TempDir Path dir) throws Exception {
    var store = new ConfigStore(dir);
    store.addConnection("pg-prod", pgEntry());
    store.addConnection("pg-dev", pgEntry());
    var vm = new DiscoveryViewModel(store);
    vm.loadConnections();
    assertThat(vm.connNamesProperty()).containsExactlyInAnyOrder("pg-prod", "pg-dev");
  }

  @Test
  void errorClearedAfterSuccessfulLoad(@TempDir Path dir) throws Exception {
    var vm = new DiscoveryViewModel(new ConfigStore(dir));
    vm.loadConnections();
    assertThat(vm.errorProperty().get()).isEmpty();
  }

  @Test
  void sourceDatabasesInitiallyEmpty(@TempDir Path dir) throws Exception {
    var vm = new DiscoveryViewModel(new ConfigStore(dir));
    assertThat(vm.sourceDatabasesProperty()).isEmpty();
    assertThat(vm.targetDatabasesProperty()).isEmpty();
  }
}
