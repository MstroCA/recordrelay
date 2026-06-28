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
package io.recordrelay.connector.snowflake;

import static org.assertj.core.api.Assertions.assertThat;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DatabaseType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnowflakeConnectorTest {

  private final SnowflakeConnector connector = new SnowflakeConnector();

  private static ConnectionProfile profile(DatabaseType type) {
    return new ConnectionProfile(
        "sf-1",
        "snowflake-test",
        "env-1",
        type,
        "myorg-myaccount.snowflakecomputing.com",
        443,
        "MY_DATABASE",
        new Credentials("user", "password"),
        Map.of("warehouse", "COMPUTE_WH", "schema", "PUBLIC"));
  }

  @Test
  void connectorIdShouldBeSnowflake() {
    assertThat(connector.connectorId()).isEqualTo("snowflake");
  }

  @Test
  void shouldSupportSnowflakeType() {
    assertThat(connector.supports(profile(DatabaseType.SNOWFLAKE))).isTrue();
  }

  @Test
  void shouldNotSupportPostgresql() {
    assertThat(connector.supports(profile(DatabaseType.POSTGRESQL))).isFalse();
  }

  @Test
  void shouldNotSupportMongodb() {
    assertThat(connector.supports(profile(DatabaseType.MONGODB))).isFalse();
  }

  @Test
  void shouldNotSupportBigQuery() {
    assertThat(connector.supports(profile(DatabaseType.BIGQUERY))).isFalse();
  }

  @Test
  void shouldNotSupportDynamoDb() {
    assertThat(connector.supports(profile(DatabaseType.DYNAMODB))).isFalse();
  }

  @Test
  void schemaInspectorIsNotNull() {
    assertThat(connector.schemaInspector()).isNotNull();
    assertThat(connector.schemaInspector()).isInstanceOf(SnowflakeSchemaInspector.class);
  }

  @Test
  void createReaderIsNotNull() {
    assertThat(connector.createReader()).isNotNull();
    assertThat(connector.createReader()).isInstanceOf(SnowflakeRecordReader.class);
  }

  @Test
  void createWriterIsNotNull() {
    assertThat(connector.createWriter()).isNotNull();
    assertThat(connector.createWriter()).isInstanceOf(SnowflakeRecordWriter.class);
  }

  @Test
  void listDatabasesReturnsSingleEntryWithDatabaseName() {
    var dbs = connector.listDatabases(profile(DatabaseType.SNOWFLAKE));
    assertThat(dbs).hasSize(1);
    assertThat(dbs.get(0).name()).isEqualTo("MY_DATABASE");
    assertThat(dbs.get(0).type()).isEqualTo(DatabaseType.SNOWFLAKE);
  }
}
