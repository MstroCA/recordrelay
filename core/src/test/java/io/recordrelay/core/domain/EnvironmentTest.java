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
package io.recordrelay.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EnvironmentTest {

  @Test
  void shouldCreateValidEnvironment() {
    var env = new Environment("env-1", "Production", "Live environment");
    assertThat(env.id()).isEqualTo("env-1");
    assertThat(env.name()).isEqualTo("Production");
    assertThat(env.description()).isEqualTo("Live environment");
  }

  @Test
  void shouldDefaultDescriptionToEmptyString() {
    var env = new Environment("env-1", "Dev", null);
    assertThat(env.description()).isEmpty();
  }

  @Test
  void shouldRejectBlankId() {
    assertThatThrownBy(() -> new Environment("  ", "name", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id must not be blank");
  }

  @Test
  void shouldRejectBlankName() {
    assertThatThrownBy(() -> new Environment("id", "", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be blank");
  }

  @Test
  void shouldRejectNullId() {
    assertThatThrownBy(() -> new Environment(null, "name", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldRejectNullName() {
    assertThatThrownBy(() -> new Environment("id", null, null))
        .isInstanceOf(NullPointerException.class);
  }
}
