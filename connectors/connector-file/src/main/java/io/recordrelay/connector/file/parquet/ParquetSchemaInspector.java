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
package io.recordrelay.connector.file.parquet;

import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.SchemaInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.util.HadoopInputFile;

/** Reads the Avro schema from a Parquet file to infer column names and types. */
public final class ParquetSchemaInspector implements SchemaInspector {

  @Override
  public List<TableRef> listTables(ConnectionProfile profile, DatabaseRef database)
      throws ConnectorException {
    var path = Path.of(profile.database());
    return List.of(new TableRef(database, "", path.getFileName().toString()));
  }

  @Override
  public List<ColumnMeta> inspectColumns(ConnectionProfile profile, TableRef table)
      throws ConnectorException {
    var hadoopPath = new org.apache.hadoop.fs.Path(profile.database());
    var conf = new Configuration();
    try (var reader = ParquetFileReader.open(HadoopInputFile.fromPath(hadoopPath, conf))) {
      var parquetSchema = reader.getFileMetaData().getSchema();
      var result = new ArrayList<ColumnMeta>();
      var ordinal = new AtomicInteger(1);
      for (var field : parquetSchema.getFields()) {
        result.add(
            new ColumnMeta(
                field.getName(),
                field.asPrimitiveType().getPrimitiveTypeName().name(),
                field.getRepetition() != org.apache.parquet.schema.Type.Repetition.REQUIRED,
                false,
                false,
                ordinal.getAndIncrement(),
                null));
      }
      return List.copyOf(result);
    } catch (IOException e) {
      throw new ConnectorException("Failed to inspect Parquet schema: " + e.getMessage(), e);
    }
  }
}
