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
package io.recordrelay.cli.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Renders CLI output as either a human-readable table or JSON, based on {@link OutputMode}. */
public final class Printer {

  private final OutputMode mode;
  private final PrintStream out;
  private final ObjectMapper mapper;

  public Printer(OutputMode mode) {
    this(mode, System.out);
  }

  Printer(OutputMode mode, PrintStream out) {
    this.mode = mode;
    this.out = out;
    this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  }

  /** Prints a plain line in HUMAN mode; no-op in JSON mode. */
  public void printLine(String text) {
    if (mode == OutputMode.HUMAN) {
      out.println(text);
    }
  }

  /** Prints a success envelope. In JSON mode emits {@code {"status":"ok","message":...}}. */
  public void printSuccess(String message) throws Exception {
    if (mode == OutputMode.JSON) {
      out.println(mapper.writeValueAsString(Map.of("status", "ok", "message", message)));
    } else {
      out.println("[OK] " + message);
    }
  }

  /** Prints an error envelope to stderr. In JSON mode emits {@code {"status":"error",...}}. */
  public void printError(String message) throws Exception {
    if (mode == OutputMode.JSON) {
      System.err.println(mapper.writeValueAsString(Map.of("status", "error", "message", message)));
    } else {
      System.err.println("[ERROR] " + message);
    }
  }

  /** Serialises any object as pretty-printed JSON to stdout. No-op in HUMAN mode. */
  public void printJson(Object value) throws Exception {
    if (mode == OutputMode.JSON) {
      out.println(mapper.writeValueAsString(value));
    }
  }

  /**
   * Renders tabular data. In JSON mode each row becomes an object keyed by header names. In HUMAN
   * mode an ASCII border table is drawn.
   */
  public void printTable(List<String> headers, List<List<String>> rows) throws Exception {
    if (mode == OutputMode.JSON) {
      printTableAsJson(headers, rows);
    } else {
      printHumanTable(headers, rows);
    }
  }

  private void printTableAsJson(List<String> headers, List<List<String>> rows) throws Exception {
    var list = new ArrayList<Map<String, String>>();
    for (var row : rows) {
      var map = new LinkedHashMap<String, String>();
      for (int i = 0; i < headers.size(); i++) {
        map.put(headers.get(i), i < row.size() ? row.get(i) : "");
      }
      list.add(map);
    }
    out.println(mapper.writeValueAsString(list));
  }

  private void printHumanTable(List<String> headers, List<List<String>> rows) {
    int[] widths = computeWidths(headers, rows);
    String separator = buildSeparator(widths);
    out.println(separator);
    out.println(buildRow(headers, widths));
    out.println(separator);
    for (var row : rows) {
      out.println(buildRow(row, widths));
    }
    out.println(separator);
  }

  private int[] computeWidths(List<String> headers, List<List<String>> rows) {
    int[] widths = new int[headers.size()];
    for (int i = 0; i < headers.size(); i++) {
      widths[i] = headers.get(i).length();
    }
    for (var row : rows) {
      for (int i = 0; i < Math.min(row.size(), widths.length); i++) {
        widths[i] = Math.max(widths[i], row.get(i).length());
      }
    }
    return widths;
  }

  private String buildSeparator(int[] widths) {
    var sb = new StringBuilder("+");
    for (int w : widths) {
      sb.append("-".repeat(w + 2)).append("+");
    }
    return sb.toString();
  }

  private String buildRow(List<String> cells, int[] widths) {
    var sb = new StringBuilder("|");
    for (int i = 0; i < widths.length; i++) {
      String cell = i < cells.size() ? cells.get(i) : "";
      sb.append(" ").append(String.format("%-" + widths[i] + "s", cell)).append(" |");
    }
    return sb.toString();
  }
}
