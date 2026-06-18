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
import java.util.UUID;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;

/**
 * Parses a SQL {@code SELECT} statement into a {@link MappingDefinition}.
 *
 * <p>Uses JSqlParser for syntax validation. The full SQL is stored in {@link
 * MappingDefinition#query()} so that the {@code connector-postgresql} reader can use it directly as
 * the source query. Simple {@code col AS alias} items are also extracted as {@link ColumnMapping}
 * entries.
 *
 * <p>Example:
 *
 * <pre>{@code
 * SELECT cust_id AS id, TRIM(email_addr) AS email FROM public.customers
 * }</pre>
 */
public final class SqlMappingParser implements MappingParser {

  @Override
  public String formatId() {
    return "sql";
  }

  @Override
  public ValidationResult validate(String content) {
    if (content == null || content.isBlank()) {
      return ValidationResult.failed(
          List.of(ValidationError.error("", "SQL content must not be empty")));
    }
    try {
      var stmt = CCJSqlParserUtil.parse(content);
      if (!(stmt instanceof net.sf.jsqlparser.statement.select.Select)) {
        return ValidationResult.failed(
            List.of(ValidationError.error("", "Only SELECT statements are supported")));
      }
    } catch (JSQLParserException e) {
      return ValidationResult.failed(
          List.of(ValidationError.syntaxError(-1, -1, extractMessage(e))));
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
      return buildDefinition(content);
    } catch (JSQLParserException e) {
      throw new MappingParseException(extractMessage(e), e);
    }
  }

  private MappingDefinition buildDefinition(String sql) throws JSQLParserException {
    var stmt = (net.sf.jsqlparser.statement.select.Select) CCJSqlParserUtil.parse(sql);
    var plainSelect = (PlainSelect) stmt;
    var srcRef = extractSourceTableRef(plainSelect);
    var columns = extractColumnMappings(plainSelect);
    String id = "sql-" + UUID.randomUUID().toString().substring(0, 8);
    var tgtRef = new TableRef(new DatabaseRef("target", DatabaseType.POSTGRESQL), "", "target");
    return new MappingDefinition(id, srcRef, tgtRef, columns, MappingFormat.SQL, sql);
  }

  private TableRef extractSourceTableRef(PlainSelect plainSelect) {
    var fromItem = plainSelect.getFromItem();
    if (fromItem instanceof Table t) {
      String schema = t.getSchemaName() != null ? t.getSchemaName() : "";
      String tableName = t.getName() != null ? t.getName() : "source";
      return new TableRef(new DatabaseRef("source", DatabaseType.POSTGRESQL), schema, tableName);
    }
    return new TableRef(new DatabaseRef("source", DatabaseType.POSTGRESQL), "", "source");
  }

  private List<ColumnMapping> extractColumnMappings(PlainSelect plainSelect) {
    var mappings = new ArrayList<ColumnMapping>();
    for (SelectItem<?> item : plainSelect.getSelectItems()) {
      if (item.getExpression() instanceof AllColumns) {
        break;
      }
      String alias = item.getAlias() != null ? item.getAlias().getName() : null;
      String expr = item.getExpression().toString();
      if (alias != null) {
        boolean isSimpleCol = !expr.contains("(") && !expr.contains(" ");
        if (isSimpleCol) {
          mappings.add(new ColumnMapping(expr, alias));
        }
      }
    }
    return List.copyOf(mappings);
  }

  private String extractMessage(JSQLParserException e) {
    if (e.getCause() != null) {
      return e.getCause().getMessage();
    }
    return e.getMessage() != null ? e.getMessage() : "SQL parse error";
  }
}
