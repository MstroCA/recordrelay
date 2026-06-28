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
package io.recordrelay.connector.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DatabaseType;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class DynamoDbConnectorTest {

  private final DynamoDbConnector connector = new DynamoDbConnector();

  private static ConnectionProfile profile(DatabaseType type) {
    return new ConnectionProfile(
        "ddb-1",
        "dynamodb-test",
        "env-1",
        type,
        "us-east-1",
        443,
        "ignored",
        new Credentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"),
        Map.of());
  }

  // ── connector metadata ────────────────────────────────────────────────────

  @Test
  void connectorIdShouldBeDynamodb() {
    assertThat(connector.connectorId()).isEqualTo("dynamodb");
  }

  @Test
  void shouldSupportDynamoDbType() {
    assertThat(connector.supports(profile(DatabaseType.DYNAMODB))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"POSTGRESQL", "MONGODB", "SNOWFLAKE", "BIGQUERY", "MYSQL"})
  void shouldNotSupportOtherTypes(String typeName) {
    var type = DatabaseType.valueOf(typeName);
    assertThat(connector.supports(profile(type))).isFalse();
  }

  @Test
  void schemaInspectorIsCorrectType() {
    assertThat(connector.schemaInspector()).isInstanceOf(DynamoDbSchemaInspector.class);
  }

  @Test
  void createReaderIsCorrectType() {
    assertThat(connector.createReader()).isInstanceOf(DynamoDbRecordReader.class);
  }

  @Test
  void createWriterIsCorrectType() {
    assertThat(connector.createWriter()).isInstanceOf(DynamoDbRecordWriter.class);
  }

  @Test
  void listDatabasesReturnsSingleRegionEntry() {
    var dbs = connector.listDatabases(profile(DatabaseType.DYNAMODB));
    assertThat(dbs).hasSize(1);
    assertThat(dbs.get(0).name()).isEqualTo("us-east-1");
    assertThat(dbs.get(0).type()).isEqualTo(DatabaseType.DYNAMODB);
  }

  // ── AttributeValues conversion ────────────────────────────────────────────

  @Test
  void attributeValueStringRoundTrip() {
    var av = AttributeValues.fromObject("hello");
    assertThat(av.s()).isEqualTo("hello");
    assertThat(AttributeValues.toObject(av)).isEqualTo("hello");
  }

  @Test
  void attributeValueIntegerRoundTrip() {
    var av = AttributeValues.fromObject(42);
    assertThat(av.n()).isEqualTo("42");
    Object back = AttributeValues.toObject(av);
    assertThat(back).isInstanceOf(BigDecimal.class);
    assertThat(((BigDecimal) back).intValue()).isEqualTo(42);
  }

  @Test
  void attributeValueLongRoundTrip() {
    var av = AttributeValues.fromObject(Long.MAX_VALUE);
    assertThat(av.n()).isEqualTo(String.valueOf(Long.MAX_VALUE));
  }

  @Test
  void attributeValueBooleanTrueRoundTrip() {
    var av = AttributeValues.fromObject(Boolean.TRUE);
    assertThat(av.bool()).isTrue();
    assertThat(AttributeValues.toObject(av)).isEqualTo(Boolean.TRUE);
  }

  @Test
  void attributeValueBooleanFalseRoundTrip() {
    var av = AttributeValues.fromObject(Boolean.FALSE);
    assertThat(av.bool()).isFalse();
    assertThat(AttributeValues.toObject(av)).isEqualTo(Boolean.FALSE);
  }

  @Test
  void attributeValueNullBecomesNulTrue() {
    var av = AttributeValues.fromObject(null);
    assertThat(av.nul()).isTrue();
    assertThat(AttributeValues.toObject(av)).isNull();
  }

  @Test
  void attributeValueEmptyStringBecomesSpace() {
    // DynamoDB rejects empty strings; AttributeValues replaces with " "
    var av = AttributeValues.fromObject("");
    assertThat(av.s()).isEqualTo(" ");
  }

  @Test
  void attributeValueNullAvReturnsNull() {
    assertThat(AttributeValues.toObject(null)).isNull();
  }

  @Test
  void attributeValueByteArrayRoundTrip() {
    byte[] bytes = {1, 2, 3};
    var av = AttributeValues.fromObject(bytes);
    assertThat(av.b()).isNotNull();
    Object back = AttributeValues.toObject(av);
    assertThat(back).isInstanceOf(byte[].class);
    assertThat((byte[]) back).containsExactly(1, 2, 3);
  }

  @Test
  void attributeValueUnknownTypeFallsBackToString() {
    // A custom object that is not String/Number/Boolean/byte[]
    var obj = new java.awt.Point(1, 2);
    var av = AttributeValues.fromObject(obj);
    assertThat(av.s()).isNotNull();
    assertThat(av.s()).isEqualTo(obj.toString());
  }

  @Test
  void attributeValueStringListRoundTrip() {
    var av = AttributeValue.builder().ss("a", "b", "c").build();
    var back = AttributeValues.toObject(av);
    assertThat(back).isInstanceOf(java.util.List.class);
  }

  @Test
  void attributeValueNestedMapReturnedAsMap() {
    var inner = AttributeValue.fromS("nested");
    var av = AttributeValue.builder().m(Map.of("key", inner)).build();
    var back = AttributeValues.toObject(av);
    assertThat(back).isInstanceOf(Map.class);
    @SuppressWarnings("unchecked")
    var map = (Map<String, Object>) back;
    assertThat(map.get("key")).isEqualTo("nested");
  }
}
