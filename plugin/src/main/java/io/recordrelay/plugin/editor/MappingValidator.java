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
package io.recordrelay.plugin.editor;

import io.recordrelay.core.domain.ValidationResult;
import io.recordrelay.core.spi.MappingParserRegistry;
import java.util.stream.Collectors;

/** Utility for validating RecordRelay mapping file content via the SPI registry. */
public final class MappingValidator {

  private MappingValidator() {}

  /**
   * Validates the content of a mapping file.
   *
   * @param content raw file text
   * @param formatId parser format identifier, e.g. {@code "json"} or {@code "yaml"}
   * @return a human-readable result string; {@code null} when content is valid
   */
  public static String validate(String content, String formatId) {
    var parserOpt = MappingParserRegistry.find(formatId);
    if (parserOpt.isEmpty()) {
      return "No parser available for format: " + formatId;
    }
    ValidationResult result = parserOpt.get().validate(content);
    if (result.valid()) {
      return null;
    }
    return result.errors().stream()
        .map(e -> "Line " + e.line() + ": " + e.message())
        .collect(Collectors.joining("\n"));
  }

  /**
   * Infers the format identifier from a file name extension.
   *
   * @param fileName the file name (may include path)
   * @return {@code "json"}, {@code "yaml"}, or {@code null} when unrecognized
   */
  public static String inferFormat(String fileName) {
    if (fileName == null) {
      return null;
    }
    String lower = fileName.toLowerCase();
    if (lower.endsWith(".json")) {
      return "json";
    }
    if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
      return "yaml";
    }
    return null;
  }
}
