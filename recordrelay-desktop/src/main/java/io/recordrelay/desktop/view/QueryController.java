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
import io.recordrelay.cli.engine.QueryRunner;
import io.recordrelay.core.domain.QueryResult;
import io.recordrelay.core.i18n.Messages;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;

/** Controller for the Query Analyzer screen. */
public final class QueryController implements Refreshable {

  @FXML private ComboBox<String> cmbConn;
  @FXML private Button btnRun;
  @FXML private Label lblStatus;
  @FXML private Label lblError;
  @FXML private Label lblRows;
  @FXML private TextArea taSql;
  @FXML private TableView<java.util.List<String>> tblResults;

  private ConnProfileResolver resolver;

  @FXML
  void initialize() {
    try {
      var store = new ConfigStore();
      resolver = new ConnProfileResolver(store);
      loadConnections(store);
    } catch (Exception e) {
      showError(Messages.get("err.config") + ": " + e.getMessage());
    }
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

  @FXML
  void onRun() {
    String conn = cmbConn.getValue();
    if (conn == null || conn.isBlank()) {
      showError("Select a connection first.");
      return;
    }
    String sql = taSql.getText();
    if (sql == null || sql.isBlank()) {
      showError("Enter a SELECT query.");
      return;
    }

    btnRun.setDisable(true);
    lblStatus.setText("Running…");
    lblError.setVisible(false);
    lblError.setManaged(false);
    tblResults.getColumns().clear();
    tblResults.getItems().clear();
    lblRows.setText("");

    new Thread(
            () -> {
              try {
                var profile = resolver.resolve(conn);
                var result = new QueryRunner().run(profile, sql);
                Platform.runLater(() -> showResults(result));
              } catch (Exception ex) {
                Platform.runLater(
                    () -> {
                      showError(Messages.get("query.error", ex.getMessage()));
                      btnRun.setDisable(false);
                      lblStatus.setText(Messages.get("status.ready"));
                    });
              }
            },
            "rr-query")
        .start();
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private void showResults(QueryResult result) {
    tblResults.getColumns().clear();
    tblResults.getItems().clear();

    var columns = result.columns();
    for (int i = 0; i < columns.size(); i++) {
      final int colIdx = i;
      TableColumn<java.util.List<String>, String> col = new TableColumn<>(columns.get(i));
      col.setCellValueFactory(
          cd -> {
            var row = cd.getValue();
            return new SimpleStringProperty(colIdx < row.size() ? row.get(colIdx) : "");
          });
      col.setPrefWidth(120);
      tblResults.getColumns().add(col);
    }

    tblResults.setItems(FXCollections.observableArrayList(result.rows()));
    lblRows.setText(Messages.get("query.rows", result.rowCount()));
    lblStatus.setText(Messages.get("status.ready"));
    btnRun.setDisable(false);
  }

  private void loadConnections(ConfigStore store) {
    cmbConn.getItems().clear();
    try {
      cmbConn.getItems().addAll(store.load().getConnections().keySet());
    } catch (Exception e) {
      showError(Messages.get("err.config") + ": " + e.getMessage());
    }
  }

  private void showError(String msg) {
    lblError.setText(msg);
    lblError.setVisible(true);
    lblError.setManaged(true);
    lblStatus.setText(Messages.get("status.ready"));
    btnRun.setDisable(false);
  }
}
