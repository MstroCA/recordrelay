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
package io.recordrelay.connector.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DatabaseType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link TemplateConnector}. */
class TemplateConnectorTest {

  private final TemplateConnector connector = new TemplateConnector();

  @Test
  void connectorIdIsTemplate() {
    assertThat(connector.connectorId()).isEqualTo("template");
  }

  @Test
  void doesNotSupportAnyProfile() {
    for (DatabaseType type : DatabaseType.values()) {
      var profile =
          new ConnectionProfile(
              "id",
              "test",
              "env",
              type,
              "localhost",
              5432,
              "db",
              new Credentials("user", "pass"),
              Map.of());
      assertThat(connector.supports(profile))
          .as("Template connector must not match any DatabaseType: " + type)
          .isFalse();
    }
  }
}
