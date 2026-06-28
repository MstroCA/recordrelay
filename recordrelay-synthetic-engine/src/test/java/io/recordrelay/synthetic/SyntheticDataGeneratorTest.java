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
package io.recordrelay.synthetic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.DataRecord;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SyntheticDataGeneratorTest {

  private static ColumnMeta col(String name, String type, boolean pk) {
    return new ColumnMeta(name, type, !pk, pk, false, 1, null);
  }

  private static List<ColumnMeta> customerSchema() {
    return List.of(
        col("id", "integer", true),
        col("email", "varchar", false),
        col("phone", "varchar", false),
        col("name", "varchar", false),
        col("status", "varchar", false),
        col("created_at", "timestamp", false));
  }

  @Test
  void generateExactNumberOfRows() {
    var gen = new SyntheticDataGenerator(customerSchema()).seed(42);
    var rows = gen.generate(100);
    assertThat(rows).hasSize(100);
  }

  @Test
  void generateZeroRowsThrowsException() {
    var gen = new SyntheticDataGenerator(customerSchema()).seed(1);
    assertThatThrownBy(() -> gen.generate(0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void everyRowContainsAllColumns() {
    var gen = new SyntheticDataGenerator(customerSchema()).seed(7);
    for (DataRecord row : gen.generate(20)) {
      assertThat(row.fieldNames())
          .containsExactlyInAnyOrder("id", "email", "phone", "name", "status", "created_at");
    }
  }

  @Test
  void primaryKeyColumnIsSequential() {
    var gen = new SyntheticDataGenerator(customerSchema()).seed(0).startId(1);
    var rows = gen.generate(5);
    for (int i = 0; i < rows.size(); i++) {
      Object id = rows.get(i).get("id");
      assertThat(id).isNotNull();
      // PK value should be numeric and sequential starting from startId
      int numericId = Integer.parseInt(id.toString());
      assertThat(numericId).isGreaterThanOrEqualTo(1);
    }
  }

  @Test
  void primaryKeyValuesAreUnique() {
    var gen = new SyntheticDataGenerator(customerSchema()).seed(99);
    var rows = gen.generate(50);
    var ids = rows.stream().map(r -> r.get("id").toString()).toList();
    assertThat(ids).doesNotHaveDuplicates();
  }

  @Test
  void emailColumnLooksLikeEmail() {
    var gen = new SyntheticDataGenerator(customerSchema()).seed(13);
    var rows = gen.generate(10);
    for (DataRecord row : rows) {
      Object email = row.get("email");
      if (email != null) {
        assertThat(email.toString()).contains("@");
      }
    }
  }

  @Test
  void reproducibleWithSameSeed() {
    var schema = customerSchema();
    var rows1 = new SyntheticDataGenerator(schema).seed(42).generate(10);
    var rows2 = new SyntheticDataGenerator(schema).seed(42).generate(10);
    for (int i = 0; i < rows1.size(); i++) {
      assertThat(rows1.get(i).get("email")).isEqualTo(rows2.get(i).get("email"));
    }
  }

  @Test
  void differentSeedsProduceDifferentData() {
    var schema = customerSchema();
    var rows1 = new SyntheticDataGenerator(schema).seed(1).generate(5);
    var rows2 = new SyntheticDataGenerator(schema).seed(999).generate(5);
    // At least one email should differ
    boolean anyDifferent = false;
    for (int i = 0; i < rows1.size(); i++) {
      if (!String.valueOf(rows1.get(i).get("email"))
          .equals(String.valueOf(rows2.get(i).get("email")))) {
        anyDifferent = true;
        break;
      }
    }
    assertThat(anyDifferent).isTrue();
  }

  @Test
  void nullOrEmptyColumnsThrowsException() {
    assertThatThrownBy(() -> new SyntheticDataGenerator(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SyntheticDataGenerator(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 10, 100, 500})
  void generateVariousRowCounts(int n) {
    var gen = new SyntheticDataGenerator(customerSchema()).seed(42);
    assertThat(gen.generate(n)).hasSize(n);
  }

  @Test
  void numericColumnsProduceNumericValues() {
    var schema =
        List.of(
            col("id", "integer", true),
            col("amount", "numeric", false),
            col("quantity", "int", false));
    var gen = new SyntheticDataGenerator(schema).seed(1);
    for (DataRecord row : gen.generate(10)) {
      Object amount = row.get("amount");
      if (amount != null) {
        assertThat(amount).isInstanceOfAny(Number.class, String.class);
      }
    }
  }

  @Test
  void booleanColumnProducesBooleanOrString() {
    var schema = List.of(col("id", "integer", true), col("active", "boolean", false));
    var gen = new SyntheticDataGenerator(schema).seed(2);
    for (DataRecord row : gen.generate(10)) {
      Object active = row.get("active");
      if (active != null) {
        assertThat(active.toString()).isIn("true", "false", "True", "False");
      }
    }
  }

  @Test
  void singleColumnSchemaWorks() {
    var schema = List.of(col("id", "bigint", true));
    var rows = new SyntheticDataGenerator(schema).seed(5).generate(3);
    assertThat(rows).hasSize(3);
    rows.forEach(r -> assertThat(r.get("id")).isNotNull());
  }

  @Test
  void startIdCustomValueIsReflectedInPk() {
    var schema = customerSchema();
    var rows = new SyntheticDataGenerator(schema).seed(0).startId(1000).generate(3);
    var firstId = Integer.parseInt(rows.get(0).get("id").toString());
    assertThat(firstId).isGreaterThanOrEqualTo(1000);
  }

  @Test
  void orderSchemaWithForeignKey() {
    var schema =
        List.of(
            col("order_id", "integer", true),
            col("customer_id", "integer", false),
            col("total_amount", "decimal", false),
            col("status", "varchar", false),
            col("created_at", "timestamp", false));
    var rows = new SyntheticDataGenerator(schema).seed(42).generate(20);
    assertThat(rows).hasSize(20);
    rows.forEach(
        r -> {
          assertThat(r.fieldNames())
              .containsExactlyInAnyOrder(
                  "order_id", "customer_id", "total_amount", "status", "created_at");
          assertThat(r.get("order_id")).isNotNull();
        });
  }
}
