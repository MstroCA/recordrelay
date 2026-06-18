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

import java.nio.file.Path;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** ViewModel for the Packages (reproduction packages) screen. */
public final class PackagesViewModel extends BaseViewModel {

  /** Row in the packages table. */
  public record PackageEntry(
      String fileName, String entityLabel, String capturedAt, String bugTitle, Path path) {}

  private final ObservableList<PackageEntry> packages = FXCollections.observableArrayList();
  private final ObjectProperty<PackageEntry> selectedPackage = new SimpleObjectProperty<>();

  private final StringProperty targetConn = new SimpleStringProperty("");
  private final StringProperty detailText =
      new SimpleStringProperty("Select a package to inspect.");
  private final DoubleProperty progress = new SimpleDoubleProperty(0.0);
  private final StringProperty statusText = new SimpleStringProperty("Ready");
  private final StringProperty logText = new SimpleStringProperty("");

  /** Returns the observable list of loaded packages. */
  public ObservableList<PackageEntry> packagesProperty() {
    return packages;
  }

  /** Returns the currently selected package property. */
  public ObjectProperty<PackageEntry> selectedPackageProperty() {
    return selectedPackage;
  }

  /** Returns the target connection name property. */
  public StringProperty targetConnProperty() {
    return targetConn;
  }

  /** Returns the package detail text property. */
  public StringProperty detailTextProperty() {
    return detailText;
  }

  /** Returns the replay progress property (0.0–1.0). */
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

  /** Resets all progress state and marks the engine as busy. */
  public void resetProgress() {
    progress.set(0.0);
    statusText.set("Starting…");
    logText.set("");
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

  /** Marks the replay as complete and clears the busy flag. */
  public void markComplete(String message) {
    javafx.application.Platform.runLater(
        () -> {
          progress.set(1.0);
          statusText.set(message);
          setBusy(false);
        });
  }

  /** Marks the replay as failed and sets the error message. */
  public void markFailed(String errorMsg) {
    javafx.application.Platform.runLater(
        () -> {
          statusText.set("Replay failed");
          setError(errorMsg);
          setBusy(false);
        });
  }
}
