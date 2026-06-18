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
package io.recordrelay.mapping;

import io.recordrelay.core.domain.ColumnMapping;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.MappingFormat;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.domain.ValidationError;
import io.recordrelay.core.domain.ValidationResult;
import io.recordrelay.core.exception.MappingParseException;
import io.recordrelay.core.port.in.MappingParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.MarkedYAMLException;

/**
 * Parses a YAML mapping document into a {@link MappingDefinition}.
 *
 * <p>Expected format:
 *
 * <pre>{@code
 * id: mapping-001
 * name: my-mapping
 * source:
 *   schema: public
 *   table: customers
 *   dbType: POSTGRESQL
 * target:
 *   table: users
 *   dbType: MONGODB
 * columns:
 *   - source: cust_id
 *     target: id
 *     transform: "cast:int"
 *   - source: email
 *     target: email
 * }</pre>
 */
public final class YamlMappingParser implements MappingParser {

  @Override
  public String formatId() {
    return "yaml";
  }

  @Override
  public ValidationResult validate(String content) {
    if (content == null || content.isBlank()) {
      return ValidationResult.failed(
          List.of(ValidationError.error("", "Mapping content must not be empty")));
    }
    Map<String, Object> data;
    try {
      data = loadYaml(content);
    } catch (MarkedYAMLException e) {
      var mark = e.getProblemMark();
      int line = mark != null ? mark.getLine() + 1 : -1;
      int col = mark != null ? mark.getColumn() + 1 : -1;
      return ValidationResult.failed(
          List.of(ValidationError.syntaxError(line, col, e.getProblem())));
    } catch (Exception e) {
      return ValidationResult.failed(List.of(ValidationError.error("", e.getMessage())));
    }
    if (data == null) {
      return ValidationResult.failed(List.of(ValidationError.error("", "YAML document is empty")));
    }
    return validateStructure(data);
  }

  private ValidationResult validateStructure(Map<String, Object> data) {
    var errors = new ArrayList<ValidationError>();
    Object id = data.get("id");
    if (id == null || id.toString().isBlank()) {
      errors.add(ValidationError.error("id", "Missing required field 'id'"));
    }
    if (!hasTableField(data, "source")) {
      errors.add(ValidationError.error("source", "Missing required field 'source.table'"));
    }
    if (!hasTableField(data, "target")) {
      errors.add(ValidationError.error("target", "Missing required field 'target.table'"));
    }
    if (!errors.isEmpty()) {
      return ValidationResult.failed(errors);
    }
    return ValidationResult.ok();
  }

  @SuppressWarnings("unchecked")
  private boolean hasTableField(Map<String, Object> data, String key) {
    Object section = data.get(key);
    if (!(section instanceof Map)) {
      return false;
    }
    Map<String, Object> map = (Map<String, Object>) section;
    Object table = map.get("table");
    return table != null && !table.toString().isBlank();
  }

  @Override
  public MappingDefinition parse(String content) throws MappingParseException {
    var vr = validate(content);
    if (!vr.valid()) {
      var first = vr.errors().get(0);
      throw new MappingParseException(first.message(), first.line(), first.column());
    }
    try {
      return buildDefinition(loadYaml(content));
    } catch (Exception e) {
      throw new MappingParseException(e.getMessage(), e);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> loadYaml(String content) {
    return new Yaml(new SafeConstructor(new LoaderOptions())).load(content);
  }

  @SuppressWarnings("unchecked")
  private MappingDefinition buildDefinition(Map<String, Object> data) {
    String id = data.get("id").toString();
    var srcRef = buildTableRef((Map<String, Object>) data.get("source"), "source");
    var tgtRef = buildTableRef((Map<String, Object>) data.get("target"), "target");
    Object colsObj = data.get("columns");
    var columns =
        (colsObj instanceof List)
            ? buildColumnMappings((List<?>) colsObj)
            : List.<ColumnMapping>of();
    return new MappingDefinition(id, srcRef, tgtRef, columns, MappingFormat.YAML, null);
  }

  private TableRef buildTableRef(Map<String, Object> node, String placeholder) {
    String tableName = str(node.get("table"), placeholder);
    String schema = str(node.get("schema"), "");
    String dbTypeStr = str(node.get("dbType"), "POSTGRESQL");
    DatabaseType dbType = parseDatabaseType(dbTypeStr);
    return new TableRef(new DatabaseRef(placeholder, dbType), schema, tableName);
  }

  private String str(Object val, String fallback) {
    return val != null ? val.toString() : fallback;
  }

  private DatabaseType parseDatabaseType(String str) {
    try {
      return DatabaseType.valueOf(str.toUpperCase());
    } catch (IllegalArgumentException e) {
      return DatabaseType.POSTGRESQL;
    }
  }

  @SuppressWarnings("unchecked")
  private List<ColumnMapping> buildColumnMappings(List<?> cols) {
    var mappings = new ArrayList<ColumnMapping>();
    for (var col : cols) {
      if (!(col instanceof Map)) {
        continue;
      }
      var map = (Map<String, Object>) col;
      String src = str(map.get("source"), "");
      String tgt = str(map.get("target"), src);
      String transform = map.containsKey("transform") ? str(map.get("transform"), null) : null;
      if (!src.isBlank()) {
        mappings.add(new ColumnMapping(src, tgt, transform));
      }
    }
    return List.copyOf(mappings);
  }
}
