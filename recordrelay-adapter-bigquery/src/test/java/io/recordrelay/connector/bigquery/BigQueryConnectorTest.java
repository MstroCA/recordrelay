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
package io.recordrelay.connector.bigquery;

import static org.assertj.core.api.Assertions.assertThat;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DatabaseType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BigQueryConnectorTest {

  private final BigQueryConnector connector = new BigQueryConnector();

  private static ConnectionProfile profile(DatabaseType type) {
    return new ConnectionProfile(
        "bq-1",
        "bigquery-test",
        "env-1",
        type,
        "my-gcp-project",
        443,
        "my_dataset",
        // Empty password triggers Application Default Credentials path
        new Credentials("", ""),
        Map.of("location", "US"));
  }

  @Test
  void connectorIdShouldBeBigquery() {
    assertThat(connector.connectorId()).isEqualTo("bigquery");
  }

  @Test
  void shouldSupportBigQueryType() {
    assertThat(connector.supports(profile(DatabaseType.BIGQUERY))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"POSTGRESQL", "MONGODB", "SNOWFLAKE", "DYNAMODB", "MYSQL"})
  void shouldNotSupportOtherTypes(String typeName) {
    var type = DatabaseType.valueOf(typeName);
    assertThat(connector.supports(profile(type))).isFalse();
  }

  @Test
  void schemaInspectorIsCorrectType() {
    assertThat(connector.schemaInspector()).isInstanceOf(BigQuerySchemaInspector.class);
  }

  @Test
  void createReaderIsCorrectType() {
    assertThat(connector.createReader()).isInstanceOf(BigQueryRecordReader.class);
  }

  @Test
  void createWriterIsCorrectType() {
    assertThat(connector.createWriter()).isInstanceOf(BigQueryRecordWriter.class);
  }
}
