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
package io.recordrelay.core.port.in;

import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.SchemaMatchReport;
import io.recordrelay.core.domain.TableRef;
import java.util.List;

/**
 * Driving port: schema discovery and compatibility analysis use cases.
 *
 * <p>Implementations orchestrate the {@link io.recordrelay.core.port.out.ContextProviderPort} and
 * {@link io.recordrelay.core.port.out.SchemaInspector} driven ports to fulfil UI and CLI requests.
 */
public interface SchemaDiscoveryUseCase {

  /**
   * Returns all databases visible to the credentials in {@code profile}.
   *
   * @param profile connection parameters
   * @return list of discoverable databases
   */
  List<DatabaseRef> discoverDatabases(ConnectionProfile profile);

  /**
   * Returns all tables (collections, etc.) within the given database.
   *
   * @param profile connection parameters with schema read access
   * @param database the database to inspect
   * @return list of table references
   */
  List<TableRef> discoverTables(ConnectionProfile profile, DatabaseRef database);

  /**
   * Returns column metadata for the given table.
   *
   * @param profile connection parameters with schema read access
   * @param table the table to inspect
   * @return ordered list of column metadata
   */
  List<ColumnMeta> inspectColumns(ConnectionProfile profile, TableRef table);

  /**
   * Analyses structural compatibility between two environment tables.
   *
   * @param envProfile connection to the reference environment
   * @param envTable the reference table
   * @param replicaProfile connection to the replica environment
   * @param replicaTable the replica table
   * @return a report with match percentage and per-column compatibility details
   */
  SchemaMatchReport analyzeCompatibility(
      ConnectionProfile envProfile,
      TableRef envTable,
      ConnectionProfile replicaProfile,
      TableRef replicaTable);
}
