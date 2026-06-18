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

import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.TransformFunction;
import java.util.HashMap;
import java.util.Map;

/**
 * Casts a value to a target type.
 *
 * <p>Transform spec: {@code "cast:<type>"} or {@code "cast:boolean:<mappings>"}
 *
 * <ul>
 *   <li>{@code cast:int} / {@code cast:long} → {@link Long}
 *   <li>{@code cast:double} / {@code cast:float} → {@link Double}
 *   <li>{@code cast:string} → {@link String}
 *   <li>{@code cast:boolean} → {@link Boolean} via {@link Boolean#parseBoolean}
 *   <li>{@code cast:boolean:Y=true,N=false} → {@link Boolean} via explicit value mapping
 * </ul>
 */
public final class CastFunction implements TransformFunction {

  @Override
  public String functionId() {
    return "cast";
  }

  @Override
  public Object apply(Object value, String... args) throws ConnectorException {
    if (value == null) {
      return null;
    }
    if (args.length == 0) {
      throw new ConnectorException("cast: target type argument is required");
    }
    String type = args[0].toLowerCase();
    String str = value.toString();
    return switch (type) {
      case "int", "long" -> castToLong(str);
      case "double", "float" -> castToDouble(str);
      case "string" -> str;
      case "boolean" -> castToBoolean(str, args);
      default -> throw new ConnectorException("cast: unknown type '" + type + "'");
    };
  }

  private Long castToLong(String str) throws ConnectorException {
    try {
      return Long.parseLong(str.trim());
    } catch (NumberFormatException e) {
      throw new ConnectorException("cast:int — cannot parse '" + str + "' as integer", e);
    }
  }

  private Double castToDouble(String str) throws ConnectorException {
    try {
      return Double.parseDouble(str.trim());
    } catch (NumberFormatException e) {
      throw new ConnectorException("cast:double — cannot parse '" + str + "' as double", e);
    }
  }

  private Boolean castToBoolean(String str, String[] args) {
    if (args.length > 1 && !args[1].isBlank()) {
      return applyBooleanMapping(str, args[1]);
    }
    return Boolean.parseBoolean(str.trim());
  }

  private Boolean applyBooleanMapping(String str, String mappingSpec) {
    Map<String, Boolean> valueMap = parseBooleanMapping(mappingSpec);
    var mapped = valueMap.get(str.trim());
    return mapped != null ? mapped : Boolean.parseBoolean(str.trim());
  }

  private Map<String, Boolean> parseBooleanMapping(String spec) {
    var map = new HashMap<String, Boolean>();
    for (String pair : spec.split(",")) {
      String[] kv = pair.split("=", 2);
      if (kv.length == 2) {
        map.put(kv[0].trim(), Boolean.parseBoolean(kv[1].trim()));
      }
    }
    return map;
  }
}
