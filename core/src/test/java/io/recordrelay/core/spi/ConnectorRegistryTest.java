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
package io.recordrelay.core.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.exception.NoConnectorFoundException;
import io.recordrelay.core.port.out.DataSourceConnector;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectorRegistryTest {

  @Mock private DataSourceConnector connector;

  private static ConnectionProfile profile(DatabaseType type) {
    return new ConnectionProfile(
        "p1", "test", "env-1", type, "localhost", 5432, "db", new Credentials("u", "p"), Map.of());
  }

  @Test
  void loadFromShouldReturnEmptyListForUnknownClassLoader() {
    var classLoader = new ClassLoader(null) {};
    var connectors = ConnectorRegistry.loadFrom(classLoader);
    assertThat(connectors).isEmpty();
  }

  @Test
  void allConnectorsShouldReturnImmutableList() {
    var all = ConnectorRegistry.allConnectors();
    assertThatThrownBy(() -> all.add(connector)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void findConnectorShouldThrowWhenNoConnectorMatches() {
    // No connectors are on the test classpath (no META-INF/services file in core)
    var profile = profile(DatabaseType.ORACLE);
    assertThatThrownBy(() -> ConnectorRegistry.findConnector(profile))
        .isInstanceOf(NoConnectorFoundException.class)
        .hasMessageContaining("ORACLE");
  }

  @Test
  void mockConnectorShouldSupportMatchingType() throws ConnectorException {
    var profile = profile(DatabaseType.POSTGRESQL);
    when(connector.supports(profile)).thenReturn(true);
    when(connector.listDatabases(profile))
        .thenReturn(List.of(new DatabaseRef("mydb", DatabaseType.POSTGRESQL)));

    assertThat(connector.supports(profile)).isTrue();
    assertThat(connector.listDatabases(profile)).hasSize(1);
  }
}
