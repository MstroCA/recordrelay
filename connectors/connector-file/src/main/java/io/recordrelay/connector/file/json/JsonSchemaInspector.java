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
package io.recordrelay.connector.file.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.SchemaInspector;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Reads the first JSON Line to infer column names. */
public final class JsonSchemaInspector implements SchemaInspector {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public List<TableRef> listTables(ConnectionProfile profile, DatabaseRef database)
      throws ConnectorException {
    var path = Path.of(profile.database());
    return List.of(new TableRef(database, "", path.getFileName().toString()));
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<ColumnMeta> inspectColumns(ConnectionProfile profile, TableRef table)
      throws ConnectorException {
    try (var reader = new BufferedReader(new FileReader(profile.database()))) {
      var firstLine = reader.readLine();
      if (firstLine == null || firstLine.isBlank()) {
        return List.of();
      }
      var node = MAPPER.readTree(firstLine);
      var result = new ArrayList<ColumnMeta>();
      var ordinal = new AtomicInteger(1);
      node.fieldNames()
          .forEachRemaining(
              name ->
                  result.add(
                      new ColumnMeta(
                          name, "ANY", true, false, false, ordinal.getAndIncrement(), null)));
      return List.copyOf(result);
    } catch (IOException e) {
      throw new ConnectorException("Failed to inspect JSON columns: " + e.getMessage(), e);
    }
  }
}
