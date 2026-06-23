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
package io.recordrelay.desktop;

import io.recordrelay.core.i18n.Messages;
import io.recordrelay.desktop.theme.ThemeManager;
import java.net.URL;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** JavaFX application entry point for RecordRelay Desktop. */
public final class RecordRelayApp extends Application {

  private static final int WINDOW_WIDTH = 1200;
  private static final int WINDOW_HEIGHT = 780;
  private static final int MIN_WIDTH = 900;
  private static final int MIN_HEIGHT = 600;

  @Override
  public void start(Stage stage) throws Exception {
    ThemeManager.applyLight();

    URL fxml = getClass().getResource("/io/recordrelay/desktop/fxml/main.fxml");
    var loader = new FXMLLoader(fxml, Messages.getBundle());
    Parent root = loader.load();
    var scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
    addStylesheet(scene);

    stage.setTitle("RecordRelay 0.1.0-SNAPSHOT");
    stage.setScene(scene);
    stage.setMinWidth(MIN_WIDTH);
    stage.setMinHeight(MIN_HEIGHT);
    stage.show();
  }

  private void addStylesheet(Scene scene) {
    URL css = getClass().getResource("/io/recordrelay/desktop/css/recordrelay.css");
    if (css != null) {
      scene.getStylesheets().add(css.toExternalForm());
    }
  }

  /** Application entry point. */
  public static void main(String[] args) {
    launch(args);
  }
}
