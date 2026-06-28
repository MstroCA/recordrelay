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

import io.recordrelay.core.domain.DataRecord;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes synthetic DataRecords to a CSV file. */
public final class CsvExporter {

  private CsvExporter() {}

  /**
   * Writes {@code records} to {@code output} in RFC-4180 CSV format.
   *
   * @param records rows to write
   * @param output destination file path (created or overwritten)
   */
  public static void write(List<DataRecord> records, Path output) throws IOException {
    if (records.isEmpty()) return;
    var headers = records.get(0).fieldNames();
    try (BufferedWriter w = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
      w.write(String.join(",", headers.stream().map(CsvExporter::quote).toList()));
      w.newLine();
      for (var rec : records) {
        var row = headers.stream().map(h -> quote(String.valueOf(rec.get(h)))).toList();
        w.write(String.join(",", row));
        w.newLine();
      }
    }
  }

  private static String quote(String value) {
    if (value == null || value.equals("null")) return "";
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }
}
