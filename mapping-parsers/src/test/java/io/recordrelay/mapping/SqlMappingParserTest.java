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
import io.recordrelay.core.exception.MappingParseException;
import org.junit.jupiter.api.Test;

class SqlMappingParserTest {

  private final SqlMappingParser parser = new SqlMappingParser();

  private static final String VALID_SQL =
      "SELECT cust_id AS id, email_addr AS email FROM public.customers";

  @Test
  void formatIdIsSql() {
    assertThat(parser.formatId()).isEqualTo("sql");
  }

  @Test
  void validSelectPassesValidation() {
    assertThat(parser.validate(VALID_SQL).valid()).isTrue();
  }

  @Test
  void validSelectParsesCorrectly() throws MappingParseException {
    var mapping = parser.parse(VALID_SQL);
    assertThat(mapping.format()).isEqualTo(MappingFormat.SQL);
    assertThat(mapping.query()).isEqualTo(VALID_SQL);
    assertThat(mapping.source().tableName()).isEqualTo("customers");
    assertThat(mapping.source().schemaName()).isEqualTo("public");
  }

  @Test
  void simpleAliasExtractedAsColumnMapping() throws MappingParseException {
    var mapping = parser.parse("SELECT cust_id AS id, name AS full_name FROM orders");
    assertThat(mapping.columnMappings())
        .anySatisfy(
            cm -> {
              assertThat(cm.sourceColumn()).isEqualTo("cust_id");
              assertThat(cm.targetColumn()).isEqualTo("id");
            });
  }

  @Test
  void complexExpressionNotExtractedAsColumnMapping() throws MappingParseException {
    var mapping = parser.parse("SELECT CONCAT(first_name, ' ', last_name) AS full_name FROM users");
    assertThat(mapping.columnMappings()).isEmpty();
  }

  @Test
  void invalidSqlReturnsSyntaxError() {
    String bad = "SELECT FROM WHERE broken";
    var result = parser.validate(bad);
    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).hasSize(1);
  }

  @Test
  void insertStatementRejected() {
    var result = parser.validate("INSERT INTO t VALUES (1)");
    assertThat(result.valid()).isFalse();
  }

  @Test
  void emptyContentProducesError() {
    assertThat(parser.validate("").valid()).isFalse();
    assertThat(parser.validate(null).valid()).isFalse();
  }

  @Test
  void parseThrowsOnInvalidSql() {
    assertThatThrownBy(() -> parser.parse("not sql at all!!!"))
        .isInstanceOf(MappingParseException.class);
  }

  @Test
  void starSelectWithNoAlias() throws MappingParseException {
    var mapping = parser.parse("SELECT * FROM orders");
    assertThat(mapping.columnMappings()).isEmpty();
    assertThat(mapping.query()).isEqualTo("SELECT * FROM orders");
  }
}
