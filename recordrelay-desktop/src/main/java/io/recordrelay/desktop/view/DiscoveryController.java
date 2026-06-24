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
import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.desktop.viewmodel.DiscoveryViewModel;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/** Controller for the Schema Discovery screen. */
public final class DiscoveryController implements Refreshable {

  @FXML private ComboBox<String> cmbSrcConn;
  @FXML private ComboBox<DatabaseRef> cmbSrcDb;
  @FXML private ComboBox<TableRef> cmbSrcTable;
  @FXML private Button btnDiscover;
  @FXML private Label lblDiscoveryStatus;
  @FXML private Label lblError;

  @FXML private TableView<ColumnMeta> tblColumns;
  @FXML private TableColumn<ColumnMeta, String> colName;
  @FXML private TableColumn<ColumnMeta, String> colType;
  @FXML private TableColumn<ColumnMeta, Boolean> colNullable;
  @FXML private TableColumn<ColumnMeta, Boolean> colPk;

  private DiscoveryViewModel discoveryVm;
  private ConnProfileResolver resolver;
  private DiscoveryEngine engine;

  @FXML
  void initialize() {
    try {
      var store = new ConfigStore();
      discoveryVm = new DiscoveryViewModel(store);
      resolver = new ConnProfileResolver(store);
      engine = new DiscoveryEngine();
    } catch (Exception e) {
      lblError.setText("Config init failed: " + e.getMessage());
      lblError.setVisible(true);
      lblError.setManaged(true);
      return;
    }
    bindDiscoveryPanel();
    bindColumnsTable();
    discoveryVm.loadConnections();
  }

  @Override
  public void refresh() {
    if (discoveryVm != null) {
      discoveryVm.loadConnections();
    }
  }

  @FXML
  void onSrcConnChanged() {
    discoveryVm.sourceDatabasesProperty().clear();
    discoveryVm.sourceDbProperty().set(null);
    discoveryVm.sourceTablesProperty().clear();
    String conn = cmbSrcConn.getValue();
    if (conn == null || conn.isEmpty()) {
      return;
    }
    lblDiscoveryStatus.setText("Loading databases…");
    new Thread(() -> runLoadDatabases(conn), "rr-disc-db").start();
  }

  @FXML
  void onSrcDbChanged() {
    DatabaseRef db = cmbSrcDb.getValue();
    String conn = cmbSrcConn.getValue();
    if (db == null || conn == null) {
      return;
    }
    discoveryVm.sourceTablesProperty().clear();
    lblDiscoveryStatus.setText("Loading tables…");
    new Thread(() -> runLoadTables(conn, db), "rr-disc-tbl").start();
  }

  @FXML
  void onDiscover() {
    TableRef src = cmbSrcTable.getValue();
    String conn = cmbSrcConn.getValue();
    if (src == null || conn == null) {
      return;
    }
    lblDiscoveryStatus.setText("Inspecting columns…");
    tblColumns.getItems().clear();
    new Thread(() -> runInspect(conn, src), "rr-disc-inspect").start();
  }

  private void runLoadDatabases(String conn) {
    try {
      var profile = resolver.resolve(conn);
      var dbs = engine.discoverDatabases(profile);
      Platform.runLater(
          () -> {
            discoveryVm.sourceDatabasesProperty().setAll(dbs);
            lblDiscoveryStatus.setText("Ready");
          });
    } catch (Exception e) {
      Platform.runLater(() -> lblDiscoveryStatus.setText("Error: " + e.getMessage()));
    }
  }

  private void runLoadTables(String conn, DatabaseRef db) {
    try {
      var profile = resolver.resolve(conn);
      var tables = engine.discoverTables(profile, db);
      Platform.runLater(
          () -> {
            discoveryVm.sourceTablesProperty().setAll(tables);
            lblDiscoveryStatus.setText("Ready");
          });
    } catch (Exception e) {
      Platform.runLater(() -> lblDiscoveryStatus.setText("Error: " + e.getMessage()));
    }
  }

  private void runInspect(String conn, TableRef table) {
    try {
      var profile = resolver.resolve(conn);
      var cols = engine.inspectColumns(profile, table);
      Platform.runLater(
          () -> {
            tblColumns.getItems().setAll(cols);
            lblDiscoveryStatus.setText(
                cols.size() + " column" + (cols.size() == 1 ? "" : "s"));
          });
    } catch (Exception e) {
      Platform.runLater(() -> lblDiscoveryStatus.setText("Error: " + e.getMessage()));
    }
  }

  private void bindDiscoveryPanel() {
    cmbSrcConn.setItems(discoveryVm.connNamesProperty());
    cmbSrcDb.setItems(discoveryVm.sourceDatabasesProperty());
    cmbSrcTable.setItems(discoveryVm.sourceTablesProperty());
    applyDbRefCells(cmbSrcDb);
    applyTableRefCells(cmbSrcTable);
    btnDiscover.disableProperty().bind(cmbSrcTable.valueProperty().isNull());
    lblError.textProperty().bind(discoveryVm.errorProperty());
    lblError.visibleProperty().bind(discoveryVm.errorProperty().isNotEmpty());
    lblError.managedProperty().bind(discoveryVm.errorProperty().isNotEmpty());
  }

  private void bindColumnsTable() {
    colName.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().name()));
    colType.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().nativeType()));
    colNullable.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().nullable()));
    colPk.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().primaryKey()));
  }

  private static void applyDbRefCells(ComboBox<DatabaseRef> cb) {
    cb.setButtonCell(new DbRefCell());
    cb.setCellFactory(lv -> new DbRefCell());
  }

  private static void applyTableRefCells(ComboBox<TableRef> cb) {
    cb.setButtonCell(new TableRefCell());
    cb.setCellFactory(lv -> new TableRefCell());
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
