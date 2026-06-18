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
package io.recordrelay.connector.redis;

import static org.assertj.core.api.Assertions.assertThat;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DatabaseType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RedisConnector}. */
class RedisConnectorTest {

  private final RedisConnector connector = new RedisConnector();

  @Test
  void connectorIdIsRedis() {
    assertThat(connector.connectorId()).isEqualTo("redis");
  }

  @Test
  void supportsRedisProfiles() {
    assertThat(connector.supports(profile(DatabaseType.REDIS))).isTrue();
  }

  @Test
  void doesNotSupportMysql() {
    assertThat(connector.supports(profile(DatabaseType.MYSQL))).isFalse();
  }

  @Test
  void doesNotSupportCassandra() {
    assertThat(connector.supports(profile(DatabaseType.CASSANDRA))).isFalse();
  }

  @Test
  void listDatabasesReturnsSingleRedisEntry() throws Exception {
    var p = profile(DatabaseType.REDIS);
    var dbs = connector.listDatabases(p);
    assertThat(dbs).hasSize(1);
    assertThat(dbs.get(0).name()).isEqualTo("redis");
  }

  private ConnectionProfile profile(DatabaseType type) {
    return new ConnectionProfile(
        "id", "test", "env", type, "localhost", 6379, "", new Credentials("", ""), Map.of());
  }
}
