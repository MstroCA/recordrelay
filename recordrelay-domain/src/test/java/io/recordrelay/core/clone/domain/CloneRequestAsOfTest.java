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
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for the point-in-time {@code asOf} field added to {@link CloneRequest}. */
class CloneRequestAsOfTest {

  private static ConnectionProfile profile(String id) {
    return new ConnectionProfile(
        id,
        "test",
        "env-1",
        DatabaseType.POSTGRESQL,
        "localhost",
        5432,
        "testdb",
        new Credentials("user", "pass"),
        Map.of());
  }

  @Test
  void builderAsOfDefaultsToNull() {
    var req = CloneRequest.builder(profile("src"), profile("tgt"), "orders", "1").build();
    assertThat(req.asOf()).isNull();
  }

  @Test
  void builderAsOfCanBeSet() {
    var ts = Instant.parse("2026-06-01T12:00:00Z");
    var req = CloneRequest.builder(profile("src"), profile("tgt"), "orders", "99").asOf(ts).build();
    assertThat(req.asOf()).isEqualTo(ts);
  }

  @Test
  void asOfInThePast() {
    var ts = Instant.ofEpochSecond(0); // 1970-01-01
    var req =
        CloneRequest.builder(profile("src"), profile("tgt"), "customers", "5").asOf(ts).build();
    assertThat(req.asOf()).isEqualTo(Instant.EPOCH);
  }

  @Test
  void asOfCanBeExplicitlySetToNull() {
    var req =
        CloneRequest.builder(profile("src"), profile("tgt"), "customers", "5").asOf(null).build();
    assertThat(req.asOf()).isNull();
  }

  @Test
  void asOfDoesNotAffectOtherFields() {
    var ts = Instant.parse("2026-01-15T08:30:00Z");
    var req =
        CloneRequest.builder(profile("src"), profile("tgt"), "invoices", "77")
            .depth(4)
            .asOf(ts)
            .build();
    assertThat(req.depth()).isEqualTo(4);
    assertThat(req.rootTable()).isEqualTo("invoices");
    assertThat(req.rootId()).isEqualTo("77");
    assertThat(req.asOf()).isEqualTo(ts);
  }

  @Test
  void relativeInstantsCompareCorrectly() {
    var past = Instant.parse("2025-01-01T00:00:00Z");
    var future = Instant.parse("2027-01-01T00:00:00Z");
    var req = CloneRequest.builder(profile("src"), profile("tgt"), "t", "1").asOf(past).build();
    assertThat(req.asOf()).isBefore(future);
    assertThat(req.asOf()).isAfter(Instant.EPOCH);
  }
}
