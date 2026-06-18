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
package io.recordrelay.connector.file;

import static org.assertj.core.api.Assertions.assertThat;

import io.recordrelay.connector.file.csv.CsvConnector;
import io.recordrelay.connector.file.csv.CsvRecordReader;
import io.recordrelay.connector.file.csv.CsvRecordWriter;
import io.recordrelay.connector.file.excel.ExcelConnector;
import io.recordrelay.connector.file.excel.ExcelRecordReader;
import io.recordrelay.connector.file.excel.ExcelRecordWriter;
import io.recordrelay.connector.file.json.JsonConnector;
import io.recordrelay.connector.file.json.JsonRecordReader;
import io.recordrelay.connector.file.json.JsonRecordWriter;
import io.recordrelay.connector.file.parquet.ParquetConnector;
import io.recordrelay.connector.file.parquet.ParquetRecordReader;
import io.recordrelay.connector.file.parquet.ParquetRecordWriter;
import io.recordrelay.connector.file.yaml.YamlConnector;
import io.recordrelay.connector.file.yaml.YamlRecordReader;
import io.recordrelay.connector.file.yaml.YamlRecordWriter;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.MappingFormat;
import io.recordrelay.core.domain.TableRef;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Functional tests for all file format connectors using @TempDir. */
class FileConnectorTest {

  @TempDir Path tempDir;

  // ---- CSV ----
  @Test
  void csvConnectorIdAndSupports() {
    var c = new CsvConnector();
    assertThat(c.connectorId()).isEqualTo("file-csv");
    assertThat(c.supports(profile(DatabaseType.FILE_CSV, "test.csv"))).isTrue();
    assertThat(c.supports(profile(DatabaseType.FILE_JSON, "test.json"))).isFalse();
  }

  @Test
  void csvRoundTrip() throws Exception {
    var file = tempDir.resolve("data.csv").toString();
    var profile = profile(DatabaseType.FILE_CSV, file);
    var db = new DatabaseRef(file, DatabaseType.FILE_CSV);
    var table = new TableRef(db, "", "data.csv");
    var mapping =
        new MappingDefinition("test", table, table, List.of(), MappingFormat.DIRECT, null);

    var writer = new CsvRecordWriter();
    writer.open(profile, table, mapping);
    writer.write(record("name", "Alice", "age", "30"));
    writer.write(record("name", "Bob", "age", "25"));
    writer.close();

    var reader = new CsvRecordReader();
    reader.open(profile, table, mapping);
    var records = drain(reader);
    assertThat(records).hasSize(2);
    assertThat(records.get(0).get("name")).isEqualTo("Alice");
    assertThat(records.get(1).get("name")).isEqualTo("Bob");
  }

  // ---- JSON ----
  @Test
  void jsonConnectorIdAndSupports() {
    var c = new JsonConnector();
    assertThat(c.connectorId()).isEqualTo("file-json");
    assertThat(c.supports(profile(DatabaseType.FILE_JSON, "test.json"))).isTrue();
    assertThat(c.supports(profile(DatabaseType.FILE_CSV, "test.csv"))).isFalse();
  }

  @Test
  void jsonRoundTrip() throws Exception {
    var file = tempDir.resolve("data.jsonl").toString();
    var profile = profile(DatabaseType.FILE_JSON, file);
    var db = new DatabaseRef(file, DatabaseType.FILE_JSON);
    var table = new TableRef(db, "", "data.jsonl");
    var mapping =
        new MappingDefinition("test", table, table, List.of(), MappingFormat.DIRECT, null);

    var writer = new JsonRecordWriter();
    writer.open(profile, table, mapping);
    writer.write(record("id", "1", "value", "foo"));
    writer.write(record("id", "2", "value", "bar"));
    writer.close();

    var reader = new JsonRecordReader();
    reader.open(profile, table, mapping);
    var records = drain(reader);
    assertThat(records).hasSize(2);
    assertThat(records.get(0).get("id")).isEqualTo("1");
  }

  // ---- YAML ----
  @Test
  void yamlConnectorIdAndSupports() {
    var c = new YamlConnector();
    assertThat(c.connectorId()).isEqualTo("file-yaml");
    assertThat(c.supports(profile(DatabaseType.FILE_YAML, "test.yaml"))).isTrue();
  }

  @Test
  void yamlRoundTrip() throws Exception {
    var file = tempDir.resolve("data.yaml").toString();
    var profile = profile(DatabaseType.FILE_YAML, file);
    var db = new DatabaseRef(file, DatabaseType.FILE_YAML);
    var table = new TableRef(db, "", "data.yaml");
    var mapping =
        new MappingDefinition("test", table, table, List.of(), MappingFormat.DIRECT, null);

    var writer = new YamlRecordWriter();
    writer.open(profile, table, mapping);
    writer.write(record("key", "a", "val", "1"));
    writer.write(record("key", "b", "val", "2"));
    writer.close();

    var reader = new YamlRecordReader();
    reader.open(profile, table, mapping);
    var records = drain(reader);
    assertThat(records).hasSize(2);
    assertThat(records.get(0).get("key").toString()).isEqualTo("a");
  }

  // ---- Excel ----
  @Test
  void excelConnectorIdAndSupports() {
    var c = new ExcelConnector();
    assertThat(c.connectorId()).isEqualTo("file-excel");
    assertThat(c.supports(profile(DatabaseType.FILE_EXCEL, "test.xlsx"))).isTrue();
  }

  @Test
  void excelRoundTrip() throws Exception {
    var file = tempDir.resolve("data.xlsx").toString();
    var profile = profile(DatabaseType.FILE_EXCEL, file);
    var db = new DatabaseRef(file, DatabaseType.FILE_EXCEL);
    var table = new TableRef(db, "", "data.xlsx");
    var mapping =
        new MappingDefinition("test", table, table, List.of(), MappingFormat.DIRECT, null);

    var writer = new ExcelRecordWriter();
    writer.open(profile, table, mapping);
    writer.write(record("col1", "hello", "col2", "world"));
    writer.close();

    var reader = new ExcelRecordReader();
    reader.open(profile, table, mapping);
    var records = drain(reader);
    assertThat(records).hasSize(1);
    assertThat(records.get(0).get("col1")).isEqualTo("hello");
  }

  // ---- Parquet ----
  @Test
  void parquetConnectorIdAndSupports() {
    var c = new ParquetConnector();
    assertThat(c.connectorId()).isEqualTo("file-parquet");
    assertThat(c.supports(profile(DatabaseType.FILE_PARQUET, "test.parquet"))).isTrue();
  }

  @Test
  void parquetRoundTrip() throws Exception {
    var file = tempDir.resolve("data.parquet").toString();
    var profile = profile(DatabaseType.FILE_PARQUET, file);
    var db = new DatabaseRef(file, DatabaseType.FILE_PARQUET);
    var table = new TableRef(db, "", "data.parquet");
    var mapping =
        new MappingDefinition("test", table, table, List.of(), MappingFormat.DIRECT, null);

    var writer = new ParquetRecordWriter();
    writer.open(profile, table, mapping);
    writer.write(record("name", "Alice", "score", "95"));
    writer.write(record("name", "Bob", "score", "80"));
    writer.close();

    var reader = new ParquetRecordReader();
    reader.open(profile, table, mapping);
    var records = drain(reader);
    assertThat(records).hasSize(2);
    assertThat(records.get(0).get("name").toString()).isEqualTo("Alice");
  }

  // ---- Helpers ----
  private ConnectionProfile profile(DatabaseType type, String filePath) {
    return new ConnectionProfile(
        "id",
        "test",
        "env",
        type,
        "localhost",
        1,
        tempDir.resolve(filePath).toString(),
        new Credentials("", ""),
        Map.of());
  }

  private DataRecord record(String... kvPairs) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < kvPairs.length; i += 2) {
      map.put(kvPairs[i], kvPairs[i + 1]);
    }
    return new DataRecord(map);
  }

  private List<DataRecord> drain(io.recordrelay.core.port.out.RecordReader reader)
      throws Exception {
    var result = new ArrayList<DataRecord>();
    while (reader.hasMore()) {
      reader.readNext().ifPresent(result::add);
    }
    reader.close();
    return result;
  }
}
