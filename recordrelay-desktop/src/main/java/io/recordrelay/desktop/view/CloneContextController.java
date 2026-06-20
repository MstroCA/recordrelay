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
import io.recordrelay.core.clone.domain.BugReport;
import io.recordrelay.core.clone.domain.ContextClonePlan;
import io.recordrelay.core.clone.domain.MaskerType;
import io.recordrelay.core.clone.domain.MaskingConfig;
import io.recordrelay.core.clone.domain.MaskingRule;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.desktop.viewmodel.CloneContextViewModel;
import io.recordrelay.engine.clone.BuiltinEntityRegistry;
import io.recordrelay.engine.clone.DefaultContextCloneEngine;
import java.nio.file.Path;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

/**
 * Controller for the Clone Context screen.
 *
 * <p>Lets engineers reproduce a production business context (customer, order, user…) locally in
 * under 5 minutes, or export it as a portable {@code .rrpkg} reproduction package.
 */
public final class CloneContextController implements Refreshable {

  // ── Entity selection ───────────────────────────────────────────────────────
  @FXML private ComboBox<String> cmbEntity;
  @FXML private TextField tfEntityId;

  // ── Connections ────────────────────────────────────────────────────────────
  @FXML private ComboBox<String> cmbSource;
  @FXML private ComboBox<String> cmbTarget;
  @FXML private CheckBox chkExportMode;
  @FXML private Label lblTargetOrDir;
  @FXML private HBox hboxOutputDir;
  @FXML private TextField tfOutputDir;
  @FXML private Button btnBrowseDir;

  // ── Options ────────────────────────────────────────────────────────────────
  @FXML private Slider sliderDepth;
  @FXML private Label lblDepthValue;
  @FXML private CheckBox chkMaskPii;

  // ── Bug context ────────────────────────────────────────────────────────────
  @FXML private VBox paneBugContext;
  @FXML private TextField tfBugId;
  @FXML private TextField tfBugTitle;
  @FXML private TextField tfBugService;
  @FXML private TextField tfBugEnv;

  // ── Actions ────────────────────────────────────────────────────────────────
  @FXML private Button btnClone;
  @FXML private Button btnExport;

  // ── Progress ───────────────────────────────────────────────────────────────
  @FXML private ProgressBar progressBar;
  @FXML private Label lblStatus;
  @FXML private TextArea taLog;
  @FXML private Label lblError;
  @FXML private Label lblExportedPath;

  private CloneContextViewModel vm;
  private ConfigStore store;
  private ConnProfileResolver resolver;

  @FXML
  void initialize() {
    vm = new CloneContextViewModel();
    try {
      store = new ConfigStore();
      resolver = new ConnProfileResolver(store);
    } catch (Exception e) {
      showError("Config init failed: " + e.getMessage());
      return;
    }

    setupEntityCombo();
    setupDepthSlider();
    setupExportModeToggle();
    bindViewModel();
    loadConnections();
  }

  @Override
  public void refresh() {
    if (store != null) {
      loadConnections();
    }
  }

  // ── Setup ──────────────────────────────────────────────────────────────────

  private void setupEntityCombo() {
    var entities =
        BuiltinEntityRegistry.INSTANCE.listAll().stream()
            .map(e -> e.displayName() + "  (" + e.tableName() + ")")
            .toList();
    cmbEntity.setItems(FXCollections.observableArrayList(entities));
    cmbEntity.getItems().add(0, "Custom (use --table)");
    cmbEntity.getSelectionModel().select(1); // default: Customer
  }

  private void setupDepthSlider() {
    sliderDepth.setMin(1);
    sliderDepth.setMax(10);
    sliderDepth.setValue(3);
    sliderDepth.setMajorTickUnit(1);
    sliderDepth.setMinorTickCount(0);
    sliderDepth.setSnapToTicks(true);
    lblDepthValue.setText("3");
    sliderDepth
        .valueProperty()
        .addListener((obs, o, n) -> lblDepthValue.setText(String.valueOf(n.intValue())));
  }

  private void setupExportModeToggle() {
    chkExportMode
        .selectedProperty()
        .addListener(
            (obs, o, v) -> {
              cmbTarget.setDisable(v);
              lblTargetOrDir.setText(v ? "Output Directory:" : "Target:");
              hboxOutputDir.setVisible(v);
              hboxOutputDir.setManaged(v);
              btnClone.setDisable(v);
              btnExport.setDisable(!v);
              paneBugContext.setVisible(v);
              paneBugContext.setManaged(v);
            });
    // Initial state: live clone mode
    hboxOutputDir.setVisible(false);
    hboxOutputDir.setManaged(false);
    btnExport.setDisable(true);
    paneBugContext.setVisible(false);
    paneBugContext.setManaged(false);
  }

  private void bindViewModel() {
    progressBar.progressProperty().bind(vm.progressProperty());
    lblStatus.textProperty().bind(vm.statusTextProperty());
    taLog.textProperty().bind(vm.logTextProperty());
    lblError.textProperty().bind(vm.errorProperty());
    lblError.visibleProperty().bind(vm.errorProperty().isNotEmpty());
    lblError.managedProperty().bind(vm.errorProperty().isNotEmpty());
    lblExportedPath.visibleProperty().bind(vm.exportedPathProperty().isNotEmpty());
    lblExportedPath.managedProperty().bind(vm.exportedPathProperty().isNotEmpty());
    lblExportedPath
        .textProperty()
        .bind(vm.exportedPathProperty().map(p -> p.isEmpty() ? "" : "Exported: " + p));
    btnClone.disableProperty().bind(vm.busyProperty());
    btnExport.disableProperty().bind(vm.busyProperty());
  }

  private void loadConnections() {
    try {
      var names = FXCollections.observableArrayList(store.load().getConnections().keySet());
      cmbSource.setItems(names);
      cmbTarget.setItems(FXCollections.observableArrayList(names));
    } catch (Exception e) {
      showError("Failed to load connections: " + e.getMessage());
    }
  }

  // ── Action handlers ────────────────────────────────────────────────────────

  @FXML
  void onClone() {
    var validation = validate(false);
    if (validation != null) {
      showAlert(validation);
      return;
    }
    vm.resetProgress();
    new Thread(this::runLiveClone, "rr-clone-ctx").start();
  }

  @FXML
  void onExport() {
    var validation = validate(true);
    if (validation != null) {
      showAlert(validation);
      return;
    }
    vm.resetProgress();
    new Thread(this::runExport, "rr-export-ctx").start();
  }

  @FXML
  void onBrowseDir() {
    var dc = new DirectoryChooser();
    dc.setTitle("Select Output Directory");
    var dir = dc.showDialog(btnBrowseDir.getScene().getWindow());
    if (dir != null) {
      tfOutputDir.setText(dir.getAbsolutePath());
    }
  }

  // ── Clone execution ────────────────────────────────────────────────────────

  private void runLiveClone() {
    try {
      var entity = resolveEntity();
      var srcProfile = resolver.resolve(cmbSource.getValue());
      var tgtProfile = resolver.resolve(cmbTarget.getValue());
      var masking = buildMasking();
      int depth = (int) sliderDepth.getValue();

      var plan =
          ContextClonePlan.liveClone(
              entity, tfEntityId.getText().trim(), srcProfile, tgtProfile, depth, masking);

      vm.appendLog(
          "Cloning "
              + entity.displayName()
              + " #"
              + plan.entityId()
              + " from '"
              + cmbSource.getValue()
              + "' → '"
              + cmbTarget.getValue()
              + "'");

      var engine = DefaultContextCloneEngine.createDefault();
      var report = engine.cloneContext(plan, buildListener());

      vm.appendLog("\nClone complete:");
      vm.appendLog("  Tables  : " + report.tableCount());
      vm.appendLog("  Records : " + report.totalRecords());
      vm.appendLog("  Duration: " + report.formattedDuration());
      if (report.maskedFieldCount() > 0) {
        vm.appendLog("  Masked  : " + report.maskedFieldCount() + " field(s)");
      }
      vm.markComplete("Clone complete — " + report.totalRecords() + " records");
    } catch (Exception e) {
      vm.appendLog("ERROR: " + e.getMessage());
      vm.markFailed(e.getMessage());
    }
  }

  private void runExport() {
    try {
      var entity = resolveEntity();
      var srcProfile = resolver.resolve(cmbSource.getValue());
      var masking = buildMasking();
      var outDir =
          tfOutputDir.getText().isBlank() ? Path.of(".") : Path.of(tfOutputDir.getText().trim());
      var bugReport = buildBugReport();

      var plan =
          ContextClonePlan.bugCapture(
              entity, tfEntityId.getText().trim(), srcProfile, outDir, masking, bugReport);

      vm.appendLog(
          "Exporting "
              + entity.displayName()
              + " #"
              + plan.entityId()
              + " from '"
              + cmbSource.getValue()
              + "'");

      var engine = DefaultContextCloneEngine.createDefault();
      var pkgPath = engine.exportContext(plan);

      vm.appendLog("\nExported: " + pkgPath.toAbsolutePath());
      Platform.runLater(() -> vm.exportedPathProperty().set(pkgPath.toAbsolutePath().toString()));
      vm.markComplete("Exported → " + pkgPath.getFileName());
    } catch (Exception e) {
      vm.appendLog("ERROR: " + e.getMessage());
      vm.markFailed(e.getMessage());
    }
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private io.recordrelay.core.clone.domain.BusinessEntity resolveEntity() {
    var selected = cmbEntity.getValue();
    if (selected == null || selected.startsWith("Custom")) {
      return io.recordrelay.core.clone.domain.BusinessEntity.of("custom", "records");
    }
    // Format: "Customer  (customers)" — extract the name part
    var name = selected.split("\\s+")[0].toLowerCase();
    return BuiltinEntityRegistry.INSTANCE
        .findByName(name)
        .orElseGet(() -> io.recordrelay.core.clone.domain.BusinessEntity.of(name, name + "s"));
  }

  private MaskingConfig buildMasking() {
    if (!chkMaskPii.isSelected()) {
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

  private BugReport buildBugReport() {
    var title = tfBugTitle.getText().trim();
    if (title.isBlank()) {
      return null;
    }
    var id = tfBugId.getText().trim();
    if (id.isBlank()) {
      id = "UNKNOWN";
    }
    return BugReport.of(
        id,
        title,
        tfBugService.getText().trim().isEmpty() ? null : tfBugService.getText().trim(),
        tfBugEnv.getText().trim().isEmpty() ? null : tfBugEnv.getText().trim(),
        null);
  }

  private CloneProgressListener buildListener() {
    return new CloneProgressListener() {
      @Override
      public void onRelationshipsDiscovered(int edgeCount) {
        vm.appendLog("  Discovered " + edgeCount + " relationship edge(s)");
        vm.updateProgress(0.1);
      }

      @Override
      public void onTableExtractionStarted(String tableName) {
        vm.appendLog("  Extracting: " + tableName);
        vm.updateStatus("Extracting " + tableName + "…");
      }

      @Override
      public void onTableExtractionCompleted(String tableName, long recordCount) {
        vm.appendLog("    └─ " + recordCount + " record(s)");
      }

      @Override
      public void onImportStarted(String tableName) {
        vm.appendLog("  Importing:  " + tableName);
        vm.updateStatus("Importing " + tableName + "…");
      }

      @Override
      public void onImportCompleted(String tableName, long recordCount) {
        vm.appendLog("    └─ " + recordCount + " record(s) imported");
      }

      @Override
      public void onWarning(String message) {
        vm.appendLog("  WARN: " + message);
      }
    };
  }

  private String validate(boolean exportMode) {
    if (tfEntityId.getText().isBlank()) {
      return "Entity ID is required.";
    }
    if (cmbSource.getValue() == null || cmbSource.getValue().isBlank()) {
      return "Source connection is required.";
    }
    if (!exportMode && (cmbTarget.getValue() == null || cmbTarget.getValue().isBlank())) {
      return "Target connection is required for live clone.";
    }
    return null;
  }

  private void showError(String msg) {
    if (lblError != null) {
      lblError.setText(msg);
      lblError.setVisible(true);
      lblError.setManaged(true);
    }
  }

  private void showAlert(String msg) {
    var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
    alert.setTitle("Validation");
    alert.setHeaderText(null);
    alert.setContentText(msg);
    alert.showAndWait();
  }
}
