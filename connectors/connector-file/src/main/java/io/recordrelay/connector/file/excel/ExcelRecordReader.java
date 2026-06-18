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

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Streams rows from the first sheet of an Excel .xlsx file using XSSF. */
public final class ExcelRecordReader implements RecordReader {

  private XSSFWorkbook workbook;
  private List<String> headers;
  private Iterator<Row> rowIterator;

  @Override
  public void open(ConnectionProfile profile, TableRef table, MappingDefinition mapping)
      throws ConnectorException {
    try {
      workbook = new XSSFWorkbook(new FileInputStream(profile.database()));
      var sheet = workbook.getSheetAt(0);
      var headerRow = sheet.getRow(0);
      headers = new ArrayList<>();
      if (headerRow != null) {
        for (var cell : headerRow) {
          headers.add(
              cell.getCellType() == CellType.STRING
                  ? cell.getStringCellValue()
                  : "col" + cell.getColumnIndex());
        }
      }
      rowIterator = sheet.rowIterator();
      if (rowIterator.hasNext()) {
        rowIterator.next(); // skip header
      }
    } catch (IOException e) {
      throw new ConnectorException("Failed to open Excel file: " + profile.database(), e);
    }
  }

  @Override
  public Optional<DataRecord> readNext() throws ConnectorException {
    if (!rowIterator.hasNext()) {
      return Optional.empty();
    }
    var row = rowIterator.next();
    var fields = new LinkedHashMap<String, Object>(headers.size());
    for (int i = 0; i < headers.size(); i++) {
      var cell = row.getCell(i);
      fields.put(headers.get(i), cell == null ? null : getCellValue(cell));
    }
    return Optional.of(new DataRecord(fields));
  }

  @Override
  public boolean hasMore() {
    return rowIterator.hasNext();
  }

  @Override
  public void close() throws ConnectorException {
    try {
      if (workbook != null) {
        workbook.close();
      }
    } catch (IOException e) {
      throw new ConnectorException("Error closing Excel reader", e);
    }
  }

  private Object getCellValue(org.apache.poi.ss.usermodel.Cell cell) {
    return switch (cell.getCellType()) {
      case STRING -> cell.getStringCellValue();
      case NUMERIC -> cell.getNumericCellValue();
      case BOOLEAN -> cell.getBooleanCellValue();
      default -> cell.toString();
    };
  }
}
