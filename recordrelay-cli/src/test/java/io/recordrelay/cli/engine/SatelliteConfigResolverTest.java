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
package io.recordrelay.cli.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.recordrelay.cli.config.ConfigStore;
import io.recordrelay.cli.config.ConnectionEntry;
import io.recordrelay.cli.config.SatelliteEntry;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SatelliteConfigResolverTest {

  @TempDir Path tempDir;

  @Test
  void forEntityReturnsEmptyWhenNoneConfigured() throws Exception {
    var resolver = newResolver();
    assertThat(resolver.forEntity("beyanname")).isEmpty();
    assertThat(resolver.configForEntity("beyanname").isEmpty()).isTrue();
  }

  @Test
  void forEntityResolvesConfiguredSatellite() throws Exception {
    var store = new ConfigStore(tempDir);
    store.addConnection("prod-user", conn(store));
    store.addConnection("test-user", conn(store));

    var config = store.load();
    var entry = new SatelliteEntry();
    entry.setSourceConn("prod-user");
    entry.setTargetConn("test-user");
    entry.setTable("read_model");
    entry.setLinkColumn("beyanname_id");
    config.getSatellites().put("beyanname", List.of(entry));
    store.save(config);

    var satellites = new SatelliteConfigResolver(store, new ConnProfileResolver(store));
    var resolved = satellites.forEntity("beyanname");

    assertThat(resolved).hasSize(1);
    assertThat(resolved.get(0).table()).isEqualTo("read_model");
    assertThat(resolved.get(0).linkColumn()).isEqualTo("beyanname_id");
    assertThat(resolved.get(0).pkColumn()).isNull();
    assertThat(resolved.get(0).source().database()).isEqualTo("userdb");
    assertThat(resolved.get(0).target().database()).isEqualTo("userdb");
  }

  @Test
  void incompleteEntryIsSkipped() throws Exception {
    var store = new ConfigStore(tempDir);
    store.addConnection("prod-user", conn(store));

    var config = store.load();
    var entry = new SatelliteEntry();
    entry.setSourceConn("prod-user"); // missing target/table/link
    config.getSatellites().put("beyanname", List.of(entry));
    store.save(config);

    var satellites = new SatelliteConfigResolver(store, new ConnProfileResolver(store));
    assertThat(satellites.forEntity("beyanname")).isEmpty();
  }

  @Test
  void parseAcceptsFullSpecWithPkColumn() throws Exception {
    var store = new ConfigStore(tempDir);
    store.addConnection("prod-user", conn(store));
    store.addConnection("test-user", conn(store));

    var satellites = new SatelliteConfigResolver(store, new ConnProfileResolver(store));
    var sat = satellites.parse("prod-user>test-user:read_model.beyanname_id.id");

    assertThat(sat.table()).isEqualTo("read_model");
    assertThat(sat.linkColumn()).isEqualTo("beyanname_id");
    assertThat(sat.pkColumn()).isEqualTo("id");
  }

  @Test
  void parseRejectsMissingColon() {
    var satellites = new SatelliteConfigResolver(newStore(), newProfileResolver());
    assertThatThrownBy(() -> satellites.parse("prod-user>test-user"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseRejectsMissingArrow() {
    var satellites = new SatelliteConfigResolver(newStore(), newProfileResolver());
    assertThatThrownBy(() -> satellites.parse("prod-user:read_model.beyanname_id"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseRejectsMissingLinkColumn() {
    var satellites = new SatelliteConfigResolver(newStore(), newProfileResolver());
    assertThatThrownBy(() -> satellites.parse("prod-user>test-user:read_model"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private SatelliteConfigResolver newResolver() throws Exception {
    var store = new ConfigStore(tempDir);
    return new SatelliteConfigResolver(store, new ConnProfileResolver(store));
  }

  private ConfigStore newStore() {
    try {
      return new ConfigStore(tempDir);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private ConnProfileResolver newProfileResolver() {
    return new ConnProfileResolver(newStore());
  }

  private ConnectionEntry conn(ConfigStore store) throws Exception {
    var entry = new ConnectionEntry();
    entry.setEnvironment("dev");
    entry.setType("POSTGRESQL");
    entry.setHost("localhost");
    entry.setPort(5432);
    entry.setDatabase("userdb");
    entry.setUser("admin");
    entry.setEncryptedPassword(store.encryptor().encrypt("secret"));
    return entry;
  }
}
