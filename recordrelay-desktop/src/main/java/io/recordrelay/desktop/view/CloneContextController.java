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
import io.recordrelay.core.clone.domain.BusinessEntity;
import io.recordrelay.core.clone.domain.ContextClonePlan;
import io.recordrelay.core.clone.domain.MaskerType;
import io.recordrelay.core.clone.domain.MaskingConfig;
import io.recordrelay.core.clone.domain.MaskingRule;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.spi.ConnectorRegistry;
import io.recordrelay.desktop.viewmodel.CloneContextViewModel;
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
import javafx.stage.DirectoryChooser;

/** Controller for the Clone Context screen. */
public final class CloneContextController implements Refreshable {

  // ── Step 1: Connections ────────────────────────────────────────────────────
  @FXML private ComboBox<String> cmbSource;
  @FXML private ComboBox<String> cmbTarget;
  @FXML private CheckBox chkExportMode;
  @FXML private Label lblTargetHeader;
  @FXML private HBox hboxOutputDir;
  @FXML private TextField tfOutputDir;
  @FXML private Button btnBrowseDir;

  // ── Step 2: Tables & Record ────────────────────────────────────────────────
  @FXML private Button btnLoadTables;
  @FXML private Label lblTableCount;
  @FXML private ComboBox<String> cmbRootTable;
  @FXML private TextField tfPkColumn;
  @FXML private TextField tfEntityId;

  // ── Step 3: Options ────────────────────────────────────────────────────────
  @FXML private Slider sliderDepth;
  @FXML private Label lblDepthValue;
  @FXML private CheckBox chkMaskPii;

  // ── Actions & Progress ─────────────────────────────────────────────────────
  @FXML private Button btnClone;
  @FXML private Button btnExport;
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
      showError("Config yüklenemedi: " + e.getMessage());
      return;
    }

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
            (obs, o, exportOn) -> {
              cmbTarget.setDisable(exportOn);
              lblTargetHeader.setText(exportOn ? "Çıktı Klasörü" : "Target (Hedef Veritabanı)");
              hboxOutputDir.setVisible(exportOn);
              hboxOutputDir.setManaged(exportOn);
              btnClone.setDisable(exportOn || vm.busyProperty().get());
              btnExport.setDisable(!exportOn || vm.busyProperty().get());
            });
    hboxOutputDir.setVisible(false);
    hboxOutputDir.setManaged(false);
    btnExport.setDisable(true);
  }

  private void bindViewModel() {
    progressBar.progressProperty().bind(vm.progressProperty());
    lblStatus.textProperty().bind(vm.statusTextProperty());
    taLog.textProperty().bind(vm.logTextProperty());
    lblExportedPath.visibleProperty().bind(vm.exportedPathProperty().isNotEmpty());
    lblExportedPath.managedProperty().bind(vm.exportedPathProperty().isNotEmpty());
    lblExportedPath
        .textProperty()
        .bind(vm.exportedPathProperty().map(p -> p.isEmpty() ? "" : "Dosya: " + p));
    vm.busyProperty()
        .addListener(
            (obs, o, busy) -> {
              btnClone.setDisable(busy || chkExportMode.isSelected());
              btnExport.setDisable(busy || !chkExportMode.isSelected());
            });
  }

  private void loadConnections() {
    try {
      var names = FXCollections.observableArrayList(store.load().getConnections().keySet());
      cmbSource.setItems(names);
      cmbTarget.setItems(FXCollections.observableArrayList(names));
    } catch (Exception e) {
      showError("Bağlantılar yüklenemedi: " + e.getMessage());
    }
  }

  // ── Table loading ──────────────────────────────────────────────────────────

  @FXML
  void onSourceChanged() {
    var connName = cmbSource.getValue();
    if (connName == null || connName.isBlank()) return;
    btnLoadTables.setDisable(false);
    onLoadTables();
  }

  @FXML
  void onLoadTables() {
    var connName = cmbSource.getValue();
    if (connName == null || connName.isBlank()) {
      showError("Önce bir source bağlantı seçin.");
      return;
    }
    lblTableCount.setText("Yükleniyor…");
    btnLoadTables.setDisable(true);
    cmbRootTable.setItems(FXCollections.emptyObservableList());

    new Thread(
            () -> {
              try {
                var profile = resolver.resolve(connName);
                var connector = ConnectorRegistry.findConnector(profile);
                var dbRef = new DatabaseRef(profile.database(), profile.type());
                var tables = connector.schemaInspector().listTables(profile, dbRef);
                var tableNames =
                    tables.stream().map(t -> t.tableName()).sorted().toList();

                Platform.runLater(
                    () -> {
                      cmbRootTable.setItems(FXCollections.observableArrayList(tableNames));
                      lblTableCount.setText(tableNames.size() + " tablo");
                      btnLoadTables.setDisable(false);
                      if (!tableNames.isEmpty()) {
                        cmbRootTable.getSelectionModel().selectFirst();
                      }
                    });
              } catch (Exception e) {
                Platform.runLater(
                    () -> {
                      lblTableCount.setText("Yükleme başarısız");
                      btnLoadTables.setDisable(false);
                      showError("Tablolar yüklenemedi: " + e.getMessage());
                    });
              }
            },
            "rr-load-tables")
        .start();
  }

  // ── Action handlers ────────────────────────────────────────────────────────

  @FXML
  void onClone() {
    var err = validate(false);
    if (err != null) {
      showAlert(err);
      return;
    }
    vm.resetProgress();
    new Thread(this::runLiveClone, "rr-clone-ctx").start();
  }

  @FXML
  void onExport() {
    var err = validate(true);
    if (err != null) {
      showAlert(err);
      return;
    }
    vm.resetProgress();
    new Thread(this::runExport, "rr-export-ctx").start();
  }

  @FXML
  void onBrowseDir() {
    var dc = new DirectoryChooser();
    dc.setTitle("Çıktı Klasörü Seç");
    var dir = dc.showDialog(btnBrowseDir.getScene().getWindow());
    if (dir != null) {
      tfOutputDir.setText(dir.getAbsolutePath());
    }
  }

  // ── Clone execution ────────────────────────────────────────────────────────

  private void runLiveClone() {
    try {
      var entity = buildEntity();
      var srcProfile = resolver.resolve(cmbSource.getValue());
      var tgtProfile = resolver.resolve(cmbTarget.getValue());
      var masking = buildMasking();
      int depth = (int) sliderDepth.getValue();

      var plan =
          ContextClonePlan.liveClone(
              entity, tfEntityId.getText().trim(), srcProfile, tgtProfile, depth, masking);

      vm.appendLog(
          "Kopyalanıyor: "
              + entity.tableName()
              + " #"
              + plan.entityId()
              + "  |  "
              + cmbSource.getValue()
              + " → "
              + cmbTarget.getValue()
              + "  |  derinlik="
              + depth);

      var engine = DefaultContextCloneEngine.createDefault();
      var report = engine.cloneContext(plan, buildListener());

      vm.appendLog("\nTamamlandı:");
      vm.appendLog("  Tablo sayısı : " + report.tableCount());
      vm.appendLog("  Kayıt sayısı : " + report.totalRecords());
      vm.appendLog("  Süre         : " + report.formattedDuration());
      if (report.maskedFieldCount() > 0) {
        vm.appendLog("  Maskelenen   : " + report.maskedFieldCount() + " alan");
      }
      vm.markComplete("Tamamlandı — " + report.totalRecords() + " kayıt kopyalandı");
    } catch (Exception e) {
      vm.appendLog("HATA: " + e.getMessage());
      vm.markFailed(e.getMessage());
    }
  }

  private void runExport() {
    try {
      var entity = buildEntity();
      var srcProfile = resolver.resolve(cmbSource.getValue());
      var masking = buildMasking();
      var outDir =
          tfOutputDir.getText().isBlank() ? Path.of(".") : Path.of(tfOutputDir.getText().trim());

      var plan =
          ContextClonePlan.bugCapture(
              entity, tfEntityId.getText().trim(), srcProfile, outDir, masking, null);

      vm.appendLog(
          "Dışa aktarılıyor: "
              + entity.tableName()
              + " #"
              + plan.entityId()
              + "  |  "
              + cmbSource.getValue());

      var engine = DefaultContextCloneEngine.createDefault();
      var pkgPath = engine.exportContext(plan);

      vm.appendLog("\nDosya: " + pkgPath.toAbsolutePath());
      Platform.runLater(() -> vm.exportedPathProperty().set(pkgPath.toAbsolutePath().toString()));
      vm.markComplete("Dışa aktarıldı → " + pkgPath.getFileName());
    } catch (Exception e) {
      vm.appendLog("HATA: " + e.getMessage());
      vm.markFailed(e.getMessage());
    }
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private BusinessEntity buildEntity() {
    var rootTable = cmbRootTable.getValue();
    if (rootTable == null || rootTable.isBlank()) {
      rootTable = "records";
    }
    var pk = tfPkColumn.getText().trim();
    if (pk.isBlank()) pk = "id";
    return BusinessEntity.of(rootTable, rootTable, pk, "");
  }

  private MaskingConfig buildMasking() {
    if (!chkMaskPii.isSelected()) return MaskingConfig.none();
    return new MaskingConfig(
        List.of(
            new MaskingRule("email", MaskerType.EMAIL),
            new MaskingRule("phone", MaskerType.PHONE),
            new MaskingRule("phone_number", MaskerType.PHONE),
            new MaskingRule("address", MaskerType.ADDRESS),
            new MaskingRule("national_id", MaskerType.NATIONAL_ID),
            new MaskingRule("iban", MaskerType.IBAN)));
  }

  private CloneProgressListener buildListener() {
    return new CloneProgressListener() {
      @Override
      public void onRelationshipsDiscovered(int edgeCount) {
        vm.appendLog("  İlişki keşfedildi: " + edgeCount + " edge");
        vm.updateProgress(0.1);
      }

      @Override
      public void onTableExtractionStarted(String tableName) {
        vm.appendLog("  Çekiliyor: " + tableName);
        vm.updateStatus("Çekiliyor: " + tableName + "…");
      }

      @Override
      public void onTableExtractionCompleted(String tableName, long recordCount) {
        vm.appendLog("    └─ " + recordCount + " kayıt");
      }

      @Override
      public void onImportStarted(String tableName) {
        vm.appendLog("  Yazılıyor: " + tableName);
        vm.updateStatus("Yazılıyor: " + tableName + "…");
      }

      @Override
      public void onImportCompleted(String tableName, long recordCount) {
        vm.appendLog("    └─ " + recordCount + " kayıt yazıldı");
      }

      @Override
      public void onWarning(String message) {
        vm.appendLog("  UYARI: " + message);
      }
    };
  }

  private String validate(boolean exportMode) {
    if (cmbSource.getValue() == null || cmbSource.getValue().isBlank()) {
      return "Source bağlantı seçilmedi.";
    }
    if (!exportMode && (cmbTarget.getValue() == null || cmbTarget.getValue().isBlank())) {
      return "Hedef bağlantı seçilmedi (veya Export modunu işaretleyin).";
    }
    if (cmbRootTable.getValue() == null || cmbRootTable.getValue().isBlank()) {
      return "Başlangıç tablosu (root table) girilmedi.";
    }
    if (tfEntityId.getText().isBlank()) {
      return "Kayıt ID'si girilmedi.";
    }
    return null;
  }

  private void showError(String msg) {
    if (lblError != null) {
      Platform.runLater(
          () -> {
            lblError.setText(msg);
            lblError.setVisible(true);
            lblError.setManaged(true);
          });
    }
  }

  private void showAlert(String msg) {
    var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
    alert.setTitle("Uyarı");
    alert.setHeaderText(null);
    alert.setContentText(msg);
    alert.showAndWait();
  }
}
