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
import io.recordrelay.cli.engine.ConnProfileResolver;
import io.recordrelay.cli.engine.DiscoveryEngine;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.SchemaMatchReport;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.desktop.viewmodel.DiscoveryViewModel;
import io.recordrelay.desktop.viewmodel.MappingViewModel;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.FileChooser;

/** Controller for the Discovery &amp; Mapping screen. */
public final class DiscoveryController implements Refreshable {

  @FXML private ComboBox<String> cmbSrcConn;
  @FXML private ComboBox<DatabaseRef> cmbSrcDb;
  @FXML private ComboBox<TableRef> cmbSrcTable;
  @FXML private ComboBox<String> cmbTgtConn;
  @FXML private ComboBox<DatabaseRef> cmbTgtDb;
  @FXML private ComboBox<TableRef> cmbTgtTable;
  @FXML private Button btnDiscover;
  @FXML private Label lblDiscoveryStatus;
  @FXML private Label lblError;

  @FXML private TableView<MappingViewModel.ColumnPair> tblMapping;
  @FXML private TableColumn<MappingViewModel.ColumnPair, String> colSrc;
  @FXML private TableColumn<MappingViewModel.ColumnPair, String> colTgt;
  @FXML private TableColumn<MappingViewModel.ColumnPair, String> colXform;
  @FXML private TableColumn<MappingViewModel.ColumnPair, String> colWarn;
  @FXML private Label lblMatchPct;
  @FXML private ComboBox<String> cmbFormat;
  @FXML private TextArea taPreview;
  @FXML private Button btnSave;

  private DiscoveryViewModel discoveryVm;
  private MappingViewModel mappingVm;
  private ConnProfileResolver resolver;
  private DiscoveryEngine engine;

  @FXML
  void initialize() {
    try {
      var store = new ConfigStore();
      discoveryVm = new DiscoveryViewModel(store);
      mappingVm = new MappingViewModel();
      resolver = new ConnProfileResolver(store);
      engine = new DiscoveryEngine();
    } catch (Exception e) {
      lblError.setText("Config init failed: " + e.getMessage());
      lblError.setVisible(true);
      lblError.setManaged(true);
      return;
    }
    bindDiscoveryPanel();
    bindMappingPanel();
    discoveryVm.loadConnections();
  }

  /** Reloads connection names; called each time this screen is shown. */
  @Override
  public void refresh() {
    if (discoveryVm != null) {
      discoveryVm.loadConnections();
    }
  }

  @FXML
  void onSrcConnChanged() {
    fireLoadDatabases(true);
  }

  @FXML
  void onTgtConnChanged() {
    fireLoadDatabases(false);
  }

  @FXML
  void onSrcDbChanged() {
    fireLoadTables(true);
  }

  @FXML
  void onTgtDbChanged() {
    fireLoadTables(false);
  }

  @FXML
  void onDiscover() {
    TableRef src = cmbSrcTable.getValue();
    TableRef tgt = cmbTgtTable.getValue();
    if (src == null || tgt == null) {
      return;
    }
    lblDiscoveryStatus.setText("Analyzing…");
    mappingVm.columnPairsProperty().clear();
    String srcConn = cmbSrcConn.getValue();
    String tgtConn = cmbTgtConn.getValue();
    new Thread(() -> runAnalyze(srcConn, tgtConn, src, tgt), "rr-disc-analyze").start();
  }

  @FXML
  void onSaveMapping() {
    if (mappingVm.columnPairsProperty().isEmpty()) {
      return;
    }
    var fc = new FileChooser();
    fc.setTitle("Save Mapping");
    fc.setInitialFileName("mapping." + mappingVm.selectedFormatProperty().get());
    var file = fc.showSaveDialog(btnSave.getScene().getWindow());
    if (file == null) {
      return;
    }
    try {
      Files.writeString(file.toPath(), mappingVm.formatPreviewProperty().get());
      lblDiscoveryStatus.setText("Saved: " + file.getName());
    } catch (IOException e) {
      showErrorAlert("Save failed: " + e.getMessage());
    }
  }

  private void fireLoadDatabases(boolean source) {
    String conn = source ? cmbSrcConn.getValue() : cmbTgtConn.getValue();
    if (conn == null || conn.isEmpty()) {
      return;
    }
    if (source) {
      discoveryVm.sourceDatabasesProperty().clear();
      discoveryVm.sourceDbProperty().set(null);
      discoveryVm.sourceTablesProperty().clear();
    } else {
      discoveryVm.targetDatabasesProperty().clear();
      discoveryVm.targetDbProperty().set(null);
      discoveryVm.targetTablesProperty().clear();
    }
    lblDiscoveryStatus.setText("Loading databases…");
    new Thread(() -> runLoadDatabases(conn, source), "rr-disc-db").start();
  }

  private void runLoadDatabases(String conn, boolean source) {
    try {
      var profile = resolver.resolve(conn);
      var dbs = engine.discoverDatabases(profile);
      Platform.runLater(
          () -> {
            if (source) {
              discoveryVm.sourceDatabasesProperty().setAll(dbs);
            } else {
              discoveryVm.targetDatabasesProperty().setAll(dbs);
            }
            lblDiscoveryStatus.setText("● Ready");
          });
    } catch (Exception e) {
      Platform.runLater(() -> lblDiscoveryStatus.setText("Error: " + e.getMessage()));
    }
  }

  private void fireLoadTables(boolean source) {
    DatabaseRef db = source ? cmbSrcDb.getValue() : cmbTgtDb.getValue();
    String conn = source ? cmbSrcConn.getValue() : cmbTgtConn.getValue();
    if (db == null || conn == null) {
      return;
    }
    if (source) {
      discoveryVm.sourceTablesProperty().clear();
    } else {
      discoveryVm.targetTablesProperty().clear();
    }
    lblDiscoveryStatus.setText("Loading tables…");
    new Thread(() -> runLoadTables(conn, db, source), "rr-disc-tbl").start();
  }

  private void runLoadTables(String conn, DatabaseRef db, boolean source) {
    try {
      var profile = resolver.resolve(conn);
      var tables = engine.discoverTables(profile, db);
      Platform.runLater(
          () -> {
            if (source) {
              discoveryVm.sourceTablesProperty().setAll(tables);
            } else {
              discoveryVm.targetTablesProperty().setAll(tables);
            }
            lblDiscoveryStatus.setText("● Ready");
          });
    } catch (Exception e) {
      Platform.runLater(() -> lblDiscoveryStatus.setText("Error: " + e.getMessage()));
    }
  }

  private void runAnalyze(String srcConn, String tgtConn, TableRef src, TableRef tgt) {
    try {
      var srcProfile = resolver.resolve(srcConn);
      var tgtProfile = resolver.resolve(tgtConn);
      var report = engine.analyzeCompatibility(srcProfile, src, tgtProfile, tgt);
      buildMapping(report);
    } catch (Exception e) {
      Platform.runLater(() -> lblDiscoveryStatus.setText("Error: " + e.getMessage()));
    }
  }

  private void buildMapping(SchemaMatchReport report) {
    var pairs = new ArrayList<MappingViewModel.ColumnPair>();
    for (var compat : report.columnCompatibilities()) {
      String srcName = compat.sourceColumn() != null ? compat.sourceColumn() : "";
      var pair = new MappingViewModel.ColumnPair(srcName, compat.targetColumn(), null);
      if (compat.warning() != null) {
        pair.warningProperty().set(compat.warning());
      }
      pairs.add(pair);
    }
    double pct = report.matchPercentage();
    Platform.runLater(
        () -> {
          mappingVm.columnPairsProperty().setAll(pairs);
          mappingVm.setMatchPercent(pct);
          lblMatchPct.setText(String.format("Match: %.0f%%", pct));
          updatePreview();
          lblDiscoveryStatus.setText("● Ready");
        });
  }

  private void bindDiscoveryPanel() {
    cmbSrcConn.setItems(discoveryVm.connNamesProperty());
    cmbTgtConn.setItems(discoveryVm.connNamesProperty());
    cmbSrcDb.setItems(discoveryVm.sourceDatabasesProperty());
    cmbTgtDb.setItems(discoveryVm.targetDatabasesProperty());
    cmbSrcTable.setItems(discoveryVm.sourceTablesProperty());
    cmbTgtTable.setItems(discoveryVm.targetTablesProperty());
    applyDbRefCells(cmbSrcDb);
    applyDbRefCells(cmbTgtDb);
    applyTableRefCells(cmbSrcTable);
    applyTableRefCells(cmbTgtTable);
    btnDiscover
        .disableProperty()
        .bind(cmbSrcTable.valueProperty().isNull().or(cmbTgtTable.valueProperty().isNull()));
    lblError.textProperty().bind(discoveryVm.errorProperty());
    lblError.visibleProperty().bind(discoveryVm.errorProperty().isNotEmpty());
    lblError.managedProperty().bind(discoveryVm.errorProperty().isNotEmpty());
  }

  private void bindMappingPanel() {
    colSrc.setCellValueFactory(r -> r.getValue().sourceColumnProperty());
    colTgt.setCellValueFactory(r -> r.getValue().targetColumnProperty());
    colXform.setCellValueFactory(r -> r.getValue().transformProperty());
    colXform.setCellFactory(TextFieldTableCell.forTableColumn());
    colXform.setOnEditCommit(e -> e.getRowValue().transformProperty().set(e.getNewValue()));
    colWarn.setCellValueFactory(r -> r.getValue().warningProperty());
    tblMapping.setItems(mappingVm.columnPairsProperty());
    tblMapping.setEditable(true);
    cmbFormat.setItems(FXCollections.observableArrayList("json", "yaml"));
    cmbFormat.setValue("json");
    mappingVm.selectedFormatProperty().bind(cmbFormat.valueProperty());
    cmbFormat.valueProperty().addListener((obs, o, n) -> updatePreview());
    taPreview.setEditable(false);
    taPreview.setWrapText(true);
    btnSave.disableProperty().bind(Bindings.isEmpty(mappingVm.columnPairsProperty()));
  }

  private void updatePreview() {
    var sb = new StringBuilder();
    boolean isJson = "json".equals(cmbFormat.getValue());
    if (isJson) {
      sb.append("[\n");
      for (var p : mappingVm.columnPairsProperty()) {
        sb.append(
            String.format(
                "  { \"source\": \"%s\", \"target\": \"%s\" }%n",
                p.sourceColumnProperty().get(), p.targetColumnProperty().get()));
      }
      sb.append("]");
    } else {
      sb.append("columns:\n");
      for (var p : mappingVm.columnPairsProperty()) {
        sb.append(
            String.format(
                "  - source: %s%n    target: %s%n",
                p.sourceColumnProperty().get(), p.targetColumnProperty().get()));
      }
    }
    String preview = sb.toString();
    taPreview.setText(preview);
    mappingVm.setPreview(preview);
  }

  private static void applyDbRefCells(ComboBox<DatabaseRef> cb) {
    cb.setButtonCell(new DbRefCell());
    cb.setCellFactory(lv -> new DbRefCell());
  }

  private static void applyTableRefCells(ComboBox<TableRef> cb) {
    cb.setButtonCell(new TableRefCell());
    cb.setCellFactory(lv -> new TableRefCell());
  }

  private void showErrorAlert(String message) {
    var alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle("Error");
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }

  private static final class DbRefCell extends ListCell<DatabaseRef> {
    @Override
    protected void updateItem(DatabaseRef item, boolean empty) {
      super.updateItem(item, empty);
      setText(item == null || empty ? null : item.name() + "  (" + item.type() + ")");
    }
  }

  private static final class TableRefCell extends ListCell<TableRef> {
    @Override
    protected void updateItem(TableRef item, boolean empty) {
      super.updateItem(item, empty);
      setText(item == null || empty ? null : item.qualifiedName());
    }
  }
}
