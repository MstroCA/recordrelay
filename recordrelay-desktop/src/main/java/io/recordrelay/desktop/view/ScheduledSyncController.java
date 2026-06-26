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

import io.recordrelay.cli.config.ScheduledSync;
import io.recordrelay.cli.config.SyncStore;
import io.recordrelay.cli.engine.ConnProfileResolver;
import io.recordrelay.core.clone.domain.BusinessEntity;
import io.recordrelay.core.clone.domain.ContextClonePlan;
import io.recordrelay.core.clone.domain.MaskerType;
import io.recordrelay.core.clone.domain.MaskingConfig;
import io.recordrelay.core.clone.domain.MaskingRule;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.engine.clone.BuiltinEntityRegistry;
import io.recordrelay.engine.clone.DefaultContextCloneEngine;
import java.time.Instant;
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

/** Controller for the Scheduled Sync screen. */
public final class ScheduledSyncController implements Refreshable {

  // ── Sync list table ────────────────────────────────────────────────────────
  @FXML private TableView<ScheduledSync> tblSyncs;
  @FXML private TableColumn<ScheduledSync, String> colName;
  @FXML private TableColumn<ScheduledSync, String> colEntity;
  @FXML private TableColumn<ScheduledSync, String> colConnections;
  @FXML private TableColumn<ScheduledSync, String> colSchedule;
  @FXML private TableColumn<ScheduledSync, String> colLastRun;
  @FXML private TableColumn<ScheduledSync, String> colStatus;

  // ── Add form ───────────────────────────────────────────────────────────────
  @FXML private TextField tfName;
  @FXML private TextField tfEntityName;
  @FXML private TextField tfEntityId;
  @FXML private ComboBox<String> cmbSource;
  @FXML private ComboBox<String> cmbTarget;
  @FXML private Spinner<Integer> spinDepth;
  @FXML private CheckBox chkMaskPii;
  @FXML private TextField tfCron;
  @FXML private TextField tfIntervalMinutes;

  // ── Actions & log ──────────────────────────────────────────────────────────
  @FXML private Button btnAdd;
  @FXML private Button btnRunNow;
  @FXML private Button btnRemove;
  @FXML private Label lblStatus;
  @FXML private TextArea taLog;

  private SyncStore store;
  private ConnProfileResolver resolver;

  @FXML
  void initialize() {
    try {
      store = new SyncStore();
      var configStore = new io.recordrelay.cli.config.ConfigStore();
      resolver = new ConnProfileResolver(configStore);
      setupTable();
      loadConnections(configStore);
      spinDepth.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 3));
      refresh();
    } catch (Exception e) {
      appendLog("Init error: " + e.getMessage());
    }
  }

  @Override
  public void refresh() {
    loadSyncs();
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
    colSchedule.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().scheduleLabel()));
    colLastRun.setCellValueFactory(
        cd ->
            new SimpleStringProperty(
                cd.getValue().getLastRunAt() != null ? cd.getValue().getLastRunAt() : "—"));
    colStatus.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getLastStatus()));
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

  private void loadSyncs() {
    try {
      var all = store.loadAll();
      tblSyncs.setItems(FXCollections.observableArrayList(all));
    } catch (Exception e) {
      appendLog("Could not load syncs: " + e.getMessage());
    }
  }

  @FXML
  void onAdd() {
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
      var entry = new ScheduledSync();
      entry.setName(name);
      entry.setEntityName(entityName);
      entry.setEntityId(entityId);
      entry.setSourceConn(sourceConn);
      entry.setTargetConn(targetConn);
      entry.setDepth(spinDepth.getValue());
      entry.setMaskPii(chkMaskPii.isSelected());
      var cron = tfCron.getText().trim();
      if (!cron.isBlank()) {
        entry.setCronExpression(cron);
      }
      var interval = tfIntervalMinutes.getText().trim();
      if (!interval.isBlank()) {
        entry.setIntervalMinutes(Integer.parseInt(interval));
      }
      store.addOrReplace(entry);
      appendLog("Sync '" + name + "' saved.");
      lblStatus.setText("Saved: " + name);
      clearForm();
      loadSyncs();
    } catch (Exception e) {
      appendLog("Error saving sync: " + e.getMessage());
    }
  }

  @FXML
  void onRunNow() {
    var selected = tblSyncs.getSelectionModel().getSelectedItem();
    if (selected == null) {
      showAlert("Select a sync from the table first.");
      return;
    }
    btnRunNow.setDisable(true);
    appendLog("Running sync '" + selected.getName() + "'…");
    lblStatus.setText("Running " + selected.getName() + "…");

    new Thread(() -> runSync(selected), "rr-sync-run").start();
  }

  @FXML
  void onRemove() {
    var selected = tblSyncs.getSelectionModel().getSelectedItem();
    if (selected == null) {
      showAlert("Select a sync from the table first.");
      return;
    }
    var confirm = new Alert(Alert.AlertType.CONFIRMATION);
    confirm.setTitle("Remove Sync");
    confirm.setHeaderText(null);
    confirm.setContentText("Remove sync '" + selected.getName() + "'?");
    confirm
        .showAndWait()
        .filter(r -> r == ButtonType.OK)
        .ifPresent(
            r -> {
              try {
                store.remove(selected.getName());
                appendLog("Sync '" + selected.getName() + "' removed.");
                loadSyncs();
              } catch (Exception e) {
                appendLog("Error removing sync: " + e.getMessage());
              }
            });
  }

  private void runSync(ScheduledSync entry) {
    try {
      var srcProfile = resolver.resolve(entry.getSourceConn());
      var tgtProfile = resolver.resolve(entry.getTargetConn());
      var entity = resolveEntity(entry);
      var masking = buildMasking(entry);
      var plan =
          ContextClonePlan.liveClone(
              entity, entry.getEntityId(), srcProfile, tgtProfile, entry.getDepth(), masking);

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

      store.updateRunResult(entry.getName(), Instant.now().toString(), "OK");
      appendLog(
          "Sync '"
              + entry.getName()
              + "' complete — "
              + report.totalRecords()
              + " records in "
              + report.formattedDuration());
      Platform.runLater(
          () -> {
            lblStatus.setText("Done: " + entry.getName());
            btnRunNow.setDisable(false);
            loadSyncs();
          });
    } catch (Exception e) {
      try {
        store.updateRunResult(entry.getName(), Instant.now().toString(), "FAILED");
      } catch (Exception ignored) {
      }
      appendLog("ERROR: " + e.getMessage());
      Platform.runLater(
          () -> {
            lblStatus.setText("Failed: " + entry.getName());
            btnRunNow.setDisable(false);
            loadSyncs();
          });
    }
  }

  private BusinessEntity resolveEntity(ScheduledSync entry) {
    return BuiltinEntityRegistry.INSTANCE
        .findByName(entry.getEntityName())
        .orElseGet(
            () -> BusinessEntity.of(entry.getEntityName(), entry.getEntityName() + "s", "id", ""));
  }

  private MaskingConfig buildMasking(ScheduledSync entry) {
    if (!entry.isMaskPii()) {
      return MaskingConfig.none();
    }
    return new MaskingConfig(
        List.of(
            new MaskingRule("email", MaskerType.EMAIL),
            new MaskingRule("phone", MaskerType.PHONE),
            new MaskingRule("phone_number", MaskerType.PHONE),
            new MaskingRule("address", MaskerType.ADDRESS),
            new MaskingRule("national_id", MaskerType.NATIONAL_ID),
            new MaskingRule("iban", MaskerType.IBAN)));
  }

  private void clearForm() {
    tfName.clear();
    tfEntityName.clear();
    tfEntityId.clear();
    cmbSource.setValue(null);
    cmbTarget.setValue(null);
    spinDepth.getValueFactory().setValue(3);
    chkMaskPii.setSelected(false);
    tfCron.clear();
    tfIntervalMinutes.clear();
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
