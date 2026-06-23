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
import io.recordrelay.core.domain.MigrationDriftItem;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** ViewModel for the Migration Drift screen. */
public final class MigrationDriftViewModel extends BaseViewModel {

  private final ConfigStore store;

  private final ObservableList<String> connNames = FXCollections.observableArrayList();
  private final ObservableList<DatabaseRef> sourceDatabases = FXCollections.observableArrayList();
  private final ObservableList<DatabaseRef> targetDatabases = FXCollections.observableArrayList();
  private final ObservableList<MigrationDriftItem> driftItems = FXCollections.observableArrayList();

  public MigrationDriftViewModel(ConfigStore store) {
    this.store = store;
  }

  public void loadConnections() {
    clearError();
    try {
      connNames.setAll(store.load().getConnections().keySet());
    } catch (Exception e) {
      setError(e.getMessage());
    }
  }

  public ObservableList<String> connNamesProperty() {
    return connNames;
  }

  public ObservableList<DatabaseRef> sourceDatabasesProperty() {
    return sourceDatabases;
  }

  public ObservableList<DatabaseRef> targetDatabasesProperty() {
    return targetDatabases;
  }

  public ObservableList<MigrationDriftItem> driftItemsProperty() {
    return driftItems;
  }
}
