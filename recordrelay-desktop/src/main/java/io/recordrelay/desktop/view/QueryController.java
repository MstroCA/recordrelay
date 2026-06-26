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
import io.recordrelay.cli.engine.QueryRunner;
import io.recordrelay.cli.flow.QueryFlowModel;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.QueryResult;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.i18n.Messages;
import io.recordrelay.core.spi.ConnectorRegistry;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;

/** Controller for the Query Analyzer screen. Supports visual flow mode and raw SQL mode. */
public final class QueryController implements Refreshable {

  // ── FXML fields ────────────────────────────────────────────────────────────────
  @FXML private ComboBox<String> cmbConn;
  @FXML private ComboBox<DatabaseRef> cmbDb;
  @FXML private ToggleButton btnFlow;
  @FXML private ToggleButton btnSql;
  @FXML private Button btnRun;
  @FXML private Label lblStatus;
  @FXML private Label lblError;
  @FXML private Label lblRows;

  @FXML private VBox flowPane;
  @FXML private VBox sqlPane;
  @FXML private ScrollPane canvasScroll;
  @FXML private ScrollPane conditionsScroll;
  @FXML private ListView<TableRef> lstTables;

  @FXML private TextArea taSql;
  @FXML private TextArea taSqlPreview;
  @FXML private TableView<List<String>> tblResults;

  // ── Domain state ────────────────────────────────────────────────────────────────
  private final QueryFlowModel flowModel = new QueryFlowModel();
  private FlowQueryCanvas flowCanvas;
  private ConnProfileResolver resolver;
  private int nextNodeX = 20;

  // ── Lifecycle ───────────────────────────────────────────────────────────────────

  @FXML
  void initialize() {
    flowCanvas = new FlowQueryCanvas(flowModel);
    canvasScroll.setContent(flowCanvas);
    conditionsScroll.setContent(new FlowConditionsPane(flowModel));

    flowModel.addChangeListener(() -> taSqlPreview.setText(flowModel.toSql()));

    lstTables.setCellFactory(lv -> new TableRefCell());
    lstTables.setOnMouseClicked(
        e -> {
          if (e.getClickCount() == 2) {
            onAddTableToCanvas();
          }
        });

    btnFlow.setOnAction(e -> switchMode(true));
    btnSql.setOnAction(e -> switchMode(false));
    btnRun.setOnAction(e -> onRun());
    cmbDb.setOnAction(e -> loadTables());
    cmbConn.setOnAction(e -> onConnectionChanged());

    try {
      var store = new ConfigStore();
      resolver = new ConnProfileResolver(store);
      loadConnections(store);
    } catch (Exception e) {
      showError(Messages.get("err.config") + ": " + e.getMessage());
    }

    switchMode(true);
  }

  @Override
  public void refresh() {
    try {
      var store = new ConfigStore();
      resolver = new ConnProfileResolver(store);
      loadConnections(store);
    } catch (Exception e) {
      showError(Messages.get("err.config") + ": " + e.getMessage());
    }
  }

  // ── Mode switching ──────────────────────────────────────────────────────────────

  private void switchMode(boolean flow) {
    btnFlow.setSelected(flow);
    btnSql.setSelected(!flow);
    applyToggleStyle(btnFlow, flow);
    applyToggleStyle(btnSql, !flow);
    flowPane.setVisible(flow);
    flowPane.setManaged(flow);
    sqlPane.setVisible(!flow);
    sqlPane.setManaged(!flow);
  }

  private static void applyToggleStyle(ToggleButton btn, boolean active) {
    if (active) {
      btn.setStyle(
          "-fx-background-color: #1E88E5; -fx-text-fill: white;" + " -fx-background-radius: 4;");
    } else {
      btn.setStyle(
          "-fx-background-color: transparent; -fx-text-fill: #555;" + " -fx-background-radius: 4;");
    }
  }

  // ── Actions ─────────────────────────────────────────────────────────────────────

  @FXML
  void onRun() {
    String conn = cmbConn.getValue();
    String sql = btnFlow.isSelected() ? flowModel.toSql().trim() : taSql.getText().trim();
    if (conn == null || conn.isBlank()) {
      showError("Select a connection first.");
      return;
    }
    if (sql.isBlank() || sql.startsWith("--")) {
      showError("Build a query or enter SQL first.");
      return;
    }
    btnRun.setDisable(true);
    lblStatus.setText("Running…");
    clearResults();
    new Thread(() -> runQuery(conn, sql), "rr-query").start();
  }

  private void runQuery(String conn, String sql) {
    try {
      var profile = resolver.resolve(conn);
      var result = new QueryRunner().run(profile, sql);
      Platform.runLater(() -> showResults(result));
    } catch (Exception ex) {
      String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName();
      Platform.runLater(
          () -> {
            showError(Messages.get("query.error", msg));
            btnRun.setDisable(false);
            lblStatus.setText(Messages.get("status.ready"));
          });
    }
  }

  // ── Flow: add table ─────────────────────────────────────────────────────────────

  @FXML
  void onAddTableToCanvas() {
    var table = lstTables.getSelectionModel().getSelectedItem();
    if (table == null || cmbConn.getValue() == null) {
      return;
    }
    int posX = nextNodeX;
    nextNodeX += 240;
    String conn = cmbConn.getValue();
    new Thread(() -> loadAndPlaceTable(conn, table, posX), "rr-col-load").start();
  }

  private void loadAndPlaceTable(String conn, TableRef table, int posX) {
    try {
      var profile = resolver.resolve(conn);
      var cols = new DiscoveryEngine().inspectColumns(profile, table);
      Platform.runLater(
          () -> {
            var entry = flowModel.addNode(table, cols);
            flowCanvas.addTableNode(entry, posX, 20);
          });
    } catch (Exception ex) {
      Platform.runLater(() -> showError("Column load failed: " + ex.getMessage()));
    }
  }

  // ── Data loading ────────────────────────────────────────────────────────────────

  private void onConnectionChanged() {
    flowModel.clear();
    flowCanvas.clear();
    lstTables.getItems().clear();
    cmbDb.getItems().clear();
    nextNodeX = 20;
    loadDatabases();
  }

  private void loadConnections(ConfigStore store) {
    cmbConn.getItems().clear();
    try {
      cmbConn.getItems().addAll(store.load().getConnections().keySet());
      if (!cmbConn.getItems().isEmpty()) {
        cmbConn.setValue(cmbConn.getItems().get(0));
        loadDatabases();
      }
    } catch (Exception e) {
      showError(Messages.get("err.config") + ": " + e.getMessage());
    }
  }

  private void loadDatabases() {
    String conn = cmbConn.getValue();
    if (conn == null || conn.isBlank()) {
      return;
    }
    new Thread(
            () -> {
              try {
                var profile = resolver.resolve(conn);
                var dbs = new DiscoveryEngine().discoverDatabases(profile);
                Platform.runLater(
                    () -> {
                      cmbDb.getItems().setAll(dbs);
                      if (!dbs.isEmpty()) {
                        cmbDb.setValue(dbs.get(0));
                      }
                    });
              } catch (Exception ex) {
                Platform.runLater(() -> showError("DB load failed: " + ex.getMessage()));
              }
            },
            "rr-db-load")
        .start();
  }

  private void loadTables() {
    String conn = cmbConn.getValue();
    var db = cmbDb.getValue();
    if (conn == null || db == null) {
      return;
    }
    new Thread(
            () -> {
              try {
                var profile = resolver.resolve(conn);
                var connector = ConnectorRegistry.findConnector(profile);
                var tables = connector.schemaInspector().listTables(profile, db);
                Platform.runLater(
                    () -> lstTables.setItems(FXCollections.observableArrayList(tables)));
              } catch (Exception ex) {
                Platform.runLater(() -> showError("Table load failed: " + ex.getMessage()));
              }
            },
            "rr-tbl-load")
        .start();
  }

  // ── Results rendering ────────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private void showResults(QueryResult result) {
    tblResults.getColumns().clear();
    tblResults.getItems().clear();
    var columns = result.columns();
    for (int i = 0; i < columns.size(); i++) {
      final int idx = i;
      TableColumn<List<String>, String> col = new TableColumn<>(columns.get(i));
      col.setCellValueFactory(
          cd -> {
            var row = cd.getValue();
            return new SimpleStringProperty(idx < row.size() ? row.get(idx) : "");
          });
      col.setPrefWidth(120);
      tblResults.getColumns().add(col);
    }
    tblResults.setItems(FXCollections.observableArrayList(result.rows()));
    lblRows.setText(Messages.get("query.rows", result.rowCount()));
    lblStatus.setText(Messages.get("status.ready"));
    btnRun.setDisable(false);
  }

  private void clearResults() {
    tblResults.getColumns().clear();
    tblResults.getItems().clear();
    lblRows.setText("");
    lblError.setVisible(false);
    lblError.setManaged(false);
  }

  private void showError(String msg) {
    lblError.setText(msg);
    lblError.setVisible(true);
    lblError.setManaged(true);
    lblStatus.setText(Messages.get("status.ready"));
    btnRun.setDisable(false);
  }

  // ── Inner type ───────────────────────────────────────────────────────────────────

  private static final class TableRefCell extends javafx.scene.control.ListCell<TableRef> {
    @Override
    protected void updateItem(TableRef item, boolean empty) {
      super.updateItem(item, empty);
      setText(empty || item == null ? null : item.tableName());
    }
  }
}
