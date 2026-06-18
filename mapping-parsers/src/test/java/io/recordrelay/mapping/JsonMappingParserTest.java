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
package io.recordrelay.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.domain.MappingFormat;
import io.recordrelay.core.domain.ValidationSeverity;
import io.recordrelay.core.exception.MappingParseException;
import org.junit.jupiter.api.Test;

class JsonMappingParserTest {

  private final JsonMappingParser parser = new JsonMappingParser();

  private static final String VALID_JSON =
      """
      {
        "id": "m-001",
        "source": { "schema": "public", "table": "customers", "dbType": "POSTGRESQL" },
        "target": { "table": "users", "dbType": "MONGODB" },
        "columns": [
          { "source": "cust_id", "target": "id", "transform": "cast:int" },
          { "source": "email_addr", "target": "email", "transform": "trim" }
        ]
      }
      """;

  @Test
  void formatIdIsJson() {
    assertThat(parser.formatId()).isEqualTo("json");
  }

  @Test
  void validDocumentPassesValidation() {
    var result = parser.validate(VALID_JSON);
    assertThat(result.valid()).isTrue();
    assertThat(result.errors()).isEmpty();
  }

  @Test
  void validDocumentParsesCorrectly() throws MappingParseException {
    var mapping = parser.parse(VALID_JSON);
    assertThat(mapping.id()).isEqualTo("m-001");
    assertThat(mapping.format()).isEqualTo(MappingFormat.JSON);
    assertThat(mapping.source().tableName()).isEqualTo("customers");
    assertThat(mapping.source().schemaName()).isEqualTo("public");
    assertThat(mapping.source().database().type()).isEqualTo(DatabaseType.POSTGRESQL);
    assertThat(mapping.target().tableName()).isEqualTo("users");
    assertThat(mapping.target().database().type()).isEqualTo(DatabaseType.MONGODB);
    assertThat(mapping.columnMappings()).hasSize(2);
    assertThat(mapping.columnMappings().get(0).sourceColumn()).isEqualTo("cust_id");
    assertThat(mapping.columnMappings().get(0).targetColumn()).isEqualTo("id");
    assertThat(mapping.columnMappings().get(0).transform()).isEqualTo("cast:int");
    assertThat(mapping.columnMappings().get(1).transform()).isEqualTo("trim");
  }

  @Test
  void missingIdProducesError() {
    String json =
        """
        { "source": {"table": "t"}, "target": {"table": "u"} }
        """;
    var result = parser.validate(json);
    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).anySatisfy(e -> assertThat(e.field()).isEqualTo("id"));
  }

  @Test
  void missingSourceTableProducesError() {
    String json =
        """
        { "id": "x", "source": {"schema": "s"}, "target": {"table": "u"} }
        """;
    var result = parser.validate(json);
    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).anySatisfy(e -> assertThat(e.field()).isEqualTo("source"));
  }

  @Test
  void invalidJsonReturnsSyntaxErrorWithLocation() {
    String bad = "{ \"id\": \"x\", INVALID }";
    var result = parser.validate(bad);
    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).hasSize(1);
    var err = result.errors().get(0);
    assertThat(err.severity()).isEqualTo(ValidationSeverity.ERROR);
    assertThat(err.hasSyntaxLocation()).isTrue();
    assertThat(err.line()).isGreaterThan(0);
  }

  @Test
  void emptyContentProducesError() {
    assertThat(parser.validate("").valid()).isFalse();
    assertThat(parser.validate(null).valid()).isFalse();
  }

  @Test
  void parseThrowsWhenInvalid() {
    assertThatThrownBy(() -> parser.parse("not json")).isInstanceOf(MappingParseException.class);
  }

  @Test
  void columnsWithoutTransformHaveNullTransform() throws MappingParseException {
    String json =
        """
        {
          "id": "m-002",
          "source": {"table": "src"},
          "target": {"table": "tgt"},
          "columns": [{ "source": "col", "target": "col" }]
        }
        """;
    var mapping = parser.parse(json);
    assertThat(mapping.columnMappings().get(0).transform()).isNull();
  }

  @Test
  void missingColumnsArrayResultsInEmptyMappings() throws MappingParseException {
    String json =
        """
        { "id": "m-003", "source": {"table": "s"}, "target": {"table": "t"} }
        """;
    var mapping = parser.parse(json);
    assertThat(mapping.columnMappings()).isEmpty();
  }
}
