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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Integration tests for {@link RedisConnector} using a real Redis instance. */
@Tag("integration")
@Testcontainers
class RedisConnectorIT {

  @Container
  @SuppressWarnings("resource")
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private static ConnectionProfile profile;
  private final RedisConnector connector = new RedisConnector();

  @BeforeAll
  static void setup() {
    profile =
        new ConnectionProfile(
            "id",
            "redis-it",
            "test",
            DatabaseType.REDIS,
            REDIS.getHost(),
            REDIS.getMappedPort(6379),
            "",
            new Credentials("", ""),
            Map.of());
  }

  @Test
  void testConnectionSucceeds() throws Exception {
    connector.testConnection(profile);
  }

  @Test
  void listDatabasesReturnsSingleEntry() throws Exception {
    var dbs = connector.listDatabases(profile);
    assertThat(dbs).hasSize(1);
    assertThat(dbs.get(0).name()).isEqualTo("redis");
  }
}
