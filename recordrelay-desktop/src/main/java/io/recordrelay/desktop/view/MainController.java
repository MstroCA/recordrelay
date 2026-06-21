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
package io.recordrelay.desktop.view;

import io.recordrelay.desktop.theme.ThemeManager;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/** Root controller for the main window. Manages sidebar navigation and theme switching. */
public final class MainController {

  @FXML private StackPane contentPane;
  @FXML private Label lblStatus;
  @FXML private Button btnTheme;

  private final Map<String, Parent> screenCache = new HashMap<>();
  private final Map<String, Object> controllerCache = new HashMap<>();
  private boolean darkMode = false;

  @FXML
  void initialize() {
    showCloneContext();
  }

  @FXML
  void showCloneContext() {
    navigate("clone-context");
  }

  @FXML
  void showEnvironments() {
    navigate("environments");
  }

  @FXML
  void showConnections() {
    navigate("connections");
  }

  @FXML
  void showDiscovery() {
    navigate("discovery");
  }

  @FXML
  void showMonitor() {
    navigate("monitor");
  }

  @FXML
  void showGraphView() {
    navigate("graph-view");
  }

  @FXML
  void toggleTheme() {
    darkMode = !darkMode;
    ThemeManager.apply(darkMode);
    btnTheme.setText(darkMode ? "☀ Light" : "🌙 Dark");
  }

  /** Updates the status bar label. */
  public void setStatus(String message) {
    lblStatus.setText("● " + message);
  }

  private void navigate(String screen) {
    if (!screenCache.containsKey(screen)) {
      loadFxml(screen);
    }
    contentPane.getChildren().setAll(screenCache.getOrDefault(screen, new Label(screen)));
    var ctrl = controllerCache.get(screen);
    if (ctrl instanceof Refreshable r) {
      r.refresh();
    }
  }

  private void loadFxml(String name) {
    URL url = getClass().getResource("/io/recordrelay/desktop/fxml/" + name + ".fxml");
    if (url == null) {
      screenCache.put(name, new Label("Screen not found: " + name));
      return;
    }
    try {
      var loader = new FXMLLoader(url);
      Parent root = loader.load();
      screenCache.put(name, root);
      controllerCache.put(name, loader.getController());
    } catch (IOException e) {
      screenCache.put(name, new Label("Error loading " + name + ": " + e.getMessage()));
    }
  }
}
