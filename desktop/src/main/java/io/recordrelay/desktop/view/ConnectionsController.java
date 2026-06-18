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
import io.recordrelay.cli.config.ConnectionEntry;
import io.recordrelay.desktop.viewmodel.ConnectionViewModel;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

/** Controller for the Connections screen — lists, adds, edits, tests, and removes connections. */
public final class ConnectionsController implements Refreshable {

  private static final List<String> DB_TYPES =
      List.of(
          "POSTGRESQL",
          "MYSQL",
          "MARIADB",
          "ORACLE",
          "SQLSERVER",
          "MONGODB",
          "CASSANDRA",
          "REDIS",
          "ELASTICSEARCH");

  @FXML private TableView<Map.Entry<String, ConnectionEntry>> table;
  @FXML private TableColumn<Map.Entry<String, ConnectionEntry>, String> colName;
  @FXML private TableColumn<Map.Entry<String, ConnectionEntry>, String> colType;
  @FXML private TableColumn<Map.Entry<String, ConnectionEntry>, String> colHost;
  @FXML private TableColumn<Map.Entry<String, ConnectionEntry>, String> colPort;
  @FXML private TableColumn<Map.Entry<String, ConnectionEntry>, String> colDatabase;
  @FXML private TableColumn<Map.Entry<String, ConnectionEntry>, String> colUser;
  @FXML private Button btnEdit;
  @FXML private Button btnTest;
  @FXML private Button btnRemove;
  @FXML private Label lblError;
  @FXML private Label lblStatus;

  private ConfigStore store;
  private ConnectionViewModel vm;

  @FXML
  void initialize() {
    try {
      store = new ConfigStore();
      vm = new ConnectionViewModel(store);
    } catch (Exception e) {
      lblError.setText("Config init failed: " + e.getMessage());
      lblError.setVisible(true);
      lblError.setManaged(true);
      return;
    }
    bindTable();
    bindButtons();
    vm.load();
  }

  /** Reloads the connection list; called each time this screen is shown. */
  @Override
  public void refresh() {
    if (vm != null) {
      vm.load();
    }
  }

  @FXML
  void onAdd() {
    showConnDialog("Add Connection", "", null).ifPresent(this::addConnection);
  }

  @FXML
  void onEdit() {
    Map.Entry<String, ConnectionEntry> sel = table.getSelectionModel().getSelectedItem();
    if (sel == null) {
      return;
    }
    showConnDialog("Edit Connection", sel.getKey(), sel.getValue())
        .ifPresent(r -> editConnection(r, sel.getKey(), sel.getValue().getEncryptedPassword()));
  }

  @FXML
  void onRemove() {
    Map.Entry<String, ConnectionEntry> sel = table.getSelectionModel().getSelectedItem();
    if (sel == null) {
      return;
    }
    var alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle("Remove Connection");
    alert.setHeaderText("Remove \"" + sel.getKey() + "\"?");
    alert.setContentText("This action cannot be undone.");
    alert.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> vm.remove(sel.getKey()));
  }

  @FXML
  void onTest() {
    Map.Entry<String, ConnectionEntry> sel = table.getSelectionModel().getSelectedItem();
    if (sel == null) {
      return;
    }
    btnTest.setDisable(true);
    lblStatus.setText("Testing…");
    String name = sel.getKey();
    new Thread(() -> runTest(name), "rr-conn-test").start();
  }

  private void runTest(String name) {
    String result = vm.testConnection(name);
    Platform.runLater(
        () -> {
          btnTest.setDisable(false);
          lblStatus.setText("● Ready");
          if (result.isEmpty()) {
            showInfoAlert("Connection OK", "\"" + name + "\" is reachable.");
          } else {
            showErrorAlert("Connection failed: " + result);
          }
        });
  }

  private void addConnection(ConnDialogResult r) {
    try {
      vm.add(r.name(), buildEntry(r, null));
    } catch (Exception e) {
      showErrorAlert("Encrypt failed: " + e.getMessage());
    }
  }

  private void editConnection(ConnDialogResult r, String oldName, String existingEncPwd) {
    try {
      vm.remove(oldName);
      vm.add(r.name(), buildEntry(r, existingEncPwd));
    } catch (Exception e) {
      showErrorAlert("Update failed: " + e.getMessage());
    }
  }

  private ConnectionEntry buildEntry(ConnDialogResult r, String existingEncPwd) throws Exception {
    var entry = new ConnectionEntry();
    entry.setType(r.type());
    entry.setHost(r.host());
    entry.setPort(parsePort(r.portText()));
    entry.setDatabase(r.database());
    entry.setUser(r.user());
    if (!r.rawPassword().isEmpty()) {
      // encrypt — never store or log the plaintext value
      entry.setEncryptedPassword(store.encryptor().encrypt(r.rawPassword()));
    } else if (existingEncPwd != null) {
      entry.setEncryptedPassword(existingEncPwd);
    }
    return entry;
  }

  private void bindTable() {
    colName.setCellValueFactory(r -> new ReadOnlyStringWrapper(r.getValue().getKey()));
    colType.setCellValueFactory(r -> new ReadOnlyStringWrapper(r.getValue().getValue().getType()));
    colHost.setCellValueFactory(r -> new ReadOnlyStringWrapper(r.getValue().getValue().getHost()));
    colPort.setCellValueFactory(
        r -> new ReadOnlyStringWrapper(String.valueOf(r.getValue().getValue().getPort())));
    colDatabase.setCellValueFactory(
        r -> new ReadOnlyStringWrapper(r.getValue().getValue().getDatabase()));
    colUser.setCellValueFactory(r -> new ReadOnlyStringWrapper(r.getValue().getValue().getUser()));
    table.setItems(vm.connectionsProperty());
  }

  private void bindButtons() {
    var noSel = table.getSelectionModel().selectedItemProperty().isNull();
    btnEdit.disableProperty().bind(noSel);
    btnTest.disableProperty().bind(noSel);
    btnRemove.disableProperty().bind(noSel);
    lblError.textProperty().bind(vm.errorProperty());
    lblError.visibleProperty().bind(vm.errorProperty().isNotEmpty());
    lblError.managedProperty().bind(vm.errorProperty().isNotEmpty());
  }

  private Optional<ConnDialogResult> showConnDialog(
      String title, String initName, ConnectionEntry init) {
    var dialog = new Dialog<ConnDialogResult>();
    dialog.setTitle(title);
    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    dialog.getDialogPane().setPrefWidth(440);

    ConnFields f = createConnFields(initName, init);
    dialog.getDialogPane().setContent(buildConnGrid(f));

    var okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
    okBtn
        .disableProperty()
        .bind(
            f.name()
                .textProperty()
                .isEmpty()
                .or(f.type().valueProperty().isNull())
                .or(f.host().textProperty().isEmpty())
                .or(f.database().textProperty().isEmpty())
                .or(f.user().textProperty().isEmpty()));

    dialog.setResultConverter(
        btn ->
            btn == ButtonType.OK
                ? new ConnDialogResult(
                    f.name().getText().trim(),
                    f.type().getValue(),
                    f.host().getText().trim(),
                    f.port().getText().trim(),
                    f.database().getText().trim(),
                    f.user().getText().trim(),
                    f.password().getText())
                : null);

    return dialog.showAndWait();
  }

  private ConnFields createConnFields(String initName, ConnectionEntry init) {
    var tfName = new TextField(initName);
    var cbType = new ComboBox<>(FXCollections.observableArrayList(DB_TYPES));
    var tfHost = new TextField(init != null ? nullSafe(init.getHost()) : "");
    var tfPort = new TextField(init != null ? String.valueOf(init.getPort()) : "");
    var tfDb = new TextField(init != null ? nullSafe(init.getDatabase()) : "");
    var tfUser = new TextField(init != null ? nullSafe(init.getUser()) : "");
    var pfPass = new PasswordField();
    cbType.setValue(init != null ? init.getType() : null);
    tfName.setPromptText("e.g. prod-pg");
    cbType.setPromptText("Database type");
    tfHost.setPromptText("e.g. localhost");
    tfPort.setPromptText("e.g. 5432");
    pfPass.setPromptText(init != null ? "(unchanged)" : "Password");
    return new ConnFields(tfName, cbType, tfHost, tfPort, tfDb, tfUser, pfPass);
  }

  private GridPane buildConnGrid(ConnFields f) {
    var grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);
    grid.setPadding(new Insets(20, 16, 16, 16));
    String[] labels = {"Name:", "Type:", "Host:", "Port:", "Database:", "User:", "Password:"};
    Node[] controls = {
      f.name(), f.type(), f.host(), f.port(), f.database(), f.user(), f.password()
    };
    for (int i = 0; i < labels.length; i++) {
      grid.add(new Label(labels[i]), 0, i);
      grid.add(controls[i], 1, i);
      GridPane.setHgrow(controls[i], Priority.ALWAYS);
    }
    return grid;
  }

  private void showErrorAlert(String message) {
    var alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle("Error");
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }

  private void showInfoAlert(String title, String message) {
    var alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }

  private static int parsePort(String text) {
    try {
      return Integer.parseInt(text.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static String nullSafe(String value) {
    return value != null ? value : "";
  }

  private record ConnFields(
      TextField name,
      ComboBox<String> type,
      TextField host,
      TextField port,
      TextField database,
      TextField user,
      PasswordField password) {}

  private record ConnDialogResult(
      String name,
      String type,
      String host,
      String portText,
      String database,
      String user,
      String rawPassword) {}
}
