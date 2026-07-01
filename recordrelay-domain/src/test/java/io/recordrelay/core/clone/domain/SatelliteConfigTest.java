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
package io.recordrelay.core.clone.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DatabaseType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SatelliteConfigTest {

  private static ConnectionProfile profile(String id) {
    return new ConnectionProfile(
        id,
        "test",
        "env-1",
        DatabaseType.POSTGRESQL,
        "localhost",
        5432,
        "db",
        new Credentials("user", "pass"),
        Map.of());
  }

  @Test
  void noneIsEmpty() {
    assertThat(SatelliteConfig.none().isEmpty()).isTrue();
    assertThat(SatelliteConfig.none().satellites()).isEmpty();
  }

  @Test
  void nullSatellitesDefaultsToEmpty() {
    assertThat(new SatelliteConfig(null).isEmpty()).isTrue();
  }

  @Test
  void carriesSatellites() {
    var sat = SatelliteTable.of(profile("src"), profile("tgt"), "read_model", "beyanname_id");
    var config = new SatelliteConfig(List.of(sat));
    assertThat(config.isEmpty()).isFalse();
    assertThat(config.satellites()).containsExactly(sat);
  }
}
