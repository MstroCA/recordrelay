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
import io.recordrelay.cli.engine.RowCountDiffEngine;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.RowCountEntry;
import io.recordrelay.core.domain.RowCountEntry.RowStatus;
import io.recordrelay.desktop.viewmodel.MigrationDriftViewModel;
import javafx.application.Platform;
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

/** Controller for the Row Count Diff screen. */
public final class RowCountDiffController implements Refreshable {

  private static final long ABSENT = -2L;

  @FXML private ComboBox<String> cmbSrcConn;
  @FXML private ComboBox<DatabaseRef> cmbSrcDb;
  @FXML private ComboBox<String> cmbTgtConn;
  @FXML private ComboBox<DatabaseRef> cmbTgtDb;
  @FXML private Button btnCompare;
  @FXML private Label lblStatus;
  @FXML private Label lblError;

  @FXML private HBox hboxSummary;
  @FXML private Label lblChipSync;
  @FXML private Label lblChipBehind;
  @FXML private Label lblChipAhead;
  @FXML private Label lblChipTotal;

  @FXML private TableView<RowCountEntry> tblCounts;
  @FXML private TableColumn<RowCountEntry, String> colTable;
  @FXML private TableColumn<RowCountEntry, String> colSource;
  @FXML private TableColumn<RowCountEntry, String> colTarget;
  @FXML private TableColumn<RowCountEntry, String> colDiff;
  @FXML private TableColumn<RowCountEntry, String> colStatus;

  private MigrationDriftViewModel vm;
  private ConnProfileResolver resolver;
  private RowCountDiffEngine engine;

  @FXML
  void initialize() {
    try {
      var store = new ConfigStore();
      vm = new MigrationDriftViewModel(store);
      resolver = new ConnProfileResolver(store);
      engine = new RowCountDiffEngine();
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
    if (vm != null) {
      vm.loadConnections();
    }
  }

  @FXML
  void onSrcConnChanged() {
    vm.sourceDatabasesProperty().clear();
    cmbSrcDb.setValue(null);
    String conn = cmbSrcConn.getValue();
    if (conn == null || conn.isBlank()) {
      return;
    }
    lblStatus.setText("Veritabanları yükleniyor…");
    new Thread(() -> loadDatabases(conn, true), "rr-rcd-src-db").start();
  }

  @FXML
  void onTgtConnChanged() {
    vm.targetDatabasesProperty().clear();
    cmbTgtDb.setValue(null);
    String conn = cmbTgtConn.getValue();
    if (conn == null || conn.isBlank()) {
      return;
    }
    lblStatus.setText("Veritabanları yükleniyor…");
    new Thread(() -> loadDatabases(conn, false), "rr-rcd-tgt-db").start();
  }

  @FXML
  void onCompare() {
    String srcConn = cmbSrcConn.getValue();
    DatabaseRef srcDb = cmbSrcDb.getValue();
    String tgtConn = cmbTgtConn.getValue();
    DatabaseRef tgtDb = cmbTgtDb.getValue();
    if (srcConn == null || srcDb == null || tgtConn == null || tgtDb == null) {
      return;
    }

    btnCompare.setDisable(true);
    lblStatus.setText("Sayılıyor…");
    tblCounts.getItems().clear();
    hboxSummary.setVisible(false);
    hboxSummary.setManaged(false);

    new Thread(
            () -> {
              try {
                var srcProfile = resolver.resolve(srcConn);
                var tgtProfile = resolver.resolve(tgtConn);
                var results = engine.compare(srcProfile, srcDb, tgtProfile, tgtDb);
                Platform.runLater(() -> showResults(results));
              } catch (Exception ex) {
                Platform.runLater(
                    () -> {
                      showError("Karşılaştırma hatası: " + ex.getMessage());
                      btnCompare.setDisable(false);
                    });
              }
            },
            "rr-rcd-compare")
        .start();
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private void loadDatabases(String conn, boolean isSource) {
    try {
      var profile = resolver.resolve(conn);
      var connector = io.recordrelay.core.spi.ConnectorRegistry.findConnector(profile);
      var dbs = connector.listDatabases(profile);
      Platform.runLater(
          () -> {
            if (isSource) {
              vm.sourceDatabasesProperty().setAll(dbs);
            } else {
              vm.targetDatabasesProperty().setAll(dbs);
            }
            lblStatus.setText("Hazır");
          });
    } catch (Exception e) {
      Platform.runLater(() -> lblStatus.setText("Hata: " + e.getMessage()));
    }
  }

  private void showResults(java.util.List<RowCountEntry> results) {
    tblCounts.getItems().setAll(results);
    btnCompare.setDisable(false);

    long sync = results.stream().filter(e -> e.status() == RowStatus.IN_SYNC).count();
    long behind = results.stream().filter(e -> e.status() == RowStatus.TARGET_BEHIND).count();
    long ahead = results.stream().filter(e -> e.status() == RowStatus.TARGET_AHEAD).count();

    lblChipSync.setText("Eşit: " + sync);
    lblChipBehind.setText("Hedef eksik: " + behind);
    lblChipAhead.setText("Hedef fazla: " + ahead);
    lblChipTotal.setText("Toplam " + results.size() + " tablo");

    hboxSummary.setVisible(true);
    hboxSummary.setManaged(true);

    lblStatus.setText(
        (behind + ahead == 0)
            ? "Tüm tablolar eşit"
            : (behind + ahead) + " tabloda fark var");
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

    // Use listeners (not bind) so btnCompare.setDisable() can still be called freely
    Runnable updateBtn =
        () -> btnCompare.setDisable(cmbSrcDb.getValue() == null || cmbTgtDb.getValue() == null);
    cmbSrcDb.valueProperty().addListener((obs, o, n) -> updateBtn.run());
    cmbTgtDb.valueProperty().addListener((obs, o, n) -> updateBtn.run());
    btnCompare.setDisable(true);
  }

  private void bindTable() {
    colTable.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().tableName()));
    colSource.setCellValueFactory(
        cd -> new SimpleStringProperty(formatCount(cd.getValue().sourceCount())));
    colTarget.setCellValueFactory(
        cd -> new SimpleStringProperty(formatCount(cd.getValue().targetCount())));
    colDiff.setCellValueFactory(
        cd -> {
          RowCountEntry e = cd.getValue();
          if (e.sourceCount() < 0 || e.targetCount() < 0) {
            return new SimpleStringProperty("—");
          }
          long d = e.diff();
          return new SimpleStringProperty(d == 0 ? "0" : (d > 0 ? "+" + d : String.valueOf(d)));
        });
    colStatus.setCellValueFactory(
        cd -> new SimpleStringProperty(statusLabel(cd.getValue().status())));

    tblCounts.setRowFactory(
        tv -> {
          TableRow<RowCountEntry> row = new TableRow<>();
          row.itemProperty()
              .addListener(
                  (obs, old, item) -> {
                    row.getStyleClass()
                        .removeAll("drift-row-missing", "drift-row-extra", "drift-row-mismatch");
                    if (item != null) {
                      String style = rowStyle(item.status());
                      if (!style.isEmpty()) {
                        row.getStyleClass().add(style);
                      }
                    }
                  });
          return row;
        });
  }

  private static String formatCount(long count) {
    if (count == ABSENT) {
      return "(yok)";
    }
    if (count < 0) {
      return "—";
    }
    return String.format("%,d", count);
  }

  private static String statusLabel(RowStatus status) {
    return switch (status) {
      case IN_SYNC -> "Eşit";
      case TARGET_BEHIND -> "↓ Hedef eksik";
      case TARGET_AHEAD -> "↑ Hedef fazla";
      case UNSUPPORTED -> "— Desteklenmiyor";
    };
  }

  private static String rowStyle(RowStatus status) {
    return switch (status) {
      case TARGET_BEHIND -> "drift-row-missing";
      case TARGET_AHEAD -> "drift-row-extra";
      case UNSUPPORTED -> "drift-row-mismatch";
      case IN_SYNC -> "";
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
