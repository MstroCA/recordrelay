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

import io.recordrelay.cli.engine.ConnProfileResolver;
import io.recordrelay.cli.engine.MaskingCoverageEngine;
import io.recordrelay.cli.engine.MaskingCoverageEngine.CoverageEntry;
import io.recordrelay.cli.engine.MaskingCoverageEngine.CoverageStatus;
import io.recordrelay.core.clone.domain.MaskerType;
import io.recordrelay.core.clone.domain.MaskingConfig;
import io.recordrelay.core.clone.domain.MaskingRule;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;

/** Controller for the Masking Coverage screen. */
public final class MaskingCoverageController implements Refreshable {

  @FXML private ComboBox<String> cmbSource;
  @FXML private CheckBox chkExposedOnly;
  @FXML private Button btnScan;
  @FXML private Label lblSummary;
  @FXML private Label lblCoverage;

  @FXML private TableView<CoverageEntry> tblCoverage;
  @FXML private TableColumn<CoverageEntry, String> colTable;
  @FXML private TableColumn<CoverageEntry, String> colColumn;
  @FXML private TableColumn<CoverageEntry, String> colType;
  @FXML private TableColumn<CoverageEntry, String> colStatus;
  @FXML private TableColumn<CoverageEntry, String> colCategory;

  private ConnProfileResolver resolver;

  private static final MaskingConfig BUILTIN_MASKING =
      new MaskingConfig(
          List.of(
              new MaskingRule("email", MaskerType.EMAIL),
              new MaskingRule("phone", MaskerType.PHONE),
              new MaskingRule("phone_number", MaskerType.PHONE),
              new MaskingRule("mobile", MaskerType.PHONE),
              new MaskingRule("address", MaskerType.ADDRESS),
              new MaskingRule("street_address", MaskerType.ADDRESS),
              new MaskingRule("national_id", MaskerType.NATIONAL_ID),
              new MaskingRule("ssn", MaskerType.NATIONAL_ID),
              new MaskingRule("iban", MaskerType.IBAN)));

  @FXML
  void initialize() {
    try {
      var configStore = new io.recordrelay.cli.config.ConfigStore();
      resolver = new ConnProfileResolver(configStore);
      setupTable();
      loadConnections(configStore);
    } catch (Exception e) {
      lblSummary.setText("Init error: " + e.getMessage());
    }
  }

  @Override
  public void refresh() {
    try {
      var configStore = new io.recordrelay.cli.config.ConfigStore();
      loadConnections(configStore);
    } catch (Exception ignored) {
    }
  }

  private void setupTable() {
    colTable.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().tableName()));
    colColumn.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().columnName()));
    colType.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().columnType()));
    colStatus.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().status().name()));
    colCategory.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().piiCategory()));

    // Color-code status column
    colStatus.setCellFactory(
        col ->
            new TextFieldTableCell<>() {
              @Override
              public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                  setStyle("");
                  return;
                }
                setStyle(
                    switch (item) {
                      case "EXPOSED" -> "-fx-text-fill: #e74c3c; -fx-font-weight: bold;";
                      case "MASKED" -> "-fx-text-fill: #27ae60; -fx-font-weight: bold;";
                      case "LOW_RISK" -> "-fx-text-fill: #e67e22;";
                      default -> "";
                    });
              }
            });
  }

  private void loadConnections(io.recordrelay.cli.config.ConfigStore configStore) {
    try {
      var names = FXCollections.observableArrayList(configStore.load().getConnections().keySet());
      cmbSource.setItems(names);
    } catch (Exception ignored) {
    }
  }

  @FXML
  void onScan() {
    var connName = cmbSource.getValue();
    if (connName == null || connName.isBlank()) {
      lblSummary.setText("Select a source connection.");
      return;
    }
    btnScan.setDisable(true);
    lblSummary.setText("Scanning…");
    tblCoverage.setItems(FXCollections.emptyObservableList());

    new Thread(
            () -> {
              try {
                var profile = resolver.resolve(connName);
                var report = MaskingCoverageEngine.analyse(profile, BUILTIN_MASKING, connName);

                var filtered =
                    report.entries().stream()
                        .filter(
                            e ->
                                chkExposedOnly.isSelected()
                                    ? e.status() == CoverageStatus.EXPOSED
                                    : e.status() != CoverageStatus.CLEAN)
                        .toList();

                Platform.runLater(
                    () -> {
                      tblCoverage.setItems(FXCollections.observableArrayList(filtered));
                      lblSummary.setText(
                          String.format(
                              "%d total columns  |  %d EXPOSED  |  %d MASKED  |  %d LOW-RISK",
                              report.totalColumns(),
                              report.exposedCount(),
                              report.maskedCount(),
                              report.lowRiskCount()));
                      lblCoverage.setText(
                          report.exposedCount() > 0
                              ? String.format(
                                  "Coverage: %.1f%% — add --mask to clone commands to cover exposed columns",
                                  report.coveragePct())
                              : "All detected PII columns are covered.");
                      btnScan.setDisable(false);
                    });
              } catch (Exception e) {
                Platform.runLater(
                    () -> {
                      lblSummary.setText("Error: " + e.getMessage());
                      btnScan.setDisable(false);
                    });
              }
            },
            "rr-mask-scan")
        .start();
  }
}
