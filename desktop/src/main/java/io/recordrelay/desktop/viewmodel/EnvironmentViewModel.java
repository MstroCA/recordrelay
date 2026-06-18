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
import io.recordrelay.cli.config.EnvironmentEntry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** ViewModel for the Environments screen. */
public final class EnvironmentViewModel extends BaseViewModel {

  private final ConfigStore store;
  private final ObservableList<EnvironmentEntry> environments = FXCollections.observableArrayList();

  public EnvironmentViewModel(ConfigStore store) {
    this.store = store;
  }

  /** Returns the observable list of loaded environment entries. */
  public ObservableList<EnvironmentEntry> environmentsProperty() {
    return environments;
  }

  /** Reloads the environment list from the config store. */
  public void load() {
    clearError();
    try {
      var config = store.load();
      environments.setAll(config.getEnvironments().values());
    } catch (Exception e) {
      setError(e.getMessage());
    }
  }

  /** Adds an environment and refreshes the list. */
  public void add(String name, String description) {
    clearError();
    try {
      store.addEnvironment(name, new EnvironmentEntry(name, description));
      load();
    } catch (Exception e) {
      setError(e.getMessage());
    }
  }

  /** Removes the environment with the given name and refreshes the list. */
  public void remove(String name) {
    clearError();
    try {
      store.removeEnvironment(name);
      load();
    } catch (Exception e) {
      setError(e.getMessage());
    }
  }
}
