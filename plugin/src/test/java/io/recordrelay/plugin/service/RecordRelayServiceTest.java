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
package io.recordrelay.plugin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.recordrelay.cli.config.ConfigStore;
import io.recordrelay.cli.engine.ConnProfileResolver;
import io.recordrelay.plugin.editor.MappingValidator;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordRelayServiceTest {

  @Test
  void configStoreLoadsEmptyConfig(@TempDir Path tmpDir) throws Exception {
    var store = new ConfigStore(tmpDir);
    var config = store.load();
    assertThat(config.getConnections()).isEmpty();
    assertThat(config.getEnvironments()).isEmpty();
  }

  @Test
  void connProfileResolverThrowsForUnknownName(@TempDir Path tmpDir) throws Exception {
    var store = new ConfigStore(tmpDir);
    var resolver = new ConnProfileResolver(store);
    assertThatThrownBy(() -> resolver.resolve("nonexistent"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nonexistent");
  }

  @Test
  void mappingValidatorInfersJsonFormat() {
    assertThat(MappingValidator.inferFormat("mappings.json")).isEqualTo("json");
    assertThat(MappingValidator.inferFormat("MAPPINGS.JSON")).isEqualTo("json");
  }

  @Test
  void mappingValidatorInfersYamlFormat() {
    assertThat(MappingValidator.inferFormat("mappings.yaml")).isEqualTo("yaml");
    assertThat(MappingValidator.inferFormat("mappings.yml")).isEqualTo("yaml");
  }

  @Test
  void mappingValidatorReturnsNullForUnknownExtension() {
    assertThat(MappingValidator.inferFormat("mappings.sql")).isNull();
    assertThat(MappingValidator.inferFormat(null)).isNull();
  }
}
