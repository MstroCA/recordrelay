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

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** ViewModel for the Transfer screen — holds job configuration and live progress state. */
public final class TransferViewModel extends BaseViewModel {

  private final StringProperty sourceConn = new SimpleStringProperty("");
  private final StringProperty targetConn = new SimpleStringProperty("");
  private final StringProperty sourceTable = new SimpleStringProperty("");
  private final StringProperty targetTable = new SimpleStringProperty("");
  private final StringProperty mappingFile = new SimpleStringProperty("");
  private final StringProperty mode = new SimpleStringProperty("SYNC");
  private final StringProperty isolation = new SimpleStringProperty("READ_COMMITTED");
  private final LongProperty chunkSize = new SimpleLongProperty(1000);

  private final DoubleProperty progress = new SimpleDoubleProperty(0);
  private final LongProperty transferred = new SimpleLongProperty(0);
  private final LongProperty total = new SimpleLongProperty(-1);
  private final LongProperty throughput = new SimpleLongProperty(0);
  private final StringProperty statusText = new SimpleStringProperty("Ready");

  /** Returns the source connection name property. */
  public StringProperty sourceConnProperty() {
    return sourceConn;
  }

  /** Returns the target connection name property. */
  public StringProperty targetConnProperty() {
    return targetConn;
  }

  /** Returns the source table path property (format: "db.table" or "table"). */
  public StringProperty sourceTableProperty() {
    return sourceTable;
  }

  /** Returns the target table path property (format: "db.table" or "table"). */
  public StringProperty targetTableProperty() {
    return targetTable;
  }

  /** Returns the mapping file path property. */
  public StringProperty mappingFileProperty() {
    return mappingFile;
  }

  /** Returns the transfer mode property (SYNC or BATCH). */
  public StringProperty modeProperty() {
    return mode;
  }

  /** Returns the transaction isolation level property. */
  public StringProperty isolationProperty() {
    return isolation;
  }

  /** Returns the chunk size property. */
  public LongProperty chunkSizeProperty() {
    return chunkSize;
  }

  /** Returns the transfer progress property (0.0–1.0, or negative when unknown). */
  public DoubleProperty progressProperty() {
    return progress;
  }

  /** Returns the number of transferred records. */
  public LongProperty transferredProperty() {
    return transferred;
  }

  /** Returns the total record count (negative when unknown). */
  public LongProperty totalProperty() {
    return total;
  }

  /** Returns the current throughput in records/second. */
  public LongProperty throughputProperty() {
    return throughput;
  }

  /** Returns the human-readable status text property. */
  public StringProperty statusTextProperty() {
    return statusText;
  }

  /** Updates progress metrics from a listener callback (safe to call from any thread). */
  public void updateProgress(long done, long size, long recs) {
    javafx.application.Platform.runLater(
        () -> {
          transferred.set(done);
          total.set(size);
          throughput.set(recs);
          progress.set(size > 0 ? (double) done / size : -1);
          statusText.set(String.format("Transferred %,d records", done));
        });
  }

  /** Marks the transfer as complete. */
  public void markComplete(long finalCount) {
    javafx.application.Platform.runLater(
        () -> {
          transferred.set(finalCount);
          progress.set(1.0);
          statusText.set(String.format("Complete: %,d records", finalCount));
          setBusy(false);
        });
  }

  /** Marks the transfer as failed with an error message. */
  public void markFailed(String message) {
    javafx.application.Platform.runLater(
        () -> {
          statusText.set("Failed: " + message);
          progress.set(0);
          setBusy(false);
          setError(message);
        });
  }

  /** Resets all progress state for a new transfer. */
  public void resetProgress() {
    transferred.set(0);
    total.set(-1);
    throughput.set(0);
    progress.set(0);
    statusText.set("Starting…");
    clearError();
    setBusy(true);
  }
}
