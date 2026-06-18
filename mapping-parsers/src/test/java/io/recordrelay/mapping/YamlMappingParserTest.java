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

import io.recordrelay.core.domain.MappingFormat;
import io.recordrelay.core.domain.ValidationSeverity;
import io.recordrelay.core.exception.MappingParseException;
import org.junit.jupiter.api.Test;

class YamlMappingParserTest {

  private final YamlMappingParser parser = new YamlMappingParser();

  private static final String VALID_YAML =
      """
      id: mapping-001
      source:
        schema: public
        table: customers
        dbType: POSTGRESQL
      target:
        table: users
        dbType: MONGODB
      columns:
        - source: cust_id
          target: id
          transform: "cast:int"
        - source: email_addr
          target: email
          transform: trim
      """;

  @Test
  void formatIdIsYaml() {
    assertThat(parser.formatId()).isEqualTo("yaml");
  }

  @Test
  void validDocumentPassesValidation() {
    var result = parser.validate(VALID_YAML);
    assertThat(result.valid()).isTrue();
  }

  @Test
  void validDocumentParsesCorrectly() throws MappingParseException {
    var mapping = parser.parse(VALID_YAML);
    assertThat(mapping.id()).isEqualTo("mapping-001");
    assertThat(mapping.format()).isEqualTo(MappingFormat.YAML);
    assertThat(mapping.source().tableName()).isEqualTo("customers");
    assertThat(mapping.source().schemaName()).isEqualTo("public");
    assertThat(mapping.target().tableName()).isEqualTo("users");
    assertThat(mapping.columnMappings()).hasSize(2);
    assertThat(mapping.columnMappings().get(0).transform()).isEqualTo("cast:int");
    assertThat(mapping.columnMappings().get(1).transform()).isEqualTo("trim");
  }

  @Test
  void missingIdProducesError() {
    String yaml =
        """
        source:
          table: t
        target:
          table: u
        """;
    assertThat(parser.validate(yaml).valid()).isFalse();
  }

  @Test
  void missingSourceTableProducesError() {
    String yaml =
        """
        id: x
        source:
          schema: s
        target:
          table: u
        """;
    var result = parser.validate(yaml);
    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).anySatisfy(e -> assertThat(e.field()).isEqualTo("source"));
  }

  @Test
  void invalidYamlReturnsSyntaxErrorWithLocation() {
    String bad = "id: [\nbroken:";
    var result = parser.validate(bad);
    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().get(0).severity()).isEqualTo(ValidationSeverity.ERROR);
    assertThat(result.errors().get(0).hasSyntaxLocation()).isTrue();
  }

  @Test
  void emptyContentProducesError() {
    assertThat(parser.validate("").valid()).isFalse();
    assertThat(parser.validate(null).valid()).isFalse();
  }

  @Test
  void parseThrowsWhenInvalid() {
    assertThatThrownBy(() -> parser.parse("source:\n  table: t"))
        .isInstanceOf(MappingParseException.class);
  }

  @Test
  void columnsWithoutTransformHaveNullTransform() throws MappingParseException {
    String yaml =
        """
        id: m-002
        source:
          table: src
        target:
          table: tgt
        columns:
          - source: col
            target: col
        """;
    var mapping = parser.parse(yaml);
    assertThat(mapping.columnMappings().get(0).transform()).isNull();
  }
}
