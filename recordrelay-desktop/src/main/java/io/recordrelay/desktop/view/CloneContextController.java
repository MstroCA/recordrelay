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
import io.recordrelay.core.clone.domain.ConflictResolution;
import io.recordrelay.core.clone.domain.ContextClonePlan;
import io.recordrelay.core.clone.domain.DryRunReport;
import io.recordrelay.core.clone.domain.DryRunTableEntry;
import io.recordrelay.core.clone.domain.FieldOverride;
import io.recordrelay.core.clone.domain.FieldOverrideConfig;
import io.recordrelay.core.clone.domain.MaskerType;
import io.recordrelay.core.clone.domain.MaskingConfig;
import io.recordrelay.core.clone.domain.MaskingRule;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.RootTableCandidate;
import io.recordrelay.core.i18n.Messages;
import io.recordrelay.core.spi.ConnectorRegistry;
import io.recordrelay.desktop.viewmodel.CloneContextViewModel;
import io.recordrelay.engine.clone.DefaultContextCloneEngine;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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
  @FXML private Button btnDetectRoot;
  @FXML private VBox rootSuggestPanel;
  @FXML private FlowPane rootSuggestFlow;
  @FXML private TextField tfPkColumn;
  @FXML private TextField tfEntityId;

  // ── Step 3: Options ────────────────────────────────────────────────────────
  @FXML private Slider sliderDepth;
  @FXML private Label lblDepthValue;
  @FXML private CheckBox chkMaskPii;
  @FXML private ComboBox<ConflictResolution> cmbConflict;
  @FXML private TextField tfIdStart;

  // ── Step 4: Field Overrides ────────────────────────────────────────────────
  @FXML private TableView<OverrideRow> tblOverrides;
  @FXML private TableColumn<OverrideRow, String> colOvrTable;
  @FXML private TableColumn<OverrideRow, String> colOvrColumn;
  @FXML private TableColumn<OverrideRow, String> colOvrValue;

  private final ObservableList<OverrideRow> overrideRows = FXCollections.observableArrayList();

  // ── Step 5: Satellite (companion) tables ───────────────────────────────────
  @FXML private TableView<SatelliteRow> tblSatellites;
  @FXML private TableColumn<SatelliteRow, String> colSatSource;
  @FXML private TableColumn<SatelliteRow, String> colSatTarget;
  @FXML private TableColumn<SatelliteRow, String> colSatTable;
  @FXML private TableColumn<SatelliteRow, String> colSatLink;
  @FXML private TableColumn<SatelliteRow, String> colSatPk;

  private final ObservableList<SatelliteRow> satelliteRows = FXCollections.observableArrayList();

  // ── Actions & Progress ─────────────────────────────────────────────────────
  @FXML private Button btnClone;
  @FXML private Button btnExport;
  @FXML private Button btnDryRun;
  @FXML private ProgressBar progressBar;
  @FXML private Label lblStatus;
  @FXML private TextArea taLog;
  @FXML private Label lblError;
  @FXML private Label lblExportedPath;

  // ── Dry Run panel ──────────────────────────────────────────────────────────
  @FXML private javafx.scene.layout.VBox dryRunPanel;
  @FXML private Label lblDryRunSummary;
  @FXML private TableView<DryRunTableEntry> tblDryRun;
  @FXML private TableColumn<DryRunTableEntry, String> colDryTable;
  @FXML private TableColumn<DryRunTableEntry, String> colDryRows;
  @FXML private TableColumn<DryRunTableEntry, String> colDryDepth;

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
    setupConflictCombo();
    setupExportModeToggle();
    setupOverrideTable();
    setupSatelliteTable();
    bindViewModel();
    bindDryRunTable();
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

  private void setupConflictCombo() {
    cmbConflict.getItems().setAll(ConflictResolution.values());
    cmbConflict.setValue(ConflictResolution.REGENERATE_IDENTITIES);
    cmbConflict.setConverter(
        new javafx.util.StringConverter<>() {
          @Override
          public String toString(ConflictResolution v) {
            if (v == null) return "";
            return switch (v) {
              case REGENERATE_IDENTITIES -> "Regenerate IDs (default)";
              case SKIP_EXISTING -> "Skip existing rows";
              case ISOLATE_NAMESPACE -> "Isolate namespace";
              case FAIL_SAFE -> "Fail if target has data";
              case SEQUENCE -> "Native sequence (DB-assigned IDs)";
              case START_AT -> "Start at custom ID";
            };
          }

          @Override
          public ConflictResolution fromString(String s) {
            return ConflictResolution.REGENERATE_IDENTITIES;
          }
        });
  }

  private void setupExportModeToggle() {
    chkExportMode
        .selectedProperty()
        .addListener(
            (obs, o, exportOn) -> {
              cmbTarget.setDisable(exportOn);
              lblTargetHeader.setText(
                  exportOn ? Messages.get("cc.lbl.outdir") : Messages.get("cc.lbl.target"));
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

  private void setupOverrideTable() {
    tblOverrides.setEditable(true);
    colOvrTable.setCellValueFactory(r -> r.getValue().tableNameProperty());
    colOvrColumn.setCellValueFactory(r -> r.getValue().columnProperty());
    colOvrValue.setCellValueFactory(r -> r.getValue().valueProperty());
    colOvrTable.setCellFactory(TextFieldTableCell.forTableColumn());
    colOvrColumn.setCellFactory(TextFieldTableCell.forTableColumn());
    colOvrValue.setCellFactory(TextFieldTableCell.forTableColumn());
    colOvrTable.setOnEditCommit(e -> e.getRowValue().tableNameProperty().set(e.getNewValue()));
    colOvrColumn.setOnEditCommit(e -> e.getRowValue().columnProperty().set(e.getNewValue()));
    colOvrValue.setOnEditCommit(e -> e.getRowValue().valueProperty().set(e.getNewValue()));
    tblOverrides.setItems(overrideRows);
  }

  private void setupSatelliteTable() {
    tblSatellites.setEditable(true);
    bindSatelliteColumn(colSatSource, SatelliteRow::sourceConnProperty);
    bindSatelliteColumn(colSatTarget, SatelliteRow::targetConnProperty);
    bindSatelliteColumn(colSatTable, SatelliteRow::tableProperty);
    bindSatelliteColumn(colSatLink, SatelliteRow::linkColumnProperty);
    bindSatelliteColumn(colSatPk, SatelliteRow::pkColumnProperty);
    tblSatellites.setItems(satelliteRows);
    // Reload the saved satellites for whichever root table is selected.
    cmbRootTable.valueProperty().addListener((obs, o, n) -> loadSatellitesForEntity(n));
  }

  private void bindSatelliteColumn(
      TableColumn<SatelliteRow, String> column,
      java.util.function.Function<SatelliteRow, StringProperty> prop) {
    column.setCellValueFactory(r -> prop.apply(r.getValue()));
    column.setCellFactory(TextFieldTableCell.forTableColumn());
    column.setOnEditCommit(e -> prop.apply(e.getRowValue()).set(e.getNewValue()));
  }

  /** Loads the satellites saved for {@code entityName} in config.json into the editor table. */
  private void loadSatellitesForEntity(String entityName) {
    satelliteRows.clear();
    if (entityName == null || entityName.isBlank() || store == null) {
      return;
    }
    try {
      var config = store.load();
      var entries = config.getSatellites() == null ? null : config.getSatellites().get(entityName);
      if (entries == null) {
        return;
      }
      for (var e : entries) {
        satelliteRows.add(
            new SatelliteRow(
                nullToEmpty(e.getSourceConn()),
                nullToEmpty(e.getTargetConn()),
                nullToEmpty(e.getTable()),
                nullToEmpty(e.getLinkColumn()),
                nullToEmpty(e.getPkColumn())));
      }
    } catch (Exception ex) {
      vm.appendLog("Satellite tanımları yüklenemedi: " + ex.getMessage());
    }
  }

  @FXML
  void onAddSatellite() {
    satelliteRows.add(new SatelliteRow("", "", "", "beyanname_id", ""));
  }

  @FXML
  void onRemoveSatellite() {
    var sel = tblSatellites.getSelectionModel().getSelectedItem();
    if (sel != null) {
      satelliteRows.remove(sel);
    }
  }

  /** Persists the current satellite rows to config.json under the selected root table name. */
  @FXML
  void onSaveSatellites() {
    var entityName = cmbRootTable.getValue();
    if (entityName == null || entityName.isBlank()) {
      showAlert("Önce başlangıç tablosunu (root table) seçin.");
      return;
    }
    try {
      var config = store.load();
      var entries = new ArrayList<io.recordrelay.cli.config.SatelliteEntry>();
      for (var row : satelliteRows) {
        if (row.tableProperty().get().isBlank() || row.linkColumnProperty().get().isBlank()) {
          continue;
        }
        var entry = new io.recordrelay.cli.config.SatelliteEntry();
        entry.setSourceConn(row.sourceConnProperty().get().trim());
        entry.setTargetConn(row.targetConnProperty().get().trim());
        entry.setTable(row.tableProperty().get().trim());
        entry.setLinkColumn(row.linkColumnProperty().get().trim());
        var pk = row.pkColumnProperty().get().trim();
        entry.setPkColumn(pk.isBlank() ? null : pk);
        entries.add(entry);
      }
      if (entries.isEmpty()) {
        config.getSatellites().remove(entityName);
      } else {
        config.getSatellites().put(entityName, entries);
      }
      store.save(config);
      vm.appendLog("Satellite tanımları kaydedildi: " + entityName + " (" + entries.size() + ")");
    } catch (Exception ex) {
      showError("Satellite tanımları kaydedilemedi: " + ex.getMessage());
    }
  }

  /** Builds a {@link io.recordrelay.core.clone.domain.SatelliteConfig} from the editor rows. */
  private io.recordrelay.core.clone.domain.SatelliteConfig buildSatellites() throws Exception {
    var list = new ArrayList<io.recordrelay.core.clone.domain.SatelliteTable>();
    for (var row : satelliteRows) {
      var srcConn = row.sourceConnProperty().get().trim();
      var tgtConn = row.targetConnProperty().get().trim();
      var table = row.tableProperty().get().trim();
      var link = row.linkColumnProperty().get().trim();
      if (srcConn.isBlank() || tgtConn.isBlank() || table.isBlank() || link.isBlank()) {
        continue;
      }
      var pk = row.pkColumnProperty().get().trim();
      list.add(
          new io.recordrelay.core.clone.domain.SatelliteTable(
              resolver.resolve(srcConn),
              resolver.resolve(tgtConn),
              table,
              link,
              pk.isBlank() ? null : pk));
    }
    return list.isEmpty()
        ? io.recordrelay.core.clone.domain.SatelliteConfig.none()
        : new io.recordrelay.core.clone.domain.SatelliteConfig(list);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  /** Parses the optional Start-ID field; returns {@code null} when blank or non-numeric. */
  private Long parseIdStart() {
    if (tfIdStart == null || tfIdStart.getText() == null || tfIdStart.getText().isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(tfIdStart.getText().trim());
    } catch (NumberFormatException e) {
      vm.appendLog("Start ID sayısal değil, yok sayıldı: " + tfIdStart.getText());
      return null;
    }
  }

  // ── Table loading ──────────────────────────────────────────────────────────

  @FXML
  void onSourceChanged() {
    var connName = cmbSource.getValue();
    if (connName == null || connName.isBlank()) {
      return;
    }
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
                var tableNames = tables.stream().map(t -> t.tableName()).sorted().toList();

                Platform.runLater(
                    () -> {
                      cmbRootTable.setItems(FXCollections.observableArrayList(tableNames));
                      lblTableCount.setText(tableNames.size() + " tablo");
                      btnLoadTables.setDisable(false);
                      btnDetectRoot.setDisable(tableNames.isEmpty());
                      rootSuggestPanel.setVisible(false);
                      rootSuggestPanel.setManaged(false);
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

  @FXML
  void onDetectRoot() {
    var connName = cmbSource.getValue();
    if (connName == null || connName.isBlank()) {
      return;
    }
    btnDetectRoot.setDisable(true);
    btnDetectRoot.setText(Messages.get("cc.btn.detect") + "…");
    rootSuggestPanel.setVisible(false);
    rootSuggestPanel.setManaged(false);

    new Thread(
            () -> {
              try {
                var profile = resolver.resolve(connName);
                var connector = ConnectorRegistry.findConnector(profile);
                var dbRef = new DatabaseRef(profile.database(), profile.type());
                var candidates = connector.schemaInspector().detectRootCandidates(profile, dbRef);
                Platform.runLater(() -> showRootSuggestions(candidates));
              } catch (Exception e) {
                Platform.runLater(
                    () -> {
                      btnDetectRoot.setDisable(false);
                      btnDetectRoot.setText(Messages.get("cc.btn.detect"));
                      showError(e.getMessage());
                    });
              }
            },
            "rr-detect-root")
        .start();
  }

  private void showRootSuggestions(List<RootTableCandidate> candidates) {
    btnDetectRoot.setDisable(false);
    btnDetectRoot.setText(Messages.get("cc.btn.detect"));

    if (candidates.isEmpty()) {
      showError(Messages.get("cc.detect.no.candidates"));
      return;
    }

    rootSuggestFlow.getChildren().clear();
    for (RootTableCandidate c : candidates) {
      var btn = new Button(c.badgeLabel());
      btn.setTooltip(new javafx.scene.control.Tooltip(c.reason()));
      btn.getStyleClass().add("nav-btn");
      btn.setOnAction(
          e -> {
            cmbRootTable.setValue(c.tableName());
            rootSuggestPanel.setVisible(false);
            rootSuggestPanel.setManaged(false);
          });
      rootSuggestFlow.getChildren().add(btn);
    }

    // Auto-select the top candidate
    cmbRootTable.setValue(candidates.get(0).tableName());
    rootSuggestPanel.setVisible(true);
    rootSuggestPanel.setManaged(true);
  }

  // ── Action handlers ────────────────────────────────────────────────────────

  @FXML
  void onDryRun() {
    var err = validate(chkExportMode.isSelected());
    if (err != null) {
      showAlert(err);
      return;
    }
    btnDryRun.setDisable(true);
    btnDryRun.setText("Önizleniyor…");
    dryRunPanel.setVisible(false);
    dryRunPanel.setManaged(false);

    new Thread(this::runDryRun, "rr-dry-run").start();
  }

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
  void onAddOverride() {
    overrideRows.add(new OverrideRow("", "", ""));
  }

  @FXML
  void onRemoveOverride() {
    var sel = tblOverrides.getSelectionModel().getSelectedItem();
    if (sel != null) {
      overrideRows.remove(sel);
    }
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

      var conflict =
          cmbConflict.getValue() != null
              ? cmbConflict.getValue()
              : ConflictResolution.REGENERATE_IDENTITIES;
      var satellites = buildSatellites();
      var plan =
          ContextClonePlan.liveCloneWithOverrides(
              entity,
              tfEntityId.getText().trim(),
              srcProfile,
              tgtProfile,
              depth,
              masking,
              buildFieldOverrides(),
              conflict,
              satellites,
              parseIdStart());
      if (!satellites.isEmpty()) {
        vm.appendLog("Uydu tablolar (satellite): " + satellites.satellites().size() + " tanım");
      }

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
      logCloneReport(report);
    } catch (Exception e) {
      vm.appendLog("HATA: " + e.getMessage());
      vm.markFailed(e.getMessage());
    }
  }

  private void logCloneReport(io.recordrelay.core.clone.domain.CloneReport report) {
    vm.appendLog("\nTamamlandı:");
    vm.appendLog("  Tablo sayısı : " + report.tableCount());
    vm.appendLog("  Kayıt sayısı : " + report.totalRecords());
    vm.appendLog("  Süre         : " + report.formattedDuration());
    if (report.maskedFieldCount() > 0) {
      vm.appendLog("  Maskelenen   : " + report.maskedFieldCount() + " alan");
    }
    if (!report.warnings().isEmpty()) {
      vm.appendLog("\nUYARILAR:");
      report.warnings().forEach(w -> vm.appendLog("  ⚠ " + w));
    }
    vm.markComplete(
        "Tamamlandı — "
            + report.totalRecords()
            + " kayıt kopyalandı"
            + (report.warnings().isEmpty() ? "" : " (" + report.warnings().size() + " uyarı)"));
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

  // ── Dry run ────────────────────────────────────────────────────────────────

  private void runDryRun() {
    try {
      var entity = buildEntity();
      var srcProfile = resolver.resolve(cmbSource.getValue());
      int depth = (int) sliderDepth.getValue();

      // Use source as dummy target — dry run never writes
      var plan =
          ContextClonePlan.liveClone(
              entity,
              tfEntityId.getText().trim(),
              srcProfile,
              srcProfile,
              depth,
              MaskingConfig.none());

      var engine = DefaultContextCloneEngine.createDefault();
      DryRunReport report = engine.dryRunContext(plan);
      Platform.runLater(() -> showDryRunReport(report));
    } catch (Exception e) {
      Platform.runLater(
          () -> {
            btnDryRun.setDisable(false);
            btnDryRun.setText("Önizle");
            showError("Önizleme hatası: " + e.getMessage());
          });
    }
  }

  private void showDryRunReport(DryRunReport report) {
    tblDryRun.getItems().setAll(report.tables());
    lblDryRunSummary.setText(
        report.tableCount()
            + " tablo  •  "
            + report.totalRows()
            + " satır  •  "
            + report.durationMillis()
            + " ms");
    dryRunPanel.setVisible(true);
    dryRunPanel.setManaged(true);
    btnDryRun.setDisable(false);
    btnDryRun.setText("Önizle");
  }

  private void bindDryRunTable() {
    if (colDryTable == null) {
      return;
    }
    colDryTable.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().tableName()));
    colDryRows.setCellValueFactory(
        cd -> new SimpleStringProperty(String.valueOf(cd.getValue().rowCount())));
    colDryDepth.setCellValueFactory(
        cd -> new SimpleStringProperty(String.valueOf(cd.getValue().minDepth())));
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private BusinessEntity buildEntity() {
    var rootTable = cmbRootTable.getValue();
    if (rootTable == null || rootTable.isBlank()) {
      rootTable = "records";
    }
    var pk = tfPkColumn.getText().trim();
    if (pk.isBlank()) {
      pk = "id";
    }
    return BusinessEntity.of(rootTable, rootTable, pk, "");
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

  private FieldOverrideConfig buildFieldOverrides() {
    var list = new ArrayList<FieldOverride>();
    for (var row : overrideRows) {
      var col = row.columnProperty().get().trim();
      var val = row.valueProperty().get().trim();
      if (col.isBlank()) {
        continue;
      }
      var tbl = row.tableNameProperty().get().trim();
      list.add(
          tbl.isBlank() ? FieldOverride.global(col, val) : FieldOverride.forTable(tbl, col, val));
    }
    return new FieldOverrideConfig(list);
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

  /** Editable row model for the field-override table. */
  public static final class OverrideRow {
    private final SimpleStringProperty tableName;
    private final SimpleStringProperty column;
    private final SimpleStringProperty value;

    public OverrideRow(String tableName, String column, String value) {
      this.tableName = new SimpleStringProperty(tableName);
      this.column = new SimpleStringProperty(column);
      this.value = new SimpleStringProperty(value);
    }

    /** Returns the table-name property (empty string means global override). */
    public StringProperty tableNameProperty() {
      return tableName;
    }

    /** Returns the column-name property. */
    public StringProperty columnProperty() {
      return column;
    }

    /** Returns the override value property. */
    public StringProperty valueProperty() {
      return value;
    }
  }

  /** Editable row model for the satellite (companion table) editor. */
  public static final class SatelliteRow {
    private final SimpleStringProperty sourceConn;
    private final SimpleStringProperty targetConn;
    private final SimpleStringProperty table;
    private final SimpleStringProperty linkColumn;
    private final SimpleStringProperty pkColumn;

    public SatelliteRow(
        String sourceConn, String targetConn, String table, String linkColumn, String pkColumn) {
      this.sourceConn = new SimpleStringProperty(sourceConn);
      this.targetConn = new SimpleStringProperty(targetConn);
      this.table = new SimpleStringProperty(table);
      this.linkColumn = new SimpleStringProperty(linkColumn);
      this.pkColumn = new SimpleStringProperty(pkColumn);
    }

    /** Returns the source connection-name property. */
    public StringProperty sourceConnProperty() {
      return sourceConn;
    }

    /** Returns the target connection-name property. */
    public StringProperty targetConnProperty() {
      return targetConn;
    }

    /** Returns the companion table-name property. */
    public StringProperty tableProperty() {
      return table;
    }

    /** Returns the link-column property (references the root id). */
    public StringProperty linkColumnProperty() {
      return linkColumn;
    }

    /** Returns the primary-key column property (empty = auto-detect). */
    public StringProperty pkColumnProperty() {
      return pkColumn;
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
