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
package io.recordrelay.engine.batch;

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
import io.recordrelay.core.engine.MappingTransformer;
import io.recordrelay.core.exception.ConnectorException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MappingTransformerTest {

  private static final TableRef SRC =
      new TableRef(new DatabaseRef("db", DatabaseType.POSTGRESQL), "public", "src");
  private static final TableRef TGT =
      new TableRef(new DatabaseRef("db", DatabaseType.POSTGRESQL), "public", "tgt");

  @Test
  void passthroughWhenNoColumnMappings() throws ConnectorException {
    var mapping = new MappingDefinition("m", SRC, TGT, List.of(), null, null);
    var transformer = new MappingTransformer(mapping, TransferOptions.defaults());
    var source = DataRecord.of(Map.of("id", 1, "name", "Alice"));

    var result = transformer.transform(source);

    assertThat(result.fields()).containsEntry("id", 1).containsEntry("name", "Alice");
  }

  @Test
  void renamesSourceColumnsToTargetNames() throws ConnectorException {
    var mappings =
        List.of(new ColumnMapping("username", "user_name"), new ColumnMapping("age", "user_age"));
    var mapping = new MappingDefinition("m", SRC, TGT, mappings, null, null);
    var transformer = new MappingTransformer(mapping, TransferOptions.defaults());
    var source = DataRecord.of(Map.of("username", "Bob", "age", 30, "email", "bob@test.com"));

    var result = transformer.transform(source);

    assertThat(result.fieldNames()).containsExactlyInAnyOrder("user_name", "user_age");
    assertThat(result.get("user_name")).isEqualTo("Bob");
    assertThat(result.get("user_age")).isEqualTo(30);
    assertThat(result.hasField("email")).isFalse();
  }

  @Test
  void fillsMissingColumnWithNullWhenStrategyIsNullFill() throws ConnectorException {
    var mappings =
        List.of(new ColumnMapping("existing", "existing"), new ColumnMapping("missing", "missing"));
    var mapping = new MappingDefinition("m", SRC, TGT, mappings, null, null);
    var options =
        new TransferOptions(
            0, 0, 1, TypeCoercionStrategy.LENIENT, MissingColumnStrategy.NULL_FILL, null);
    var transformer = new MappingTransformer(mapping, options);
    var source = DataRecord.of(Map.of("existing", "value"));

    var result = transformer.transform(source);

    assertThat(result.get("existing")).isEqualTo("value");
    assertThat(result.hasField("missing")).isTrue();
    assertThat(result.get("missing")).isNull();
  }

  @Test
  void failsOnMissingColumnWhenStrategyIsFail() {
    var mappings = List.of(new ColumnMapping("required", "required"));
    var mapping = new MappingDefinition("m", SRC, TGT, mappings, null, null);
    var options =
        new TransferOptions(
            0, 0, 1, TypeCoercionStrategy.LENIENT, MissingColumnStrategy.FAIL, null);
    var transformer = new MappingTransformer(mapping, options);
    var source = DataRecord.of(Map.of("other", "val"));

    assertThatThrownBy(() -> transformer.transform(source))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("required");
  }

  @Test
  void passesNullValuesThrough() throws ConnectorException {
    var mappings = List.of(new ColumnMapping("col", "col"));
    var mapping = new MappingDefinition("m", SRC, TGT, mappings, null, null);
    var transformer = new MappingTransformer(mapping, TransferOptions.defaults());
    var fields = new java.util.HashMap<String, Object>();
    fields.put("col", null);
    var source = DataRecord.of(fields);

    var result = transformer.transform(source);

    assertThat(result.hasField("col")).isTrue();
    assertThat(result.get("col")).isNull();
  }
}
