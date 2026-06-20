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

class ConnectionViewModelTest {

  private static ConnectionEntry entry(String type, String host, int port) {
    var e = new ConnectionEntry();
    e.setType(type);
    e.setHost(host);
    e.setPort(port);
    e.setDatabase("testdb");
    e.setUser("admin");
    e.setEncryptedPassword("ENC(AES256:dummyForTest)");
    return e;
  }

  @Test
  void loadOnEmptyStoreReturnsEmpty(@TempDir Path dir) throws Exception {
    var vm = new ConnectionViewModel(new ConfigStore(dir));
    vm.load();
    assertThat(vm.connectionsProperty()).isEmpty();
  }

  @Test
  void addConnectionAndLoad(@TempDir Path dir) throws Exception {
    var vm = new ConnectionViewModel(new ConfigStore(dir));
    vm.add("pg-prod", entry("POSTGRESQL", "db.example.com", 5432));
    vm.load();
    assertThat(vm.connectionsProperty()).extracting(e -> e.getKey()).containsExactly("pg-prod");
  }

  @Test
  void removeConnection(@TempDir Path dir) throws Exception {
    var vm = new ConnectionViewModel(new ConfigStore(dir));
    vm.add("pg-prod", entry("POSTGRESQL", "db1.example.com", 5432));
    vm.add("mongo-dev", entry("MONGODB", "db2.example.com", 27017));
    vm.remove("pg-prod");
    assertThat(vm.connectionsProperty()).extracting(e -> e.getKey()).containsExactly("mongo-dev");
  }

  @Test
  void duplicateNameReplacesExisting(@TempDir Path dir) throws Exception {
    var vm = new ConnectionViewModel(new ConfigStore(dir));
    vm.add("myconn", entry("POSTGRESQL", "host-v1", 5432));
    vm.add("myconn", entry("MYSQL", "host-v2", 3306));
    vm.load();
    assertThat(vm.connectionsProperty()).hasSize(1);
    assertThat(vm.connectionsProperty().get(0).getValue().getHost()).isEqualTo("host-v2");
  }

  @Test
  void errorClearedAfterSuccessfulLoad(@TempDir Path dir) throws Exception {
    var vm = new ConnectionViewModel(new ConfigStore(dir));
    vm.load();
    assertThat(vm.errorProperty().get()).isEmpty();
  }
}
