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
import java.util.UUID;

/**
 * Parses a MongoDB aggregation pipeline document into a {@link MappingDefinition}.
 *
 * <p>The input must be a JSON array where at least one stage is a {@code $project}. Simple field
 * references ({@code "$field"}) are extracted as {@link ColumnMapping} entries.
 *
 * <p>Example:
 *
 * <pre>{@code
 * [
 *   {
 *     "$project": {
 *       "id": "$cust_id",
 *       "email": { "$trim": { "input": "$email_addr" } }
 *     }
 *   }
 * ]
 * }</pre>
 */
public final class NoSqlMappingParser implements MappingParser {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public String formatId() {
    return "nosql-query";
  }

  @Override
  public ValidationResult validate(String content) {
    if (content == null || content.isBlank()) {
      return ValidationResult.failed(
          List.of(ValidationError.error("", "NoSQL pipeline content must not be empty")));
    }
    try {
      var root = MAPPER.readTree(content);
      return validatePipeline(root);
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

  private ValidationResult validatePipeline(JsonNode root) {
    if (!root.isArray()) {
      return ValidationResult.failed(
          List.of(
              ValidationError.error("", "NoSQL pipeline must be a JSON array of stage documents")));
    }
    if (root.isEmpty()) {
      return ValidationResult.failed(
          List.of(ValidationError.error("", "NoSQL pipeline must not be empty")));
    }
    boolean hasProject = false;
    for (var stage : root) {
      if (stage.has("$project")) {
        hasProject = true;
        break;
      }
    }
    if (!hasProject) {
      return ValidationResult.failed(
          List.of(
              ValidationError.error(
                  "$project", "Pipeline must contain at least one $project stage")));
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
      return buildDefinition(MAPPER.readTree(content), content);
    } catch (IOException e) {
      throw new MappingParseException(e.getMessage(), e);
    }
  }

  private MappingDefinition buildDefinition(JsonNode root, String rawQuery) {
    var columns = new ArrayList<ColumnMapping>();
    for (var stage : root) {
      if (stage.has("$project")) {
        extractProjectMappings(stage.get("$project"), columns);
        break;
      }
    }
    String id = "nosql-" + UUID.randomUUID().toString().substring(0, 8);
    var srcRef = new TableRef(new DatabaseRef("source", DatabaseType.MONGODB), "", "source");
    var tgtRef = new TableRef(new DatabaseRef("target", DatabaseType.MONGODB), "", "target");
    return new MappingDefinition(
        id, srcRef, tgtRef, List.copyOf(columns), MappingFormat.NOSQL_QUERY, rawQuery);
  }

  private void extractProjectMappings(JsonNode projectStage, List<ColumnMapping> out) {
    var fieldNames = projectStage.fieldNames();
    while (fieldNames.hasNext()) {
      String targetField = fieldNames.next();
      JsonNode expr = projectStage.get(targetField);
      if (expr.isTextual() && expr.asText().startsWith("$")) {
        String sourceField = expr.asText().substring(1);
        out.add(new ColumnMapping(sourceField, targetField));
      }
    }
  }
}
