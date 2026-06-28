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
package io.recordrelay.connector.bigquery;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardTableDefinition;
import com.google.cloud.bigquery.TableId;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordReader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads rows from a BigQuery table using {@code listTableData}.
 *
 * <p>The BigQuery client handles automatic result pagination internally. The returned iterator is
 * lazy: new pages are fetched on demand, avoiding full materialization of large result sets.
 */
public final class BigQueryRecordReader implements RecordReader {

  private static final Logger LOG = LoggerFactory.getLogger(BigQueryRecordReader.class);

  private Schema schema;
  private Iterator<FieldValueList> rowIterator;

  @Override
  public void open(ConnectionProfile profile, TableRef table) throws ConnectorException {
    try {
      BigQuery bq = BigQueryClientFactory.create(profile);
      var tableId = TableId.of(profile.host(), table.database().name(), table.tableName());
      var bqTable = bq.getTable(tableId);
      if (bqTable == null) {
        throw new ConnectorException("BigQuery table not found: " + tableId);
      }
      schema = bqTable.<StandardTableDefinition>getDefinition().getSchema();
      var result = bq.listTableData(tableId);
      rowIterator = result.iterateAll().iterator();
      LOG.debug(
          "Opened BigQuery reader on '{}' in project '{}'", table.qualifiedName(), profile.host());
    } catch (BigQueryException e) {
      throw new ConnectorException(
          "Failed to open BigQuery reader on '" + table.qualifiedName() + "': " + e.getMessage(),
          e);
    }
  }

  @Override
  public Optional<DataRecord> readNext() throws ConnectorException {
    if (!rowIterator.hasNext()) {
      return Optional.empty();
    }
    try {
      return Optional.of(rowToRecord(rowIterator.next()));
    } catch (BigQueryException e) {
      throw new ConnectorException("Error reading BigQuery row: " + e.getMessage(), e);
    }
  }

  @Override
  public boolean hasMore() {
    return rowIterator.hasNext();
  }

  @Override
  public void close() {
    rowIterator = null;
  }

  private DataRecord rowToRecord(FieldValueList row) {
    var fields = new LinkedHashMap<String, Object>(row.size());
    if (schema != null) {
      for (Field field : schema.getFields()) {
        fields.put(field.getName(), extractValue(row.get(field.getName()), field));
      }
    } else {
      for (int i = 0; i < row.size(); i++) {
        fields.put("col_" + i, fieldValueToObject(row.get(i)));
      }
    }
    return new DataRecord(fields);
  }

  private static Object extractValue(FieldValue fv, Field field) {
    if (fv.isNull()) {
      return null;
    }
    return switch (field.getType().name()) {
      case "STRING", "BYTES", "DATE", "TIME", "DATETIME", "GEOGRAPHY", "JSON" ->
          fv.getStringValue();
      case "INTEGER", "INT64" -> fv.getLongValue();
      case "FLOAT", "FLOAT64" -> fv.getDoubleValue();
      case "NUMERIC", "BIGNUMERIC" -> fv.getNumericValue();
      case "BOOLEAN", "BOOL" -> fv.getBooleanValue();
      case "TIMESTAMP" -> fv.getTimestampInstant();
      case "RECORD", "STRUCT" -> fv.getRecordValue().toString();
      default -> fv.getStringValue();
    };
  }

  private static Object fieldValueToObject(FieldValue fv) {
    if (fv.isNull()) {
      return null;
    }
    return switch (fv.getAttribute()) {
      case PRIMITIVE -> fv.getStringValue();
      case REPEATED ->
          fv.getRepeatedValue().stream().map(BigQueryRecordReader::fieldValueToObject).toList();
      case RECORD -> fv.getRecordValue().toString();
      default -> fv.getStringValue();
    };
  }
}
