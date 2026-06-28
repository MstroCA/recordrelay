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

import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.DataRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvExporterTest {

  @TempDir Path tempDir;

  private static DataRecord record(Object... keysAndValues) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put(keysAndValues[i].toString(), keysAndValues[i + 1]);
    }
    return new DataRecord(map);
  }

  @Test
  void exportWritesHeaderRow() throws Exception {
    var records = List.of(record("id", 1, "name", "Alice", "email", "alice@example.com"));

    Path out = tempDir.resolve("test.csv");
    CsvExporter.write(records, out);

    String content = Files.readString(out);
    assertThat(content.lines().findFirst().orElse("")).contains("id", "name", "email");
  }

  @Test
  void exportWritesCorrectNumberOfDataRows() throws Exception {
    var records =
        List.of(
            record("id", 1, "value", "a"),
            record("id", 2, "value", "b"),
            record("id", 3, "value", "c"));

    Path out = tempDir.resolve("rows.csv");
    CsvExporter.write(records, out);

    long lineCount = Files.readString(out).lines().count();
    // 1 header + 3 data rows
    assertThat(lineCount).isEqualTo(4);
  }

  @Test
  void exportHandlesNullValues() throws Exception {
    var records = List.of(record("id", 1, "optional", null));

    Path out = tempDir.resolve("nulls.csv");
    CsvExporter.write(records, out);

    assertThat(Files.exists(out)).isTrue();
    String content = Files.readString(out);
    assertThat(content).isNotEmpty();
  }

  @Test
  void exportHandlesValueWithCommaInQuotes() throws Exception {
    var records = List.of(record("id", 1, "address", "123 Main St, Springfield"));

    Path out = tempDir.resolve("comma.csv");
    CsvExporter.write(records, out);

    String content = Files.readString(out);
    assertThat(content).contains("123 Main St, Springfield");
  }

  @Test
  void exportHandlesValueWithDoubleQuote() throws Exception {
    var records = List.of(record("id", 1, "note", "He said \"hello\""));

    Path out = tempDir.resolve("quotes.csv");
    CsvExporter.write(records, out);

    assertThat(Files.exists(out)).isTrue();
  }

  @Test
  void exportEmptyRecordListWritesNothing() throws Exception {
    Path out = tempDir.resolve("empty.csv");
    CsvExporter.write(List.of(), out);

    // write() returns early for empty input — file is not created
    assertThat(Files.exists(out)).isFalse();
  }

  @Test
  void syntheticGeneratorAndCsvExporterIntegration() throws Exception {
    var schema =
        List.of(
            new ColumnMeta("id", "integer", false, true, false, 1, null),
            new ColumnMeta("email", "varchar", true, false, false, 2, null),
            new ColumnMeta("name", "varchar", true, false, false, 3, null));

    var rows = new SyntheticDataGenerator(schema).seed(42).generate(50);
    Path out = tempDir.resolve("integration.csv");
    CsvExporter.write(rows, out);

    assertThat(Files.exists(out)).isTrue();
    long lineCount = Files.readString(out).lines().filter(l -> !l.isBlank()).count();
    assertThat(lineCount).isEqualTo(51); // 1 header + 50 data rows
  }
}
