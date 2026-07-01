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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DatabaseType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SatelliteTableTest {

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
  void ofLeavesPkColumnNullForAutoDetection() {
    var sat = SatelliteTable.of(profile("src"), profile("tgt"), "read_model", "beyanname_id");
    assertThat(sat.table()).isEqualTo("read_model");
    assertThat(sat.linkColumn()).isEqualTo("beyanname_id");
    assertThat(sat.pkColumn()).isNull();
  }

  @Test
  void blankPkColumnIsNormalisedToNull() {
    var sat =
        new SatelliteTable(profile("src"), profile("tgt"), "read_model", "beyanname_id", "  ");
    assertThat(sat.pkColumn()).isNull();
  }

  @Test
  void explicitPkColumnIsPreserved() {
    var sat =
        new SatelliteTable(profile("src"), profile("tgt"), "read_model", "beyanname_id", "id");
    assertThat(sat.pkColumn()).isEqualTo("id");
  }

  @Test
  void nullSourceThrows() {
    assertThatThrownBy(
            () -> new SatelliteTable(null, profile("tgt"), "read_model", "beyanname_id", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("source");
  }

  @Test
  void blankTableThrows() {
    assertThatThrownBy(
            () -> new SatelliteTable(profile("src"), profile("tgt"), "  ", "beyanname_id", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("table");
  }

  @Test
  void blankLinkColumnThrows() {
    assertThatThrownBy(
            () -> new SatelliteTable(profile("src"), profile("tgt"), "read_model", "  ", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("linkColumn");
  }
}
