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
package io.recordrelay.connector.template;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;

import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordWriter;

/** Template {@link RecordWriter} stub — implement to write to your data source. */
public final class TemplateRecordWriter implements RecordWriter {

  @Override
  public void open(ConnectionProfile profile, TableRef table)
      throws ConnectorException {
    throw new ConnectorException("TemplateRecordWriter.open() not implemented");
  }

  @Override
  public void write(DataRecord record) throws ConnectorException {
    throw new ConnectorException("TemplateRecordWriter.write() not implemented");
  }

  @Override
  public void flush() throws ConnectorException {
    // Nothing to flush.
  }

  @Override
  public void close() throws ConnectorException {
    // Nothing to close.
  }
}
