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
import io.recordrelay.cli.engine.MigrationDriftEngine;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.MigrationDriftItem;
import io.recordrelay.core.domain.MigrationDriftItem.DriftKind;
import io.recordrelay.core.domain.MigrationDriftReport;
import io.recordrelay.desktop.viewmodel.MigrationDriftViewModel;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;

/** Controller for the Migration Drift screen. */
public final class MigrationDriftController implements Refreshable {

  @FXML private ComboBox<String> cmbSrcConn;
  @FXML private ComboBox<DatabaseRef> cmbSrcDb;
  @FXML private ComboBox<String> cmbTgtConn;
  @FXML private ComboBox<DatabaseRef> cmbTgtDb;
  @FXML private Button btnAnalyze;
  @FXML private Label lblStatus;
  @FXML private Label lblError;

  @FXML private HBox hboxSummary;
  @FXML private Label lblSummaryMissing;
  @FXML private Label lblSummaryExtra;
  @FXML private Label lblSummaryColMiss;
  @FXML private Label lblSummaryColExtra;
  @FXML private Label lblSummaryMismatch;
  @FXML private Label lblTotal;

  @FXML private TableView<MigrationDriftItem> tblDrift;
  @FXML private TableColumn<MigrationDriftItem, String> colKind;
  @FXML private TableColumn<MigrationDriftItem, String> colTable;
  @FXML private TableColumn<MigrationDriftItem, String> colColumn;
  @FXML private TableColumn<MigrationDriftItem, String> colSourceDetail;
  @FXML private TableColumn<MigrationDriftItem, String> colTargetDetail;

  private MigrationDriftViewModel vm;
  private ConnProfileResolver resolver;
  private MigrationDriftEngine engine;

  @FXML
  void initialize() {
    try {
      var store = new ConfigStore();
      vm = new MigrationDriftViewModel(store);
      resolver = new ConnProfileResolver(store);
      engine = new MigrationDriftEngine();
    } catch (Exception e) {
      showError("Config init failed: " + e.getMessage());
      return;
    }
    bindSelectors();
    bindTable();
    vm.loadConnections();
  }

  @Override
  public void refresh() {
    if (vm != null) vm.loadConnections();
  }

  @FXML
  void onSrcConnChanged() {
    vm.sourceDatabasesProperty().clear();
    cmbSrcDb.setValue(null);
    String conn = cmbSrcConn.getValue();
    if (conn == null || conn.isBlank()) return;
    lblStatus.setText("Veritabanları yükleniyor…");
    new Thread(() -> loadDatabases(conn, true), "rr-drift-src-db").start();
  }

  @FXML
  void onTgtConnChanged() {
    vm.targetDatabasesProperty().clear();
    cmbTgtDb.setValue(null);
    String conn = cmbTgtConn.getValue();
    if (conn == null || conn.isBlank()) return;
    lblStatus.setText("Veritabanları yükleniyor…");
    new Thread(() -> loadDatabases(conn, false), "rr-drift-tgt-db").start();
  }

  @FXML
  void onAnalyze() {
    String srcConn = cmbSrcConn.getValue();
    DatabaseRef srcDb = cmbSrcDb.getValue();
    String tgtConn = cmbTgtConn.getValue();
    DatabaseRef tgtDb = cmbTgtDb.getValue();
    if (srcConn == null || srcDb == null || tgtConn == null || tgtDb == null) return;

    lblStatus.setText("Analiz ediliyor…");
    btnAnalyze.setDisable(true);
    tblDrift.getItems().clear();
    hboxSummary.setVisible(false);
    hboxSummary.setManaged(false);

    new Thread(
            () -> {
              try {
                var srcProfile = resolver.resolve(srcConn);
                var tgtProfile = resolver.resolve(tgtConn);
                MigrationDriftReport report =
                    engine.analyzeDrift(srcProfile, srcDb, tgtProfile, tgtDb);
                Platform.runLater(() -> showReport(report));
              } catch (Exception ex) {
                Platform.runLater(
                    () -> {
                      showError("Analiz hatası: " + ex.getMessage());
                      btnAnalyze.setDisable(false);
                    });
              }
            },
            "rr-drift-analyze")
        .start();
  }

  private void loadDatabases(String conn, boolean isSource) {
    try {
      var profile = resolver.resolve(conn);
      // Use discovery engine via the connector directly
      var connector = io.recordrelay.core.spi.ConnectorRegistry.findConnector(profile);
      var dbs = connector.listDatabases(profile);
      Platform.runLater(
          () -> {
            if (isSource) {
              vm.sourceDatabasesProperty().setAll(dbs);
            } else {
              vm.targetDatabasesProperty().setAll(dbs);
            }
            lblStatus.setText("● Hazır");
          });
    } catch (Exception e) {
      Platform.runLater(() -> lblStatus.setText("Hata: " + e.getMessage()));
    }
  }

  private void showReport(MigrationDriftReport report) {
    tblDrift.getItems().setAll(report.items());
    btnAnalyze.setDisable(false);

    long tblMissing = report.countByKind(DriftKind.TABLE_MISSING);
    long tblExtra = report.countByKind(DriftKind.TABLE_EXTRA);
    long colMissing = report.countByKind(DriftKind.COLUMN_MISSING);
    long colExtra = report.countByKind(DriftKind.COLUMN_EXTRA);
    long mismatch = report.countByKind(DriftKind.TYPE_MISMATCH);

    lblSummaryMissing.setText("Eksik tablo: " + tblMissing);
    lblSummaryExtra.setText("Fazla tablo: " + tblExtra);
    lblSummaryColMiss.setText("Eksik kolon: " + colMissing);
    lblSummaryColExtra.setText("Fazla kolon: " + colExtra);
    lblSummaryMismatch.setText("Tip uyuşmazlığı: " + mismatch);
    lblTotal.setText("Toplam " + report.items().size() + " fark");

    hboxSummary.setVisible(true);
    hboxSummary.setManaged(true);

    if (report.isClean()) {
      lblStatus.setText("✓ Tam eşleşme — fark yok");
    } else {
      lblStatus.setText("● " + report.items().size() + " fark bulundu");
    }
  }

  private void showError(String msg) {
    lblError.setText(msg);
    lblError.setVisible(true);
    lblError.setManaged(true);
  }

  private void bindSelectors() {
    cmbSrcConn.setItems(vm.connNamesProperty());
    cmbTgtConn.setItems(vm.connNamesProperty());
    cmbSrcDb.setItems(vm.sourceDatabasesProperty());
    cmbTgtDb.setItems(vm.targetDatabasesProperty());
    applyDbRefCells(cmbSrcDb);
    applyDbRefCells(cmbTgtDb);
    lblError.visibleProperty().bind(vm.errorProperty().isNotEmpty());
    lblError.managedProperty().bind(vm.errorProperty().isNotEmpty());
    lblError.textProperty().bind(vm.errorProperty());
    btnAnalyze.disableProperty().bind(
        cmbSrcDb.valueProperty().isNull().or(cmbTgtDb.valueProperty().isNull()));
  }

  private void bindTable() {
    colKind.setCellValueFactory(cd -> new SimpleStringProperty(kindLabel(cd.getValue().kind())));
    colTable.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().tableName()));
    colColumn.setCellValueFactory(
        cd -> new SimpleStringProperty(cd.getValue().columnName() != null ? cd.getValue().columnName() : ""));
    colSourceDetail.setCellValueFactory(
        cd -> new SimpleStringProperty(cd.getValue().sourceDetail() != null ? cd.getValue().sourceDetail() : "—"));
    colTargetDetail.setCellValueFactory(
        cd -> new SimpleStringProperty(cd.getValue().targetDetail() != null ? cd.getValue().targetDetail() : "—"));

    tblDrift.setRowFactory(
        tv -> {
          TableRow<MigrationDriftItem> row = new TableRow<>();
          row.itemProperty()
              .addListener(
                  (obs, old, item) -> {
                    row.getStyleClass()
                        .removeAll("drift-row-missing", "drift-row-extra", "drift-row-mismatch");
                    if (item != null) {
                      row.getStyleClass().add(rowStyle(item.kind()));
                    }
                  });
          return row;
        });
  }

  private static String kindLabel(DriftKind kind) {
    return switch (kind) {
      case TABLE_MISSING -> "Eksik Tablo";
      case TABLE_EXTRA -> "Fazla Tablo";
      case COLUMN_MISSING -> "Eksik Kolon";
      case COLUMN_EXTRA -> "Fazla Kolon";
      case TYPE_MISMATCH -> "Tip Uyuşmazlığı";
    };
  }

  private static String rowStyle(DriftKind kind) {
    return switch (kind) {
      case TABLE_MISSING, COLUMN_MISSING -> "drift-row-missing";
      case TABLE_EXTRA, COLUMN_EXTRA -> "drift-row-extra";
      case TYPE_MISMATCH -> "drift-row-mismatch";
    };
  }

  private static void applyDbRefCells(ComboBox<DatabaseRef> cb) {
    cb.setButtonCell(new DbRefCell());
    cb.setCellFactory(lv -> new DbRefCell());
  }

  private static final class DbRefCell extends ListCell<DatabaseRef> {
    @Override
    protected void updateItem(DatabaseRef item, boolean empty) {
      super.updateItem(item, empty);
      setText(item == null || empty ? null : item.name() + "  (" + item.type() + ")");
    }
  }
}
