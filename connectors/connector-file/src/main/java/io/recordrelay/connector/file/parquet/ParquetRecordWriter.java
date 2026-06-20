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

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordWriter;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;

/**
 * Writes records to a Parquet file using AvroParquetWriter.
 *
 * <p>Schema is inferred from the first record; all fields are written as nullable strings.
 */
public final class ParquetRecordWriter implements RecordWriter {

  private ParquetWriter<GenericRecord> writer;
  private Schema avroSchema;
  private String path;

  @Override
  public void open(ConnectionProfile profile, TableRef table) throws ConnectorException {
    this.path = profile.database();
    // Writer is created lazily when the first record arrives (to infer schema).
  }

  @Override
  public void write(DataRecord record) throws ConnectorException {
    if (writer == null) {
      avroSchema = buildSchema(record);
      try {
        writer =
            AvroParquetWriter.<GenericRecord>builder(new LocalOutputFile(Path.of(path)))
                .withSchema(avroSchema)
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .build();
      } catch (IOException e) {
        throw new ConnectorException("Failed to create Parquet writer: " + path, e);
      }
    }
    var avroRecord = new GenericData.Record(avroSchema);
    for (String field : record.fieldNames()) {
      var val = record.get(field);
      avroRecord.put(field, val == null ? null : val.toString());
    }
    try {
      writer.write(avroRecord);
    } catch (IOException e) {
      throw new ConnectorException("Error writing Parquet record", e);
    }
  }

  @Override
  public void flush() throws ConnectorException {
    // Parquet buffers internally; flushing happens on close.
  }

  @Override
  public void close() throws ConnectorException {
    try {
      if (writer != null) {
        writer.close();
      }
    } catch (IOException e) {
      throw new ConnectorException("Error closing Parquet writer", e);
    }
  }

  private Schema buildSchema(DataRecord sample) {
    var sb = new StringBuilder();
    sb.append(
        "{\"type\":\"record\",\"name\":\"Row\",\"namespace\":\"io.recordrelay\",\"fields\":[");
    boolean first = true;
    for (String field : sample.fieldNames()) {
      if (!first) {
        sb.append(",");
      }
      sb.append("{\"name\":\"")
          .append(field)
          .append("\",\"type\":[\"null\",\"string\"],\"default\":null}");
      first = false;
    }
    sb.append("]}");
    return new Schema.Parser().parse(sb.toString());
  }
}
