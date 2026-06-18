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
package io.recordrelay.core.engine;

import io.recordrelay.core.domain.ColumnMapping;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.MissingColumnStrategy;
import io.recordrelay.core.domain.TransferOptions;
import io.recordrelay.core.domain.TypeCoercionStrategy;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordTransformer;
import io.recordrelay.core.port.out.TransformFunction;
import io.recordrelay.core.spi.TransformFunctionRegistry;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Applies column alias mapping, basic type coercion, and missing-value handling to each source
 * record.
 *
 * <p>When {@link MappingDefinition#columnMappings()} is empty this transformer acts as a
 * passthrough (source fields are forwarded under the same names). Otherwise only mapped source
 * columns are included in the output record.
 */
public final class MappingTransformer implements RecordTransformer {

  private final MappingDefinition mapping;
  private final TransferOptions options;
  private final Function<String, Optional<TransformFunction>> transformLookup;

  public MappingTransformer(MappingDefinition mapping, TransferOptions options) {
    this(mapping, options, TransformFunctionRegistry::find);
  }

  /** Package-private for testing — allows injecting a custom transform lookup. */
  MappingTransformer(
      MappingDefinition mapping,
      TransferOptions options,
      Function<String, Optional<TransformFunction>> transformLookup) {
    this.mapping = Objects.requireNonNull(mapping, "mapping");
    this.options = Objects.requireNonNull(options, "options");
    this.transformLookup = Objects.requireNonNull(transformLookup, "transformLookup");
  }

  @Override
  public DataRecord transform(DataRecord source) throws ConnectorException {
    if (mapping.columnMappings().isEmpty()) {
      return source;
    }
    var fields = new LinkedHashMap<String, Object>();
    for (ColumnMapping cm : mapping.columnMappings()) {
      if (!source.hasField(cm.sourceColumn())) {
        fields.put(cm.targetColumn(), handleMissing(source, cm.sourceColumn(), cm.targetColumn()));
        continue;
      }
      fields.put(cm.targetColumn(), resolveValue(source.get(cm.sourceColumn()), cm));
    }
    return new DataRecord(fields);
  }

  private Object resolveValue(Object raw, ColumnMapping cm) throws ConnectorException {
    if (cm.transform() != null && !cm.transform().isBlank()) {
      return applyTransform(raw, cm.transform());
    }
    return coerce(raw, cm.targetColumn());
  }

  private Object applyTransform(Object value, String transformSpec) throws ConnectorException {
    int colonIdx = transformSpec.indexOf(':');
    String funcId = colonIdx >= 0 ? transformSpec.substring(0, colonIdx) : transformSpec;
    String argsStr = colonIdx >= 0 ? transformSpec.substring(colonIdx + 1) : "";
    String[] args = argsStr.isEmpty() ? new String[0] : argsStr.split(":", -1);
    var fnOpt = transformLookup.apply(funcId);
    if (fnOpt.isEmpty()) {
      return value;
    }
    return fnOpt.get().apply(value, args);
  }

  private Object handleMissing(DataRecord source, String srcCol, String tgtCol)
      throws ConnectorException {
    if (options.missingColumnStrategy() == MissingColumnStrategy.FAIL) {
      throw new ConnectorException(
          "Source record is missing required column '"
              + srcCol
              + "' (target: '"
              + tgtCol
              + "'). "
              + "Set MissingColumnStrategy.NULL_FILL or SKIP_ROW to handle this.");
    }
    if (options.missingColumnStrategy() == MissingColumnStrategy.SKIP_ROW) {
      throw new SkipRowException(
          "Skipping row: source column '" + srcCol + "' not present", source);
    }
    return null;
  }

  private Object coerce(Object value, String targetColumn) throws ConnectorException {
    if (value == null) {
      return null;
    }
    if (options.coercionStrategy() == TypeCoercionStrategy.STRICT) {
      return value;
    }
    if (options.coercionStrategy() == TypeCoercionStrategy.LENIENT
        || options.coercionStrategy() == TypeCoercionStrategy.SKIP_ROW) {
      return attemptCoercion(value, targetColumn);
    }
    return value;
  }

  private Object attemptCoercion(Object value, String targetColumn) throws ConnectorException {
    if (!(value instanceof String strVal)) {
      return value;
    }
    try {
      return coerceString(strVal, targetColumn);
    } catch (NumberFormatException e) {
      throw new ConnectorException(
          "Type coercion failed for column '" + targetColumn + "': cannot parse '" + strVal + "'",
          e);
    }
  }

  private Object coerceString(String strVal, String targetColumn) {
    if (isIntegerType(targetColumn)) {
      return Long.parseLong(strVal.trim());
    }
    if (isFloatType(targetColumn)) {
      return Double.parseDouble(strVal.trim());
    }
    if (isBooleanType(targetColumn)) {
      return Boolean.parseBoolean(strVal.trim());
    }
    return strVal;
  }

  private boolean isIntegerType(String col) {
    var lower = col.toLowerCase();
    return lower.contains("int") || lower.contains("serial");
  }

  private boolean isFloatType(String col) {
    var lower = col.toLowerCase();
    return lower.contains("float")
        || lower.contains("double")
        || lower.contains("numeric")
        || lower.contains("decimal");
  }

  private boolean isBooleanType(String col) {
    return col.toLowerCase().contains("bool");
  }
}
