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
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.RecordReader;
import java.util.Optional;

/** Template {@link RecordReader} stub — implement to read from your data source. */
public final class TemplateRecordReader implements RecordReader {

  @Override
  public void open(ConnectionProfile profile, TableRef table, MappingDefinition mapping)
      throws ConnectorException {
    throw new ConnectorException("TemplateRecordReader.open() not implemented");
  }

  @Override
  public Optional<DataRecord> readNext() throws ConnectorException {
    return Optional.empty();
  }

  @Override
  public boolean hasMore() {
    return false;
  }

  @Override
  public void close() throws ConnectorException {
    // Nothing to close.
  }
}
