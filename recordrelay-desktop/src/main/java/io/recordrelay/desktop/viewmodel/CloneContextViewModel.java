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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** ViewModel for the Clone Context screen. */
public final class CloneContextViewModel extends BaseViewModel {

  // Entity selection
  private final StringProperty entityName = new SimpleStringProperty("customer");
  private final StringProperty entityId = new SimpleStringProperty("");

  // Connections
  private final StringProperty sourceConn = new SimpleStringProperty("");
  private final StringProperty targetConn = new SimpleStringProperty("");
  private final BooleanProperty exportMode = new SimpleBooleanProperty(false);

  // Options
  private final IntegerProperty depth = new SimpleIntegerProperty(3);
  private final BooleanProperty maskPii = new SimpleBooleanProperty(false);

  // Bug context (optional)
  private final StringProperty bugId = new SimpleStringProperty("");
  private final StringProperty bugTitle = new SimpleStringProperty("");
  private final StringProperty bugService = new SimpleStringProperty("");
  private final StringProperty bugEnv = new SimpleStringProperty("");

  // Progress / output
  private final DoubleProperty progress = new SimpleDoubleProperty(0.0);
  private final StringProperty statusText = new SimpleStringProperty("Ready");
  private final StringProperty logText = new SimpleStringProperty("");
  private final StringProperty exportedPath = new SimpleStringProperty("");

  /** Returns the selected business entity name property. */
  public StringProperty entityNameProperty() {
    return entityName;
  }

  /** Returns the entity ID input property. */
  public StringProperty entityIdProperty() {
    return entityId;
  }

  /** Returns the source connection name property. */
  public StringProperty sourceConnProperty() {
    return sourceConn;
  }

  /** Returns the target connection name property. */
  public StringProperty targetConnProperty() {
    return targetConn;
  }

  /** Returns the export-mode toggle property. */
  public BooleanProperty exportModeProperty() {
    return exportMode;
  }

  /** Returns the traversal depth property. */
  public IntegerProperty depthProperty() {
    return depth;
  }

  /** Returns the mask-PII toggle property. */
  public BooleanProperty maskPiiProperty() {
    return maskPii;
  }

  /** Returns the bug-report ID input property. */
  public StringProperty bugIdProperty() {
    return bugId;
  }

  /** Returns the bug-report title input property. */
  public StringProperty bugTitleProperty() {
    return bugTitle;
  }

  /** Returns the bug-report service input property. */
  public StringProperty bugServiceProperty() {
    return bugService;
  }

  /** Returns the bug-report environment input property. */
  public StringProperty bugEnvProperty() {
    return bugEnv;
  }

  /** Returns the clone progress property (0.0–1.0). */
  public DoubleProperty progressProperty() {
    return progress;
  }

  /** Returns the status-text property shown in the UI footer. */
  public StringProperty statusTextProperty() {
    return statusText;
  }

  /** Returns the log-text property bound to the log text area. */
  public StringProperty logTextProperty() {
    return logText;
  }

  /** Returns the exported-package-path property (set after a successful export). */
  public StringProperty exportedPathProperty() {
    return exportedPath;
  }

  /** Returns true when the bug-context panel has a non-blank title. */
  public boolean hasBugContext() {
    return !bugTitle.get().isBlank();
  }

  /** Resets all progress state and marks the engine as busy. */
  public void resetProgress() {
    progress.set(0.0);
    statusText.set("Starting…");
    logText.set("");
    exportedPath.set("");
    clearError();
    setBusy(true);
  }

  /** Appends a line to the log text area (thread-safe via Platform.runLater). */
  public void appendLog(String line) {
    javafx.application.Platform.runLater(
        () -> {
          var current = logText.get();
          logText.set(current.isEmpty() ? line : current + "\n" + line);
        });
  }

  /** Updates the status text (thread-safe). */
  public void updateStatus(String status) {
    javafx.application.Platform.runLater(() -> statusText.set(status));
  }

  /** Updates the progress bar value (thread-safe). */
  public void updateProgress(double value) {
    javafx.application.Platform.runLater(() -> progress.set(value));
  }

  /** Marks the operation as complete and clears the busy flag. */
  public void markComplete(String message) {
    javafx.application.Platform.runLater(
        () -> {
          progress.set(1.0);
          statusText.set(message);
          setBusy(false);
        });
  }

  /** Marks the operation as failed and sets the error message. */
  public void markFailed(String errorMsg) {
    javafx.application.Platform.runLater(
        () -> {
          statusText.set("Failed");
          setError(errorMsg);
          setBusy(false);
        });
  }
}
