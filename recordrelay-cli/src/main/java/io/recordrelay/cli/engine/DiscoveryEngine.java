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
package io.recordrelay.cli.engine;

import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.SchemaMatchReport;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.port.in.SchemaDiscoveryUseCase;
import io.recordrelay.core.spi.ConnectorRegistry;
import java.util.List;
import java.util.function.Function;

/**
 * CLI implementation of {@link SchemaDiscoveryUseCase}.
 *
 * <p>Delegates to the connector's {@link io.recordrelay.core.port.out.SchemaInspector} discovered
 * via {@link ConnectorRegistry}.
 */
public final class DiscoveryEngine implements SchemaDiscoveryUseCase {

  private final Function<ConnectionProfile, io.recordrelay.core.port.out.ContextProviderPort>
      connectorLookup;

  public DiscoveryEngine() {
    this(ConnectorRegistry::findConnector);
  }

  /** Package-private for unit testing. */
  DiscoveryEngine(
      Function<ConnectionProfile, io.recordrelay.core.port.out.ContextProviderPort>
          connectorLookup) {
    this.connectorLookup = connectorLookup;
  }

  @Override
  public List<DatabaseRef> discoverDatabases(ConnectionProfile profile) {
    try {
      return connectorLookup.apply(profile).listDatabases(profile);
    } catch (ConnectorException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public List<TableRef> discoverTables(ConnectionProfile profile, DatabaseRef database) {
    try {
      return connectorLookup.apply(profile).schemaInspector().listTables(profile, database);
    } catch (ConnectorException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public List<ColumnMeta> inspectColumns(ConnectionProfile profile, TableRef table) {
    try {
      return connectorLookup.apply(profile).schemaInspector().inspectColumns(profile, table);
    } catch (ConnectorException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public SchemaMatchReport analyzeCompatibility(
      ConnectionProfile sourceProfile,
      TableRef sourceTable,
      ConnectionProfile targetProfile,
      TableRef targetTable) {
    try {
      var srcInspector = connectorLookup.apply(sourceProfile).schemaInspector();
      var tgtInspector = connectorLookup.apply(targetProfile).schemaInspector();
      var srcCols = srcInspector.inspectColumns(sourceProfile, sourceTable);
      var tgtCols = tgtInspector.inspectColumns(targetProfile, targetTable);
      return srcInspector.analyzeCompatibility(srcCols, tgtCols);
    } catch (ConnectorException e) {
      throw new RuntimeException(e);
    }
  }
}
