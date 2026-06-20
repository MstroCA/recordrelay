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
package io.recordrelay.connector.file.yaml;

import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.SchemaInspector;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.yaml.snakeyaml.Yaml;

/** Reads the first YAML document to infer keys as column names. */
public final class YamlSchemaInspector implements SchemaInspector {

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
    try (var reader = new FileReader(profile.database())) {
      var yaml = new Yaml();
      var rawIter = yaml.loadAll(reader).iterator();
      if (!rawIter.hasNext()) {
        return List.of();
      }
      var rawDoc = rawIter.next();
      if (!(rawDoc instanceof Map)) {
        return List.of();
      }
      @SuppressWarnings("unchecked")
      var doc = (Map<String, Object>) rawDoc;
      var result = new ArrayList<ColumnMeta>();
      var ordinal = new AtomicInteger(1);
      for (var key : doc.keySet()) {
        result.add(new ColumnMeta(key, "ANY", true, false, false, ordinal.getAndIncrement(), null));
      }
      return List.copyOf(result);
    } catch (IOException e) {
      throw new ConnectorException("Failed to inspect YAML columns: " + e.getMessage(), e);
    }
  }
}
