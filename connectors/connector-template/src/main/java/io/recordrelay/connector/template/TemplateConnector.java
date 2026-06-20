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
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.out.ContextProviderPort;
import io.recordrelay.core.port.out.RecordReader;
import io.recordrelay.core.port.out.RecordWriter;
import io.recordrelay.core.port.out.SchemaInspector;
import java.util.List;

/**
 * Template connector — copy this module to implement a custom {@link ContextProviderPort}.
 *
 * <p>Steps:
 *
 * <ol>
 *   <li>Rename the module and all classes.
 *   <li>Override {@link #connectorId()} with a unique ID.
 *   <li>Override {@link #supports(ConnectionProfile)} to match your {@code DatabaseType}.
 *   <li>Implement {@link #testConnection}, {@link #listDatabases}, and the reader/writer/inspector.
 *   <li>Register your connector in {@code
 *       META-INF/services/io.recordrelay.core.port.out.ContextProviderPort}.
 * </ol>
 *
 * <p>This class intentionally returns {@code false} from {@link #supports} so it is never
 * accidentally selected by the registry.
 */
public final class TemplateConnector implements ContextProviderPort {

  @Override
  public String connectorId() {
    return "template";
  }

  /** Always returns {@code false} — replace with your type check. */
  @Override
  public boolean supports(ConnectionProfile profile) {
    return false;
  }

  @Override
  public void testConnection(ConnectionProfile profile) throws ConnectorException {
    throw new ConnectorException("TemplateConnector.testConnection() not implemented");
  }

  @Override
  public List<DatabaseRef> listDatabases(ConnectionProfile profile) throws ConnectorException {
    throw new ConnectorException("TemplateConnector.listDatabases() not implemented");
  }

  @Override
  public SchemaInspector schemaInspector() {
    return new TemplateSchemaInspector();
  }

  @Override
  public RecordReader createReader() {
    return new TemplateRecordReader();
  }

  @Override
  public RecordWriter createWriter() {
    return new TemplateRecordWriter();
  }
}
