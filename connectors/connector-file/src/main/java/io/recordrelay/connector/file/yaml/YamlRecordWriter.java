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

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordWriter;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/** Writes records to a YAML file as a stream of documents separated by {@code ---}. */
public final class YamlRecordWriter implements RecordWriter {

  private BufferedWriter writer;
  private Yaml yaml;

  @Override
  public void open(ConnectionProfile profile, TableRef table) throws ConnectorException {
    try {
      writer = new BufferedWriter(new FileWriter(profile.database()));
      var opts = new DumperOptions();
      opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
      yaml = new Yaml(opts);
    } catch (IOException e) {
      throw new ConnectorException(
          "Failed to open YAML file for writing: " + profile.database(), e);
    }
  }

  @Override
  public void write(DataRecord record) throws ConnectorException {
    var map = new LinkedHashMap<String, Object>();
    for (String field : record.fieldNames()) {
      map.put(field, record.get(field));
    }
    try {
      writer.write("---");
      writer.newLine();
      writer.write(yaml.dump(map));
    } catch (IOException e) {
      throw new ConnectorException("Error writing YAML record", e);
    }
  }

  @Override
  public void flush() throws ConnectorException {
    try {
      if (writer != null) {
        writer.flush();
      }
    } catch (IOException e) {
      throw new ConnectorException("Error flushing YAML writer", e);
    }
  }

  @Override
  public void close() throws ConnectorException {
    try {
      if (writer != null) {
        writer.close();
      }
    } catch (IOException e) {
      throw new ConnectorException("Error closing YAML writer", e);
    }
  }
}
