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
package io.recordrelay.connector.file.excel;

import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.SchemaInspector;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Reads the header row of the first sheet in an .xlsx file. */
public final class ExcelSchemaInspector implements SchemaInspector {

  @Override
  public List<TableRef> listTables(ConnectionProfile profile, DatabaseRef database)
      throws ConnectorException {
    var path = Path.of(profile.database());
    return List.of(new TableRef(database, "", path.getFileName().toString()));
  }

  @Override
  public List<ColumnMeta> inspectColumns(ConnectionProfile profile, TableRef table)
      throws ConnectorException {
    try (var fis = new FileInputStream(profile.database());
        var wb = new XSSFWorkbook(fis)) {
      var sheet = wb.getSheetAt(0);
      var headerRow = sheet.getRow(0);
      if (headerRow == null) {
        return List.of();
      }
      var result = new ArrayList<ColumnMeta>();
      var ordinal = new AtomicInteger(1);
      for (var cell : headerRow) {
        String name =
            cell.getCellType() == CellType.STRING
                ? cell.getStringCellValue()
                : String.valueOf(cell.getColumnIndex());
        result.add(
            new ColumnMeta(name, "STRING", true, false, false, ordinal.getAndIncrement(), null));
      }
      return List.copyOf(result);
    } catch (IOException e) {
      throw new ConnectorException("Failed to inspect Excel headers: " + e.getMessage(), e);
    }
  }
}
