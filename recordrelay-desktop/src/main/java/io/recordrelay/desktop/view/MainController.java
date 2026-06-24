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

import io.recordrelay.core.i18n.Messages;
import io.recordrelay.desktop.theme.ThemeManager;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * Root controller for the main window. Manages sidebar navigation, language switching and theme.
 */
public final class MainController {

  @FXML private StackPane contentPane;
  @FXML private Label lblStatus;
  @FXML private Label lblVersion;
  @FXML private Button btnTheme;
  @FXML private ComboBox<String> cboLanguage;

  private static final Map<String, String> LANG_CODES = new LinkedHashMap<>();

  static {
    LANG_CODES.put("English", "en");
    LANG_CODES.put("Türkçe", "tr");
    LANG_CODES.put("Français", "fr");
    LANG_CODES.put("Español", "es");
    LANG_CODES.put("Deutsch", "de");
    LANG_CODES.put("Italiano", "it");
    LANG_CODES.put("Português", "pt");
    LANG_CODES.put("Русский", "ru");
    LANG_CODES.put("中文", "zh");
    LANG_CODES.put("日本語", "ja");
    LANG_CODES.put("العربية", "ar");
    LANG_CODES.put("한국어", "ko");
  }

  private final Map<String, Parent> screenCache = new HashMap<>();
  private final Map<String, Object> controllerCache = new HashMap<>();
  private boolean darkMode = false;
  private String currentScreen = "clone-context";
  private String currentLanguage = "English";

  @FXML
  void initialize() {
    cboLanguage.getItems().addAll(LANG_CODES.keySet());
    cboLanguage.setValue("English");
    lblVersion.setText(readVersion());
    showCloneContext();
  }

  @FXML
  void onLanguageChanged() {
    var selected = cboLanguage.getValue();
    if (selected == null || selected.equals(currentLanguage)) {
      return;
    }
    currentLanguage = selected;
    Messages.setLocale(Locale.of(LANG_CODES.getOrDefault(selected, "en")));
    screenCache.clear();
    controllerCache.clear();
    btnTheme.setText(darkMode ? Messages.get("theme.light") : Messages.get("theme.dark"));
    navigate(currentScreen);
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
  void showMigrationDrift() {
    navigate("migration-drift");
  }

  @FXML
  void showRowCountDiff() {
    navigate("row-count-diff");
  }

  @FXML
  void showQuery() {
    navigate("query");
  }

  @FXML
  void showConnectionHealth() {
    navigate("connection-health");
  }

  @FXML
  void toggleTheme() {
    darkMode = !darkMode;
    ThemeManager.apply(darkMode);
    btnTheme.setText(darkMode ? Messages.get("theme.light") : Messages.get("theme.dark"));
  }

  /** Updates the status bar label. */
  public void setStatus(String message) {
    lblStatus.setText(message);
  }

  private void navigate(String screen) {
    currentScreen = screen;
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
      var loader = new FXMLLoader(url, Messages.getBundle());
      Parent root = loader.load();
      screenCache.put(name, root);
      controllerCache.put(name, loader.getController());
    } catch (IOException e) {
      screenCache.put(name, new Label("Error loading " + name + ": " + e.getMessage()));
    }
  }

  private String readVersion() {
    try (var is = getClass().getResourceAsStream("/io/recordrelay/desktop/app.properties")) {
      if (is == null) {
        return "";
      }
      var props = new java.util.Properties();
      props.load(is);
      return props.getProperty("app.version", "");
    } catch (Exception e) {
      return "";
    }
  }
}
