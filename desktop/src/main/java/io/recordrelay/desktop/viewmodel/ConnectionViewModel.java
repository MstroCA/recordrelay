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
import io.recordrelay.cli.config.ConnectionEntry;
import io.recordrelay.cli.engine.ConnProfileResolver;
import io.recordrelay.core.spi.ConnectorRegistry;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** ViewModel for the Connections screen. */
public final class ConnectionViewModel extends BaseViewModel {

  private final ConfigStore store;
  private final ObservableList<Map.Entry<String, ConnectionEntry>> connections =
      FXCollections.observableArrayList();

  public ConnectionViewModel(ConfigStore store) {
    this.store = store;
  }

  /** Returns the observable list of name→entry pairs for all stored connections. */
  public ObservableList<Map.Entry<String, ConnectionEntry>> connectionsProperty() {
    return connections;
  }

  /** Reloads the connection list from the config store. */
  public void load() {
    clearError();
    try {
      var config = store.load();
      connections.setAll(config.getConnections().entrySet());
    } catch (Exception e) {
      setError(e.getMessage());
    }
  }

  /** Adds a connection entry (password must already be encrypted) and reloads. */
  public void add(String name, ConnectionEntry entry) {
    clearError();
    try {
      store.addConnection(name, entry);
      load();
    } catch (Exception e) {
      setError(e.getMessage());
    }
  }

  /** Removes a connection and reloads. */
  public void remove(String name) {
    clearError();
    try {
      store.removeConnection(name);
      load();
    } catch (Exception e) {
      setError(e.getMessage());
    }
  }

  /**
   * Tests connectivity for the named connection.
   *
   * @return empty string on success; error message on failure
   */
  public String testConnection(String name) {
    try {
      var resolver = new ConnProfileResolver(store);
      var profile = resolver.resolve(name);
      ConnectorRegistry.findConnector(profile).testConnection(profile);
      return "";
    } catch (Exception e) {
      return e.getMessage();
    }
  }
}
