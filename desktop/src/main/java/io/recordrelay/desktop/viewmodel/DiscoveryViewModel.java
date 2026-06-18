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
package io.recordrelay.desktop.viewmodel;

import io.recordrelay.cli.config.ConfigStore;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.TableRef;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** ViewModel for the Discovery wizard — tracks selected connections, databases, and tables. */
public final class DiscoveryViewModel extends BaseViewModel {

  private final ConfigStore store;

  private final StringProperty sourceConn = new SimpleStringProperty("");
  private final StringProperty targetConn = new SimpleStringProperty("");
  private final ObjectProperty<DatabaseRef> sourceDb = new SimpleObjectProperty<>();
  private final ObjectProperty<DatabaseRef> targetDb = new SimpleObjectProperty<>();
  private final ObjectProperty<TableRef> sourceTable = new SimpleObjectProperty<>();
  private final ObjectProperty<TableRef> targetTable = new SimpleObjectProperty<>();

  private final ObservableList<String> connNames = FXCollections.observableArrayList();
  private final ObservableList<DatabaseRef> sourceDatabases = FXCollections.observableArrayList();
  private final ObservableList<DatabaseRef> targetDatabases = FXCollections.observableArrayList();
  private final ObservableList<TableRef> sourceTables = FXCollections.observableArrayList();
  private final ObservableList<TableRef> targetTables = FXCollections.observableArrayList();

  public DiscoveryViewModel(ConfigStore store) {
    this.store = store;
  }

  /** Loads available connection names from the config store. */
  public void loadConnections() {
    clearError();
    try {
      connNames.setAll(store.load().getConnections().keySet());
    } catch (Exception e) {
      setError(e.getMessage());
    }
  }

  /** Returns the selected source connection name property. */
  public StringProperty sourceConnProperty() {
    return sourceConn;
  }

  /** Returns the selected target connection name property. */
  public StringProperty targetConnProperty() {
    return targetConn;
  }

  /** Returns the selected source database property. */
  public ObjectProperty<DatabaseRef> sourceDbProperty() {
    return sourceDb;
  }

  /** Returns the selected target database property. */
  public ObjectProperty<DatabaseRef> targetDbProperty() {
    return targetDb;
  }

  /** Returns the selected source table property. */
  public ObjectProperty<TableRef> sourceTableProperty() {
    return sourceTable;
  }

  /** Returns the selected target table property. */
  public ObjectProperty<TableRef> targetTableProperty() {
    return targetTable;
  }

  /** Returns the observable list of available connection names. */
  public ObservableList<String> connNamesProperty() {
    return connNames;
  }

  /** Returns the observable list of source databases discovered so far. */
  public ObservableList<DatabaseRef> sourceDatabasesProperty() {
    return sourceDatabases;
  }

  /** Returns the observable list of target databases discovered so far. */
  public ObservableList<DatabaseRef> targetDatabasesProperty() {
    return targetDatabases;
  }

  /** Returns the observable list of tables in the selected source database. */
  public ObservableList<TableRef> sourceTablesProperty() {
    return sourceTables;
  }

  /** Returns the observable list of tables in the selected target database. */
  public ObservableList<TableRef> targetTablesProperty() {
    return targetTables;
  }
}
