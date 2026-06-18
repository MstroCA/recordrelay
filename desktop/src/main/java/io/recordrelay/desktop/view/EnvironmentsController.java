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
import io.recordrelay.cli.config.EnvironmentEntry;
import io.recordrelay.desktop.viewmodel.EnvironmentViewModel;
import java.util.Optional;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

/** Controller for the Environments screen — lists, adds, edits, and removes environments. */
public final class EnvironmentsController implements Refreshable {

  @FXML private TableView<EnvironmentEntry> table;
  @FXML private TableColumn<EnvironmentEntry, String> colName;
  @FXML private TableColumn<EnvironmentEntry, String> colDesc;
  @FXML private Button btnEdit;
  @FXML private Button btnRemove;
  @FXML private Label lblError;

  private EnvironmentViewModel vm;

  @FXML
  void initialize() {
    try {
      vm = new EnvironmentViewModel(new ConfigStore());
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

  /** Reloads the environment list; called each time this screen is shown. */
  @Override
  public void refresh() {
    if (vm != null) {
      vm.load();
    }
  }

  @FXML
  void onAdd() {
    showDialog("Add Environment", "", "").ifPresent(v -> vm.add(v[0], v[1]));
  }

  @FXML
  void onEdit() {
    EnvironmentEntry sel = table.getSelectionModel().getSelectedItem();
    if (sel == null) {
      return;
    }
    showDialog("Edit Environment", sel.getName(), sel.getDescription())
        .ifPresent(
            v -> {
              vm.remove(sel.getName());
              vm.add(v[0], v[1]);
            });
  }

  @FXML
  void onRemove() {
    EnvironmentEntry sel = table.getSelectionModel().getSelectedItem();
    if (sel == null) {
      return;
    }
    var alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle("Remove Environment");
    alert.setHeaderText("Remove \"" + sel.getName() + "\"?");
    alert.setContentText("This action cannot be undone.");
    alert.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> vm.remove(sel.getName()));
  }

  private void bindTable() {
    colName.setCellValueFactory(r -> new ReadOnlyStringWrapper(r.getValue().getName()));
    colDesc.setCellValueFactory(r -> new ReadOnlyStringWrapper(r.getValue().getDescription()));
    table.setItems(vm.environmentsProperty());
  }

  private void bindButtons() {
    var noSel = table.getSelectionModel().selectedItemProperty().isNull();
    btnEdit.disableProperty().bind(noSel);
    btnRemove.disableProperty().bind(noSel);
    lblError.textProperty().bind(vm.errorProperty());
    lblError.visibleProperty().bind(vm.errorProperty().isNotEmpty());
    lblError.managedProperty().bind(vm.errorProperty().isNotEmpty());
  }

  private Optional<String[]> showDialog(String title, String initName, String initDesc) {
    var dialog = new Dialog<String[]>();
    dialog.setTitle(title);
    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

    var grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(new Insets(20, 16, 16, 16));

    var tfName = new TextField(initName);
    var tfDesc = new TextField(initDesc);
    tfName.setPromptText("e.g. production");
    tfDesc.setPromptText("e.g. Live production environment");
    tfName.setPrefWidth(260);
    tfDesc.setPrefWidth(260);

    grid.add(new Label("Name:"), 0, 0);
    grid.add(tfName, 1, 0);
    grid.add(new Label("Description:"), 0, 1);
    grid.add(tfDesc, 1, 1);
    GridPane.setHgrow(tfName, Priority.ALWAYS);
    GridPane.setHgrow(tfDesc, Priority.ALWAYS);

    dialog.getDialogPane().setContent(grid);

    var okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
    okBtn.disableProperty().bind(tfName.textProperty().isEmpty());

    dialog.setResultConverter(
        btn ->
            btn == ButtonType.OK
                ? new String[] {tfName.getText().trim(), tfDesc.getText().trim()}
                : null);

    return dialog.showAndWait();
  }
}
