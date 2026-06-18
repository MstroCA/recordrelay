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
package io.recordrelay.mapping.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.recordrelay.core.exception.ConnectorException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransformFunctionTest {

  // ── TrimFunction ────────────────────────────────────────────────────────────

  @Test
  void trimRemovesLeadingAndTrailingSpaces() throws ConnectorException {
    assertThat(new TrimFunction().apply("  hello  ")).isEqualTo("hello");
  }

  @Test
  void trimPassesNullThrough() throws ConnectorException {
    assertThat(new TrimFunction().apply((Object) null)).isNull();
  }

  @Test
  void trimConvertsNonStringToString() throws ConnectorException {
    assertThat(new TrimFunction().apply(42)).isEqualTo("42");
  }

  // ── CastFunction ────────────────────────────────────────────────────────────

  @Test
  void castToLong() throws ConnectorException {
    assertThat(new CastFunction().apply("42", "int")).isEqualTo(42L);
  }

  @Test
  void castToDouble() throws ConnectorException {
    assertThat(new CastFunction().apply("3.14", "double")).isEqualTo(3.14);
  }

  @Test
  void castToString() throws ConnectorException {
    assertThat(new CastFunction().apply(123, "string")).isEqualTo("123");
  }

  @Test
  void castToBoolean() throws ConnectorException {
    assertThat(new CastFunction().apply("true", "boolean")).isEqualTo(true);
  }

  @Test
  void castToBooleanWithMapping() throws ConnectorException {
    assertThat(new CastFunction().apply("Y", "boolean", "Y=true,N=false")).isEqualTo(true);
    assertThat(new CastFunction().apply("N", "boolean", "Y=true,N=false")).isEqualTo(false);
  }

  @Test
  void castNullPassesThrough() throws ConnectorException {
    assertThat(new CastFunction().apply(null, "int")).isNull();
  }

  @Test
  void castInvalidNumberThrows() {
    assertThatThrownBy(() -> new CastFunction().apply("abc", "int"))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("cannot parse");
  }

  @Test
  void castMissingTypeArgThrows() {
    assertThatThrownBy(() -> new CastFunction().apply("1")).isInstanceOf(ConnectorException.class);
  }

  @Test
  void castUnknownTypeThrows() {
    assertThatThrownBy(() -> new CastFunction().apply("1", "uuid"))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("unknown type");
  }

  // ── DateFormatFunction ──────────────────────────────────────────────────────

  @Test
  void dateFormatLocalDate() throws ConnectorException {
    Object result = new DateFormatFunction().apply(LocalDate.of(2024, 3, 15), "yyyy-MM-dd");
    assertThat(result).isEqualTo("2024-03-15");
  }

  @Test
  void dateFormatIsoString() throws ConnectorException {
    Object result = new DateFormatFunction().apply("2024-06-01", "dd/MM/yyyy");
    assertThat(result).isEqualTo("01/06/2024");
  }

  @Test
  void dateFormatNullPassesThrough() throws ConnectorException {
    assertThat(new DateFormatFunction().apply(null, "yyyy-MM-dd")).isNull();
  }

  @Test
  void dateFormatMissingPatternThrows() {
    assertThatThrownBy(() -> new DateFormatFunction().apply("2024-01-01"))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("pattern");
  }

  // ── ConcatFunction ──────────────────────────────────────────────────────────

  @Test
  void concatAppendsSuffix() throws ConnectorException {
    assertThat(new ConcatFunction().apply("hello", "_v2")).isEqualTo("hello_v2");
  }

  @Test
  void concatWithNoArgReturnsStringRepresentation() throws ConnectorException {
    assertThat(new ConcatFunction().apply(42)).isEqualTo("42");
  }

  @Test
  void concatNullPassesThrough() throws ConnectorException {
    assertThat(new ConcatFunction().apply((Object) null, "x")).isNull();
  }

  // ── DefaultValueFunction ────────────────────────────────────────────────────

  @Test
  void defaultValueReplacesNull() throws ConnectorException {
    assertThat(new DefaultValueFunction().apply(null, "N/A")).isEqualTo("N/A");
  }

  @Test
  void defaultValueReplacesBlank() throws ConnectorException {
    assertThat(new DefaultValueFunction().apply("  ", "N/A")).isEqualTo("N/A");
  }

  @Test
  void defaultValuePassesThroughNonNull() throws ConnectorException {
    assertThat(new DefaultValueFunction().apply("hello", "N/A")).isEqualTo("hello");
  }

  @Test
  void defaultValueMissingArgThrows() {
    assertThatThrownBy(() -> new DefaultValueFunction().apply(null))
        .isInstanceOf(ConnectorException.class);
  }

  // ── RegexReplaceFunction ────────────────────────────────────────────────────

  @Test
  void regexReplaceAppliesPattern() throws ConnectorException {
    Object result = new RegexReplaceFunction().apply("hello world", "\\s+", "_");
    assertThat(result).isEqualTo("hello_world");
  }

  @Test
  void regexReplaceNullPassesThrough() throws ConnectorException {
    assertThat(new RegexReplaceFunction().apply(null, "x", "y")).isNull();
  }

  @Test
  void regexReplaceMissingArgsThrows() {
    assertThatThrownBy(() -> new RegexReplaceFunction().apply("hello", "x"))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("two arguments");
  }

  @Test
  void regexReplaceInvalidPatternThrows() {
    assertThatThrownBy(() -> new RegexReplaceFunction().apply("hello", "[invalid", "x"))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("invalid pattern");
  }
}
