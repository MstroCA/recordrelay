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
package io.recordrelay.core.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.recordrelay.core.domain.ColumnMapping;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.MissingColumnStrategy;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.domain.TransferOptions;
import io.recordrelay.core.domain.TypeCoercionStrategy;
import io.recordrelay.core.exception.ConnectorException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MappingTransformerCoreTest {

  private static final TableRef SRC =
      new TableRef(new DatabaseRef("db", DatabaseType.POSTGRESQL), "public", "src");
  private static final TableRef TGT =
      new TableRef(new DatabaseRef("db", DatabaseType.POSTGRESQL), "public", "tgt");

  @Test
  void passthroughWhenNoColumnMappings() throws ConnectorException {
    var mapping = new MappingDefinition("m", SRC, TGT, List.of(), null, null);
    var transformer = new MappingTransformer(mapping, TransferOptions.defaults());

    var result = transformer.transform(DataRecord.of(Map.of("id", 1, "name", "Alice")));

    assertThat(result.fields()).containsEntry("id", 1).containsEntry("name", "Alice");
  }

  @Test
  void renamesColumnsAccordingToMapping() throws ConnectorException {
    var mappings = List.of(new ColumnMapping("name", "full_name"));
    var mapping = new MappingDefinition("m", SRC, TGT, mappings, null, null);
    var transformer = new MappingTransformer(mapping, TransferOptions.defaults());

    var result = transformer.transform(DataRecord.of(Map.of("name", "Bob", "age", 30)));

    assertThat(result.hasField("full_name")).isTrue();
    assertThat(result.get("full_name")).isEqualTo("Bob");
    assertThat(result.hasField("name")).isFalse();
    assertThat(result.hasField("age")).isFalse();
  }

  @Test
  void nullFillsForMissingColumns() throws ConnectorException {
    var mappings =
        List.of(new ColumnMapping("present", "present"), new ColumnMapping("absent", "absent"));
    var mapping = new MappingDefinition("m", SRC, TGT, mappings, null, null);
    var options =
        new TransferOptions(
            0, 0, 1, TypeCoercionStrategy.LENIENT, MissingColumnStrategy.NULL_FILL, null);
    var transformer = new MappingTransformer(mapping, options);

    var result = transformer.transform(DataRecord.of(Map.of("present", "yes")));

    assertThat(result.get("present")).isEqualTo("yes");
    assertThat(result.hasField("absent")).isTrue();
    assertThat(result.get("absent")).isNull();
  }

  @Test
  void failStrategyThrowsOnMissingColumn() {
    var mappings = List.of(new ColumnMapping("required_col", "required_col"));
    var mapping = new MappingDefinition("m", SRC, TGT, mappings, null, null);
    var options =
        new TransferOptions(
            0, 0, 1, TypeCoercionStrategy.LENIENT, MissingColumnStrategy.FAIL, null);
    var transformer = new MappingTransformer(mapping, options);

    assertThatThrownBy(() -> transformer.transform(DataRecord.of(Map.of("other", 1))))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("required_col");
  }

  @Test
  void strictStrategyPassesValueAsIs() throws ConnectorException {
    var mappings = List.of(new ColumnMapping("amount_int", "amount_int"));
    var mapping = new MappingDefinition("m", SRC, TGT, mappings, null, null);
    var options =
        new TransferOptions(
            0, 0, 1, TypeCoercionStrategy.STRICT, MissingColumnStrategy.NULL_FILL, null);
    var transformer = new MappingTransformer(mapping, options);

    var result = transformer.transform(DataRecord.of(Map.of("amount_int", "42")));

    assertThat(result.get("amount_int")).isEqualTo("42");
  }

  @Test
  void lenientStrategyConvertsStringToLong() throws ConnectorException {
    var mappings = List.of(new ColumnMapping("score_int", "score_int"));
    var mapping = new MappingDefinition("m", SRC, TGT, mappings, null, null);
    var transformer = new MappingTransformer(mapping, TransferOptions.defaults());

    var result = transformer.transform(DataRecord.of(Map.of("score_int", "99")));

    assertThat(result.get("score_int")).isEqualTo(99L);
  }

  @Test
  void lenientStrategyConvertsStringToDouble() throws ConnectorException {
    var mappings = List.of(new ColumnMapping("price_decimal", "price_decimal"));
    var mapping = new MappingDefinition("m", SRC, TGT, mappings, null, null);
    var transformer = new MappingTransformer(mapping, TransferOptions.defaults());

    var result = transformer.transform(DataRecord.of(Map.of("price_decimal", "3.14")));

    assertThat(result.get("price_decimal")).isEqualTo(3.14);
  }

  @Test
  void lenientStrategyConvertsStringToBoolean() throws ConnectorException {
    var mappings = List.of(new ColumnMapping("active_boolean", "active_boolean"));
    var mapping = new MappingDefinition("m", SRC, TGT, mappings, null, null);
    var transformer = new MappingTransformer(mapping, TransferOptions.defaults());

    var result = transformer.transform(DataRecord.of(Map.of("active_boolean", "true")));

    assertThat(result.get("active_boolean")).isEqualTo(true);
  }

  @Test
  void nullValuesPassedThrough() throws ConnectorException {
    var mappings = List.of(new ColumnMapping("col", "col"));
    var mapping = new MappingDefinition("m", SRC, TGT, mappings, null, null);
    var transformer = new MappingTransformer(mapping, TransferOptions.defaults());
    var fields = new java.util.HashMap<String, Object>();
    fields.put("col", null);

    var result = transformer.transform(DataRecord.of(fields));

    assertThat(result.hasField("col")).isTrue();
    assertThat(result.get("col")).isNull();
  }

  @Test
  void badStringToNumberThrowsConnectorException() {
    var mappings = List.of(new ColumnMapping("count_int", "count_int"));
    var mapping = new MappingDefinition("m", SRC, TGT, mappings, null, null);
    var options =
        new TransferOptions(
            0, 0, 1, TypeCoercionStrategy.LENIENT, MissingColumnStrategy.NULL_FILL, null);
    var transformer = new MappingTransformer(mapping, options);

    assertThatThrownBy(
            () -> transformer.transform(DataRecord.of(Map.of("count_int", "not-a-number"))))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("count_int");
  }
}
