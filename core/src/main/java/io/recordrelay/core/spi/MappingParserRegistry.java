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
package io.recordrelay.core.spi;

import io.recordrelay.core.domain.MappingFormat;
import io.recordrelay.core.port.in.MappingParser;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Discovers {@link MappingParser} implementations via {@link ServiceLoader}.
 *
 * <p>Parsers are registered by placing an implementation in {@code
 * META-INF/services/io.recordrelay.core.port.in.MappingParser}. The registry is initialised once at
 * class-load time.
 */
public final class MappingParserRegistry {

  private static final Map<String, MappingParser> PARSERS = load();

  private MappingParserRegistry() {}

  private static Map<String, MappingParser> load() {
    var map = new HashMap<String, MappingParser>();
    ServiceLoader.load(MappingParser.class).forEach(p -> map.put(p.formatId(), p));
    return Map.copyOf(map);
  }

  /**
   * Returns the parser for {@code formatId} (e.g., {@code "json"}, {@code "yaml"}, {@code "sql"},
   * {@code "nosql-query"}), or {@link Optional#empty()} when none is registered.
   *
   * @param formatId the format identifier
   * @return the matching parser, or empty
   */
  public static Optional<MappingParser> find(String formatId) {
    return Optional.ofNullable(PARSERS.get(formatId));
  }

  /**
   * Resolves a {@link MappingFormat} enum value to a registered parser.
   *
   * @param format the mapping format
   * @return the matching parser, or empty when no parser is registered for this format
   * @throws IllegalArgumentException when {@code format} is {@link MappingFormat#DIRECT} (which has
   *     no parser)
   */
  public static Optional<MappingParser> find(MappingFormat format) {
    return switch (format) {
      case JSON -> find("json");
      case YAML -> find("yaml");
      case SQL -> find("sql");
      case NOSQL_QUERY -> find("nosql-query");
      case DIRECT ->
          throw new IllegalArgumentException("DIRECT format has no parser; use ColumnMappings");
    };
  }
}
