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

import io.recordrelay.cli.config.ConfigStore;
import io.recordrelay.cli.engine.ConnectionHealthEngine;
import io.recordrelay.core.domain.ConnectionHealthEntry;
import io.recordrelay.core.domain.HealthStatus.Status;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

/** Controller for the Connection Health screen. */
public final class HealthCheckController implements Refreshable {

  @FXML private Button btnCheck;
  @FXML private ProgressIndicator progressIndicator;
  @FXML private Label lblStatus;
  @FXML private Label lblSummary;

  @FXML private TableView<ConnectionHealthEntry> tblHealth;
  @FXML private TableColumn<ConnectionHealthEntry, String> colStatus;
  @FXML private TableColumn<ConnectionHealthEntry, String> colName;
  @FXML private TableColumn<ConnectionHealthEntry, String> colType;
  @FXML private TableColumn<ConnectionHealthEntry, String> colHost;
  @FXML private TableColumn<ConnectionHealthEntry, String> colLatency;
  @FXML private TableColumn<ConnectionHealthEntry, String> colDetail;

  private ConnectionHealthEngine engine;
  private boolean firstLoad = true;

  @FXML
  void initialize() {
    try {
      engine = new ConnectionHealthEngine(new ConfigStore());
    } catch (Exception e) {
      lblStatus.setText("Config hatası: " + e.getMessage());
      btnCheck.setDisable(true);
      return;
    }
    bindTable();
  }

  @Override
  public void refresh() {
    if (firstLoad) {
      firstLoad = false;
      runCheck();
    }
  }

  @FXML
  void onCheckAll() {
    runCheck();
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private void runCheck() {
    btnCheck.setDisable(true);
    progressIndicator.setVisible(true);
    progressIndicator.setManaged(true);
    lblStatus.setText("Test ediliyor…");
    lblSummary.setText("");
    tblHealth.getItems().clear();

    new Thread(
            () -> {
              var results = engine.checkAll();
              Platform.runLater(() -> showResults(results));
            },
            "rr-health-check")
        .start();
  }

  private void showResults(java.util.List<ConnectionHealthEntry> results) {
    tblHealth.getItems().setAll(results);
    btnCheck.setDisable(false);
    progressIndicator.setVisible(false);
    progressIndicator.setManaged(false);

    long ok = results.stream().filter(e -> e.status() == Status.OK).count();
    long down = results.stream().filter(e -> e.status() == Status.DOWN).count();
    long degraded = results.stream().filter(e -> e.status() == Status.DEGRADED).count();

    lblSummary.setText(ok + " OK  |  " + degraded + " DEGRADED  |  " + down + " DOWN");
    lblStatus.setText(
        down == 0 && degraded == 0
            ? "✓ Tüm bağlantılar erişilebilir"
            : "● " + (down + degraded) + " bağlantıda sorun var");
  }

  private void bindTable() {
    colStatus.setCellValueFactory(
        cd -> new SimpleStringProperty(statusLabel(cd.getValue().status())));
    colName.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().connName()));
    colType.setCellValueFactory(
        cd ->
            new SimpleStringProperty(
                cd.getValue().dbType() != null ? cd.getValue().dbType().name() : "—"));
    colHost.setCellValueFactory(
        cd -> new SimpleStringProperty(cd.getValue().host() != null ? cd.getValue().host() : "—"));
    colLatency.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().latencyDisplay()));
    colDetail.setCellValueFactory(
        cd ->
            new SimpleStringProperty(cd.getValue().detail() != null ? cd.getValue().detail() : ""));

    tblHealth.setRowFactory(
        tv -> {
          TableRow<ConnectionHealthEntry> row = new TableRow<>();
          row.itemProperty()
              .addListener(
                  (obs, old, item) -> {
                    row.getStyleClass()
                        .removeAll("health-row-ok", "health-row-degraded", "health-row-down");
                    if (item != null) {
                      row.getStyleClass().add(rowStyle(item.status()));
                    }
                  });
          return row;
        });
  }

  private static String statusLabel(Status status) {
    return switch (status) {
      case OK -> "✓ OK";
      case DEGRADED -> "⚠ DEGRADED";
      case DOWN -> "✗ DOWN";
    };
  }

  private static String rowStyle(Status status) {
    return switch (status) {
      case OK -> "health-row-ok";
      case DEGRADED -> "health-row-degraded";
      case DOWN -> "health-row-down";
    };
  }
}
