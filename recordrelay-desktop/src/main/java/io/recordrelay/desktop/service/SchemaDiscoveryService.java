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
package io.recordrelay.desktop.service;

import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.spi.ConnectorRegistry;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;

/**
 * Executes schema-discovery operations on a background thread and posts results to the JavaFX
 * Application Thread via {@link Platform#runLater}.
 */
public final class SchemaDiscoveryService {

  private static final ExecutorService EXEC =
      Executors.newCachedThreadPool(
          r -> {
            var t = new Thread(r, "rr-schema-discovery");
            t.setDaemon(true);
            return t;
          });

  /** Lists all databases (keyspaces/indices) accessible via {@code profile}. */
  public void loadDatabases(
      ConnectionProfile profile, Consumer<List<DatabaseRef>> onSuccess, Consumer<String> onError) {
    EXEC.submit(
        () -> {
          try {
            var connector = ConnectorRegistry.findConnector(profile);
            var dbs = connector.listDatabases(profile);
            Platform.runLater(() -> onSuccess.accept(dbs));
          } catch (Exception e) {
            Platform.runLater(() -> onError.accept(e.getMessage()));
          }
        });
  }

  /** Lists all tables (collections/indices) in {@code db}. */
  public void loadTables(
      ConnectionProfile profile,
      DatabaseRef db,
      Consumer<List<TableRef>> onSuccess,
      Consumer<String> onError) {
    EXEC.submit(
        () -> {
          try {
            var connector = ConnectorRegistry.findConnector(profile);
            var tables = connector.schemaInspector().listTables(profile, db);
            Platform.runLater(() -> onSuccess.accept(tables));
          } catch (Exception e) {
            Platform.runLater(() -> onError.accept(e.getMessage()));
          }
        });
  }

  /** Inspects the columns (or sampled fields for NoSQL) of {@code table}. */
  public void loadColumns(
      ConnectionProfile profile,
      TableRef table,
      Consumer<List<ColumnMeta>> onSuccess,
      Consumer<String> onError) {
    EXEC.submit(
        () -> {
          try {
            var connector = ConnectorRegistry.findConnector(profile);
            var cols = connector.schemaInspector().inspectColumns(profile, table);
            Platform.runLater(() -> onSuccess.accept(cols));
          } catch (Exception e) {
            Platform.runLater(() -> onError.accept(e.getMessage()));
          }
        });
  }

  /**
   * Checks whether {@code tableName} exists among the tables in {@code db}, posting the boolean
   * result to the FX thread via {@code result}.
   */
  public void checkTableExists(
      ConnectionProfile profile,
      DatabaseRef db,
      String tableName,
      Consumer<Boolean> result,
      Consumer<String> onError) {
    EXEC.submit(
        () -> {
          try {
            var connector = ConnectorRegistry.findConnector(profile);
            var tables = connector.schemaInspector().listTables(profile, db);
            boolean exists =
                tables.stream().anyMatch(t -> t.tableName().equalsIgnoreCase(tableName));
            Platform.runLater(() -> result.accept(exists));
          } catch (Exception e) {
            Platform.runLater(() -> onError.accept(e.getMessage()));
          }
        });
  }
}
