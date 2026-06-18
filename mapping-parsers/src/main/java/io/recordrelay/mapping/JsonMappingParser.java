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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a JSON mapping document into a {@link MappingDefinition}.
 *
 * <p>Expected format:
 *
 * <pre>{@code
 * {
 *   "id": "mapping-001",
 *   "name": "my-mapping",
 *   "source": { "schema": "public", "table": "customers", "dbType": "POSTGRESQL" },
 *   "target": { "table": "users", "dbType": "MONGODB" },
 *   "columns": [
 *     { "source": "cust_id", "target": "id", "transform": "cast:int" },
 *     { "source": "email",   "target": "email" }
 *   ]
 * }
 * }</pre>
 *
 * The {@code dbType} field defaults to {@code POSTGRESQL} for source and target when omitted.
 */
public final class JsonMappingParser implements MappingParser {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public String formatId() {
    return "json";
  }

  @Override
  public ValidationResult validate(String content) {
    if (content == null || content.isBlank()) {
      return ValidationResult.failed(
          List.of(ValidationError.error("", "Mapping content must not be empty")));
    }
    try {
      var root = MAPPER.readTree(content);
      return validateStructure(root);
    } catch (JsonParseException e) {
      var loc = e.getLocation();
      return ValidationResult.failed(
          List.of(
              ValidationError.syntaxError(
                  (int) loc.getLineNr(), (int) loc.getColumnNr(), e.getOriginalMessage())));
    } catch (IOException e) {
      return ValidationResult.failed(List.of(ValidationError.error("", e.getMessage())));
    }
  }

  private ValidationResult validateStructure(JsonNode root) {
    var errors = new ArrayList<ValidationError>();
    if (!root.has("id") || root.get("id").asText().isBlank()) {
      errors.add(ValidationError.error("id", "Missing required field 'id'"));
    }
    if (!root.has("source") || !root.get("source").has("table")) {
      errors.add(ValidationError.error("source", "Missing required field 'source.table'"));
    }
    if (!root.has("target") || !root.get("target").has("table")) {
      errors.add(ValidationError.error("target", "Missing required field 'target.table'"));
    }
    if (!errors.isEmpty()) {
      return ValidationResult.failed(errors);
    }
    return ValidationResult.ok();
  }

  @Override
  public MappingDefinition parse(String content) throws MappingParseException {
    var vr = validate(content);
    if (!vr.valid()) {
      var first = vr.errors().get(0);
      throw new MappingParseException(first.message(), first.line(), first.column());
    }
    try {
      return buildDefinition(MAPPER.readTree(content));
    } catch (IOException e) {
      throw new MappingParseException(e.getMessage(), e);
    }
  }

  private MappingDefinition buildDefinition(JsonNode root) {
    String id = root.get("id").asText();
    var srcNode = root.get("source");
    var tgtNode = root.get("target");
    var srcRef = buildTableRef(srcNode, "source");
    var tgtRef = buildTableRef(tgtNode, "target");
    var columns = buildColumnMappings(root.path("columns"));
    return new MappingDefinition(id, srcRef, tgtRef, columns, MappingFormat.JSON, null);
  }

  private TableRef buildTableRef(JsonNode node, String placeholder) {
    String tableName = node.path("table").asText(placeholder);
    String schema = node.path("schema").asText("");
    String dbTypeStr = node.path("dbType").asText("POSTGRESQL");
    DatabaseType dbType = parseDatabaseType(dbTypeStr);
    return new TableRef(new DatabaseRef(placeholder, dbType), schema, tableName);
  }

  private DatabaseType parseDatabaseType(String str) {
    try {
      return DatabaseType.valueOf(str.toUpperCase());
    } catch (IllegalArgumentException e) {
      return DatabaseType.POSTGRESQL;
    }
  }

  private List<ColumnMapping> buildColumnMappings(JsonNode columnsNode) {
    if (columnsNode.isMissingNode() || !columnsNode.isArray()) {
      return List.of();
    }
    var mappings = new ArrayList<ColumnMapping>();
    for (var col : columnsNode) {
      String src = col.path("source").asText("");
      String tgt = col.path("target").asText(src);
      String transform = col.has("transform") ? col.get("transform").asText() : null;
      if (!src.isBlank()) {
        mappings.add(new ColumnMapping(src, tgt, transform));
      }
    }
    return List.copyOf(mappings);
  }
}
