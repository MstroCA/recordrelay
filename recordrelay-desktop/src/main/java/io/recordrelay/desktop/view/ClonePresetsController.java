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

import io.recordrelay.cli.config.ClonePreset;
import io.recordrelay.cli.config.PresetStore;
import io.recordrelay.cli.engine.ConnProfileResolver;
import io.recordrelay.core.clone.domain.BusinessEntity;
import io.recordrelay.core.clone.domain.ContextClonePlan;
import io.recordrelay.core.clone.domain.FieldOverride;
import io.recordrelay.core.clone.domain.FieldOverrideConfig;
import io.recordrelay.core.clone.domain.MaskerType;
import io.recordrelay.core.clone.domain.MaskingConfig;
import io.recordrelay.core.clone.domain.MaskingRule;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.engine.clone.BuiltinEntityRegistry;
import io.recordrelay.engine.clone.DefaultContextCloneEngine;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

/** Controller for the Clone Presets (Bookmarks) screen. */
public final class ClonePresetsController implements Refreshable {

  // ── Preset table ───────────────────────────────────────────────────────────
  @FXML private TableView<ClonePreset> tblPresets;
  @FXML private TableColumn<ClonePreset, String> colName;
  @FXML private TableColumn<ClonePreset, String> colEntity;
  @FXML private TableColumn<ClonePreset, String> colConnections;
  @FXML private TableColumn<ClonePreset, String> colOptions;
  @FXML private TableColumn<ClonePreset, String> colDescription;

  // ── Save form ──────────────────────────────────────────────────────────────
  @FXML private TextField tfName;
  @FXML private TextField tfEntityName;
  @FXML private TextField tfEntityId;
  @FXML private ComboBox<String> cmbSource;
  @FXML private ComboBox<String> cmbTarget;
  @FXML private Spinner<Integer> spinDepth;
  @FXML private CheckBox chkMaskPii;
  @FXML private TextField tfDescription;
  @FXML private TextArea taOverrides;

  // ── Actions & log ──────────────────────────────────────────────────────────
  @FXML private Button btnSave;
  @FXML private Button btnRun;
  @FXML private Button btnRemove;
  @FXML private Label lblStatus;
  @FXML private TextArea taLog;

  private PresetStore store;
  private ConnProfileResolver resolver;

  @FXML
  void initialize() {
    try {
      store = new PresetStore();
      var configStore = new io.recordrelay.cli.config.ConfigStore();
      resolver = new ConnProfileResolver(configStore);
      setupTable();
      loadConnections(configStore);
      spinDepth.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 3));
      taOverrides.setPromptText("column=value\ntable:column=value");
      refresh();
    } catch (Exception e) {
      appendLog("Init error: " + e.getMessage());
    }
  }

  @Override
  public void refresh() {
    loadPresets();
  }

  private void setupTable() {
    colName.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getName()));
    colEntity.setCellValueFactory(
        cd ->
            new SimpleStringProperty(
                cd.getValue().getEntityName() + " #" + cd.getValue().getEntityId()));
    colConnections.setCellValueFactory(
        cd ->
            new SimpleStringProperty(
                cd.getValue().getSourceConn() + " → " + cd.getValue().getTargetConn()));
    colOptions.setCellValueFactory(
        cd ->
            new SimpleStringProperty(
                "depth=" + cd.getValue().getDepth() + (cd.getValue().isMaskPii() ? " +mask" : "")));
    colDescription.setCellValueFactory(
        cd ->
            new SimpleStringProperty(
                cd.getValue().getDescription() != null ? cd.getValue().getDescription() : ""));
  }

  private void loadConnections(io.recordrelay.cli.config.ConfigStore configStore) {
    try {
      var names = FXCollections.observableArrayList(configStore.load().getConnections().keySet());
      cmbSource.setItems(names);
      cmbTarget.setItems(FXCollections.observableArrayList(names));
    } catch (Exception e) {
      appendLog("Could not load connections: " + e.getMessage());
    }
  }

  private void loadPresets() {
    try {
      tblPresets.setItems(FXCollections.observableArrayList(store.loadAll()));
    } catch (Exception e) {
      appendLog("Could not load presets: " + e.getMessage());
    }
  }

  @FXML
  void onSave() {
    var name = tfName.getText().trim();
    var entityName = tfEntityName.getText().trim();
    var entityId = tfEntityId.getText().trim();
    var sourceConn = cmbSource.getValue();
    var targetConn = cmbTarget.getValue();

    if (name.isBlank()
        || entityName.isBlank()
        || entityId.isBlank()
        || sourceConn == null
        || targetConn == null) {
      showAlert("Name, entity name, entity ID, source and target are required.");
      return;
    }
    try {
      var p = new ClonePreset();
      p.setName(name);
      p.setEntityName(entityName);
      p.setEntityId(entityId);
      p.setSourceConn(sourceConn);
      p.setTargetConn(targetConn);
      p.setDepth(spinDepth.getValue());
      p.setMaskPii(chkMaskPii.isSelected());
      var desc = tfDescription.getText().trim();
      if (!desc.isBlank()) p.setDescription(desc);
      var overrideLines =
          taOverrides
              .getText()
              .lines()
              .map(String::trim)
              .filter(l -> !l.isBlank() && !l.startsWith("#"))
              .toList();
      if (!overrideLines.isEmpty()) p.setFieldOverrides(overrideLines);
      store.addOrReplace(p);
      appendLog("Preset '" + name + "' saved.");
      lblStatus.setText("Saved: " + name);
      clearForm();
      loadPresets();
    } catch (Exception e) {
      appendLog("Error saving preset: " + e.getMessage());
    }
  }

  @FXML
  void onRun() {
    var selected = tblPresets.getSelectionModel().getSelectedItem();
    if (selected == null) {
      showAlert("Select a preset from the table first.");
      return;
    }
    btnRun.setDisable(true);
    appendLog("Running preset '" + selected.getName() + "'…");
    lblStatus.setText("Running " + selected.getName() + "…");
    new Thread(() -> runPreset(selected), "rr-preset-run").start();
  }

  @FXML
  void onRemove() {
    var selected = tblPresets.getSelectionModel().getSelectedItem();
    if (selected == null) {
      showAlert("Select a preset from the table first.");
      return;
    }
    var confirm = new Alert(Alert.AlertType.CONFIRMATION);
    confirm.setTitle("Remove Preset");
    confirm.setHeaderText(null);
    confirm.setContentText("Remove preset '" + selected.getName() + "'?");
    confirm
        .showAndWait()
        .filter(r -> r == ButtonType.OK)
        .ifPresent(
            r -> {
              try {
                store.remove(selected.getName());
                appendLog("Preset '" + selected.getName() + "' removed.");
                loadPresets();
              } catch (Exception e) {
                appendLog("Error: " + e.getMessage());
              }
            });
  }

  private void runPreset(ClonePreset p) {
    try {
      var srcProfile = resolver.resolve(p.getSourceConn());
      var tgtProfile = resolver.resolve(p.getTargetConn());
      var entity = resolveEntity(p);
      var masking = buildMasking(p);
      var overrides = buildOverrides(p);
      var plan =
          ContextClonePlan.liveCloneWithOverrides(
              entity, p.getEntityId(), srcProfile, tgtProfile, p.getDepth(), masking, overrides);

      var report =
          DefaultContextCloneEngine.createDefault()
              .cloneContext(
                  plan,
                  new CloneProgressListener() {
                    @Override
                    public void onTableExtractionCompleted(String tableName, long recordCount) {
                      appendLog("  Fetched " + tableName + ": " + recordCount + " rows");
                    }

                    @Override
                    public void onWarning(String message) {
                      appendLog("  WARN: " + message);
                    }
                  });

      appendLog("Done — " + report.totalRecords() + " records in " + report.formattedDuration());
      Platform.runLater(
          () -> {
            lblStatus.setText("Done: " + p.getName());
            btnRun.setDisable(false);
          });
    } catch (Exception e) {
      appendLog("ERROR: " + e.getMessage());
      Platform.runLater(
          () -> {
            lblStatus.setText("Failed: " + p.getName());
            btnRun.setDisable(false);
          });
    }
  }

  private BusinessEntity resolveEntity(ClonePreset p) {
    return BuiltinEntityRegistry.INSTANCE
        .findByName(p.getEntityName())
        .orElseGet(() -> BusinessEntity.of(p.getEntityName(), p.getEntityName() + "s", "id", ""));
  }

  private MaskingConfig buildMasking(ClonePreset p) {
    if (!p.isMaskPii()) return MaskingConfig.none();
    return new MaskingConfig(
        List.of(
            new MaskingRule("email", MaskerType.EMAIL),
            new MaskingRule("phone", MaskerType.PHONE),
            new MaskingRule("phone_number", MaskerType.PHONE),
            new MaskingRule("address", MaskerType.ADDRESS),
            new MaskingRule("national_id", MaskerType.NATIONAL_ID),
            new MaskingRule("iban", MaskerType.IBAN)));
  }

  private FieldOverrideConfig buildOverrides(ClonePreset p) {
    if (p.getFieldOverrides() == null || p.getFieldOverrides().isEmpty()) {
      return FieldOverrideConfig.none();
    }
    var list = new ArrayList<FieldOverride>();
    for (var raw : p.getFieldOverrides()) {
      int colonIdx = raw.indexOf(':');
      int eqIdx = raw.indexOf('=');
      if (eqIdx < 0) continue;
      if (colonIdx > 0 && colonIdx < eqIdx) {
        list.add(
            FieldOverride.forTable(
                raw.substring(0, colonIdx).trim(),
                raw.substring(colonIdx + 1, eqIdx).trim(),
                raw.substring(eqIdx + 1)));
      } else {
        list.add(FieldOverride.global(raw.substring(0, eqIdx).trim(), raw.substring(eqIdx + 1)));
      }
    }
    return new FieldOverrideConfig(list);
  }

  private void clearForm() {
    tfName.clear();
    tfEntityName.clear();
    tfEntityId.clear();
    cmbSource.setValue(null);
    cmbTarget.setValue(null);
    spinDepth.getValueFactory().setValue(3);
    chkMaskPii.setSelected(false);
    tfDescription.clear();
    taOverrides.clear();
  }

  private void appendLog(String line) {
    Platform.runLater(
        () -> {
          var cur = taLog.getText();
          taLog.setText(cur.isEmpty() ? line : cur + "\n" + line);
          taLog.setScrollTop(Double.MAX_VALUE);
        });
  }

  private void showAlert(String msg) {
    var alert = new Alert(Alert.AlertType.WARNING);
    alert.setTitle("Warning");
    alert.setHeaderText(null);
    alert.setContentText(msg);
    alert.showAndWait();
  }
}
