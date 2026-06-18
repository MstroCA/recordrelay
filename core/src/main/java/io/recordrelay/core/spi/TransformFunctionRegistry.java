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

import io.recordrelay.core.port.out.TransformFunction;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Discovers {@link TransformFunction} implementations via {@link ServiceLoader}.
 *
 * <p>Functions are registered by placing an implementation in {@code
 * META-INF/services/io.recordrelay.core.port.out.TransformFunction}. The registry is initialised
 * once at class-load time.
 */
public final class TransformFunctionRegistry {

  private static final Map<String, TransformFunction> FUNCTIONS = load();

  private TransformFunctionRegistry() {}

  private static Map<String, TransformFunction> load() {
    var map = new HashMap<String, TransformFunction>();
    ServiceLoader.load(TransformFunction.class).forEach(f -> map.put(f.functionId(), f));
    return Map.copyOf(map);
  }

  /**
   * Returns the transform function registered under {@code functionId}, or {@link Optional#empty()}
   * when no function with that identifier is registered.
   *
   * @param functionId the function identifier (e.g., {@code "trim"}, {@code "cast"})
   * @return the matching function, or empty
   */
  public static Optional<TransformFunction> find(String functionId) {
    return Optional.ofNullable(FUNCTIONS.get(functionId));
  }
}
