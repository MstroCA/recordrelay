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
package io.recordrelay.connector.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DatabaseType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MongoDbConnectorTest {

  private final MongoDbConnector connector = new MongoDbConnector();

  private static ConnectionProfile profile(DatabaseType type) {
    return new ConnectionProfile(
        "m1",
        "test",
        "env-1",
        type,
        "localhost",
        27017,
        "admin",
        new Credentials("user", "pass"),
        Map.of());
  }

  @Test
  void connectorIdShouldBeMongodb() {
    assertThat(connector.connectorId()).isEqualTo("mongodb");
  }

  @Test
  void shouldSupportMongodbType() {
    assertThat(connector.supports(profile(DatabaseType.MONGODB))).isTrue();
  }

  @Test
  void shouldNotSupportOtherTypes() {
    assertThat(connector.supports(profile(DatabaseType.POSTGRESQL))).isFalse();
    assertThat(connector.supports(profile(DatabaseType.CASSANDRA))).isFalse();
    assertThat(connector.supports(profile(DatabaseType.REDIS))).isFalse();
  }
}
