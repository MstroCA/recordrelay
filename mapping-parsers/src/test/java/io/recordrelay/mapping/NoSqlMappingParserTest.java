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

class NoSqlMappingParserTest {

  private final NoSqlMappingParser parser = new NoSqlMappingParser();

  private static final String VALID_PIPELINE =
      """
      [
        {
          "$project": {
            "id":    "$cust_id",
            "email": "$email_addr"
          }
        }
      ]
      """;

  @Test
  void formatIdIsNosqlQuery() {
    assertThat(parser.formatId()).isEqualTo("nosql-query");
  }

  @Test
  void validPipelinePassesValidation() {
    assertThat(parser.validate(VALID_PIPELINE).valid()).isTrue();
  }

  @Test
  void validPipelineParsesCorrectly() throws MappingParseException {
    var mapping = parser.parse(VALID_PIPELINE);
    assertThat(mapping.format()).isEqualTo(MappingFormat.NOSQL_QUERY);
    assertThat(mapping.query()).contains("$project");
    assertThat(mapping.columnMappings()).hasSize(2);
    assertThat(mapping.columnMappings())
        .anySatisfy(
            cm -> {
              assertThat(cm.sourceColumn()).isEqualTo("cust_id");
              assertThat(cm.targetColumn()).isEqualTo("id");
            });
  }

  @Test
  void complexExpressionInProjectNotExtractedAsColumnMapping() throws MappingParseException {
    String pipeline =
        """
        [{"$project": {"full_name": {"$concat": ["$first_name", " ", "$last_name"]}}}]
        """;
    var mapping = parser.parse(pipeline);
    assertThat(mapping.columnMappings()).isEmpty();
  }

  @Test
  void pipelineWithoutProjectStageRejected() {
    String noProject =
        """
        [{"$match": {"status": "active"}}]
        """;
    var result = parser.validate(noProject);
    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).anySatisfy(e -> assertThat(e.field()).isEqualTo("$project"));
  }

  @Test
  void emptyArrayRejected() {
    assertThat(parser.validate("[]").valid()).isFalse();
  }

  @Test
  void notAnArrayRejected() {
    var result =
        parser.validate(
            """
        {"$project": {"id": "$cust_id"}}
        """);
    assertThat(result.valid()).isFalse();
  }

  @Test
  void invalidJsonReturnsSyntaxErrorWithLocation() {
    String bad = "[{\"$project\": {BROKEN}]";
    var result = parser.validate(bad);
    assertThat(result.valid()).isFalse();
    assertThat(result.errors().get(0).hasSyntaxLocation()).isTrue();
  }

  @Test
  void emptyContentProducesError() {
    assertThat(parser.validate("").valid()).isFalse();
  }

  @Test
  void parseThrowsOnInvalidPipeline() {
    assertThatThrownBy(() -> parser.parse("[]")).isInstanceOf(MappingParseException.class);
  }
}
