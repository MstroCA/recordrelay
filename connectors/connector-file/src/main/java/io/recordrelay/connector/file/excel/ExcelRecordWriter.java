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

import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/** Writes records to an Excel .xlsx file using SXSSF (streaming, memory-efficient). */
public final class ExcelRecordWriter implements RecordWriter {

  private SXSSFWorkbook workbook;
  private org.apache.poi.ss.usermodel.Sheet sheet;
  private List<String> headers;
  private int rowNum = 0;
  private String path;

  @Override
  public void open(ConnectionProfile profile, TableRef table)
      throws ConnectorException {
    this.path = profile.database();
    workbook = new SXSSFWorkbook(100);
    sheet = workbook.createSheet("Sheet1");
  }

  @Override
  public void write(DataRecord record) throws ConnectorException {
    if (headers == null) {
      headers = List.copyOf(record.fieldNames());
      var headerRow = sheet.createRow(rowNum++);
      for (int i = 0; i < headers.size(); i++) {
        headerRow.createCell(i).setCellValue(headers.get(i));
      }
    }
    var row = sheet.createRow(rowNum++);
    for (int i = 0; i < headers.size(); i++) {
      var cell = row.createCell(i);
      var val = record.get(headers.get(i));
      if (val instanceof Number n) {
        cell.setCellValue(n.doubleValue());
      } else if (val instanceof Boolean b) {
        cell.setCellValue(b);
      } else {
        cell.setCellValue(val == null ? "" : val.toString());
      }
    }
  }

  @Override
  public void flush() throws ConnectorException {
    // SXSSF flushes automatically based on row window.
  }

  @Override
  public void close() throws ConnectorException {
    if (workbook != null) {
      try (var fos = new FileOutputStream(path)) {
        workbook.write(fos);
        workbook.dispose();
        workbook.close();
      } catch (IOException e) {
        throw new ConnectorException("Error writing Excel file: " + path, e);
      }
    }
  }
}
