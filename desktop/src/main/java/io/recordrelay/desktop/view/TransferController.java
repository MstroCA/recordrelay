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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.recordrelay.cli.config.ConfigStore;
import io.recordrelay.cli.engine.ConnProfileResolver;
import io.recordrelay.core.domain.ColumnMapping;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.MappingFormat;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.domain.TransactionIsolation;
import io.recordrelay.core.domain.TransferJob;
import io.recordrelay.core.domain.TransferMode;
import io.recordrelay.core.domain.TransferOptions;
import io.recordrelay.core.domain.TransferResult;
import io.recordrelay.core.engine.DefaultTransferEngine;
import io.recordrelay.core.engine.SyncPipeline;
import io.recordrelay.core.port.out.TransferProgressListener;
import io.recordrelay.desktop.service.SchemaDiscoveryService;
import io.recordrelay.desktop.viewmodel.TransferViewModel;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** Controller for the Transfer screen — cascading DB/table discovery, column mapping editor. */
public final class TransferController implements Refreshable {

  // ── Connection selectors ───────────────────────────────────────────────────
  @FXML private ComboBox<String> cmbSrcConn;
  @FXML private ComboBox<String> cmbTgtConn;

  // ── Source discovery ───────────────────────────────────────────────────────
  @FXML private ComboBox<String> cmbSrcDb;
  @FXML private ComboBox<String> cmbSrcTable;
  @FXML private Label lblSrcDbLoading;
  @FXML private Label lblSrcTblLoading;

  // ── Target discovery ───────────────────────────────────────────────────────
  @FXML private ComboBox<String> cmbTgtDb;
  @FXML private ComboBox<String> cmbTgtTable;
  @FXML private Label lblTgtDbLoading;
  @FXML private Label lblTgtTblLoading;
  @FXML private Label lblTgtNotFound;

  // ── Column mapping ─────────────────────────────────────────────────────────
  @FXML private VBox paneColumnMapping;
  @FXML private TableView<ColumnMappingRow> tblColumns;
  @FXML private TableColumn<ColumnMappingRow, Boolean> colEnabled;
  @FXML private TableColumn<ColumnMappingRow, String> colSrcCol;
  @FXML private TableColumn<ColumnMappingRow, String> colSrcType;
  @FXML private TableColumn<ColumnMappingRow, String> colTgtCol;
  @FXML private TableColumn<ColumnMappingRow, String> colTransform;
  @FXML private TableColumn<ColumnMappingRow, String> colOverride;

  // ── Job settings ──────────────────────────────────────────────────────────
  @FXML private ComboBox<String> cmbMode;
  @FXML private ComboBox<String> cmbIsolation;
  @FXML private TextField tfChunkSize;
  @FXML private TextField tfMappingFile;

  // ── Progress ──────────────────────────────────────────────────────────────
  @FXML private ProgressBar progressBar;
  @FXML private Label lblStatus;
  @FXML private Label lblTransferred;
  @FXML private Label lblError;
  @FXML private Button btnStart;
  @FXML private Button btnCancel;

  private TransferViewModel vm;
  private ConfigStore store;
  private ConnProfileResolver resolver;
  private SchemaDiscoveryService discovery;
  private volatile boolean cancelRequested = false;

  // Cached resolved profiles and table lists for the current combo selection
  private ConnectionProfile srcProfile;
  private ConnectionProfile tgtProfile;
  private List<TableRef> srcTableRefs = List.of();
  private List<TableRef> tgtTableRefs = List.of();

  @FXML
  void initialize() {
    try {
      store = new ConfigStore();
      resolver = new ConnProfileResolver(store);
      vm = new TransferViewModel();
      discovery = new SchemaDiscoveryService();
    } catch (Exception e) {
      showControllerError("Config init failed: " + e.getMessage());
      return;
    }
    setupColumnTable();
    bindJobSettings();
    bindProgress();
    bindCascade();
    loadConnections();
  }

  @Override
  public void refresh() {
    if (store != null) {
      loadConnections();
    }
  }

  // ── Cascade wiring ─────────────────────────────────────────────────────────

  private void bindCascade() {
    cmbSrcConn.valueProperty().addListener((obs, old, v) -> onSrcConnChanged(v));
    cmbSrcDb.valueProperty().addListener((obs, old, v) -> onSrcDbChanged(v));
    cmbSrcTable.valueProperty().addListener((obs, old, v) -> onSrcTableChanged(v));

    cmbTgtConn.valueProperty().addListener((obs, old, v) -> onTgtConnChanged(v));
    cmbTgtDb.valueProperty().addListener((obs, old, v) -> onTgtDbChanged(v));
    cmbTgtTable.valueProperty().addListener((obs, old, v) -> onTgtTableChanged(v));
  }

  private void onSrcConnChanged(String connName) {
    clearSrcBelow(true);
    if (connName == null || connName.isBlank()) {
      return;
    }
    try {
      srcProfile = resolver.resolve(connName);
    } catch (Exception e) {
      showError("Cannot resolve source connection: " + e.getMessage());
      return;
    }
    setLoading(lblSrcDbLoading, true);
    discovery.loadDatabases(
        srcProfile,
        dbs -> {
          setLoading(lblSrcDbLoading, false);
          cmbSrcDb.getItems().setAll(dbs.stream().map(DatabaseRef::name).toList());
          cmbSrcDb.setDisable(false);
          if (cmbSrcDb.getItems().size() == 1) {
            cmbSrcDb.setValue(cmbSrcDb.getItems().get(0));
          }
        },
        err -> {
          setLoading(lblSrcDbLoading, false);
          showError("Could not load source databases: " + err);
        });
  }

  private void onSrcDbChanged(String dbName) {
    clearSrcBelow(false);
    if (dbName == null || dbName.isBlank() || srcProfile == null) {
      return;
    }
    var db = new DatabaseRef(dbName, srcProfile.type());
    setLoading(lblSrcTblLoading, true);
    discovery.loadTables(
        srcProfile,
        db,
        tables -> {
          setLoading(lblSrcTblLoading, false);
          srcTableRefs = tables;
          cmbSrcTable.getItems().setAll(tables.stream().map(TableRef::tableName).toList());
          cmbSrcTable.setDisable(false);
        },
        err -> {
          setLoading(lblSrcTblLoading, false);
          showError("Could not load source tables: " + err);
        });
  }

  private void onSrcTableChanged(String tableName) {
    hideColumnMapping();
    if (tableName == null || tableName.isBlank() || srcProfile == null) {
      return;
    }
    String srcDb = cmbSrcDb.getValue();
    var tableRef =
        srcTableRefs.stream()
            .filter(t -> t.tableName().equals(tableName))
            .findFirst()
            .orElseGet(
                () ->
                    new TableRef(
                        new DatabaseRef(
                            srcDb != null ? srcDb : srcProfile.database(), srcProfile.type()),
                        srcDb != null ? srcDb : "",
                        tableName));
    discovery.loadColumns(
        srcProfile,
        tableRef,
        cols -> {
          tblColumns
              .getItems()
              .setAll(
                  cols.stream().map(c -> new ColumnMappingRow(c.name(), c.nativeType())).toList());
          showColumnMapping();
        },
        err -> showError("Could not load source columns: " + err));
  }

  private void onTgtConnChanged(String connName) {
    clearTgtBelow(true);
    if (connName == null || connName.isBlank()) {
      return;
    }
    try {
      tgtProfile = resolver.resolve(connName);
    } catch (Exception e) {
      showError("Cannot resolve target connection: " + e.getMessage());
      return;
    }
    setLoading(lblTgtDbLoading, true);
    discovery.loadDatabases(
        tgtProfile,
        dbs -> {
          setLoading(lblTgtDbLoading, false);
          cmbTgtDb.getItems().setAll(dbs.stream().map(DatabaseRef::name).toList());
          cmbTgtDb.setDisable(false);
          if (cmbTgtDb.getItems().size() == 1) {
            cmbTgtDb.setValue(cmbTgtDb.getItems().get(0));
          }
        },
        err -> {
          setLoading(lblTgtDbLoading, false);
          showError("Could not load target databases: " + err);
        });
  }

  private void onTgtDbChanged(String dbName) {
    clearTgtBelow(false);
    if (dbName == null || dbName.isBlank() || tgtProfile == null) {
      return;
    }
    var db = new DatabaseRef(dbName, tgtProfile.type());
    setLoading(lblTgtTblLoading, true);
    discovery.loadTables(
        tgtProfile,
        db,
        tables -> {
          setLoading(lblTgtTblLoading, false);
          tgtTableRefs = tables;
          cmbTgtTable.getItems().setAll(tables.stream().map(TableRef::tableName).toList());
          cmbTgtTable.setDisable(false);
        },
        err -> {
          setLoading(lblTgtTblLoading, false);
          showError("Could not load target tables: " + err);
        });
  }

  private void onTgtTableChanged(String tableName) {
    setVisible(lblTgtNotFound, false);
    if (tableName == null || tableName.isBlank()) {
      return;
    }
    boolean found = tgtTableRefs.stream().anyMatch(t -> t.tableName().equalsIgnoreCase(tableName));
    if (!found) {
      lblTgtNotFound.setText(
          "⚠ Table \""
              + tableName
              + "\" not found in target — it will be created on the first transfer.");
      setVisible(lblTgtNotFound, true);
    }
  }

  // ── Column mapping TableView setup ─────────────────────────────────────────

  private void setupColumnTable() {
    tblColumns.setEditable(true);

    colEnabled.setCellValueFactory(d -> d.getValue().enabledProperty());
    colEnabled.setCellFactory(CheckBoxTableCell.forTableColumn(colEnabled));

    colSrcCol.setCellValueFactory(d -> d.getValue().sourceColumnProperty());
    colSrcType.setCellValueFactory(d -> d.getValue().sourceTypeProperty());

    colTgtCol.setCellValueFactory(d -> d.getValue().targetColumnProperty());
    colTgtCol.setCellFactory(TextFieldTableCell.forTableColumn());
    colTgtCol.setOnEditCommit(e -> e.getRowValue().targetColumnProperty().set(e.getNewValue()));

    colTransform.setCellValueFactory(d -> d.getValue().transformProperty());
    colTransform.setCellFactory(TextFieldTableCell.forTableColumn());
    colTransform.setOnEditCommit(e -> e.getRowValue().transformProperty().set(e.getNewValue()));

    colOverride.setCellValueFactory(d -> d.getValue().overrideValueProperty());
    colOverride.setCellFactory(TextFieldTableCell.forTableColumn());
    colOverride.setOnEditCommit(e -> e.getRowValue().overrideValueProperty().set(e.getNewValue()));
  }

  // ── Action handlers ────────────────────────────────────────────────────────

  private String validateStartInputs(String srcTable, String tgtTable) {
    String srcConn = cmbSrcConn.getValue();
    String tgtConn = cmbTgtConn.getValue();
    if (srcConn == null || srcConn.isBlank() || tgtConn == null || tgtConn.isBlank()) {
      return "Source and target connections are required.";
    }
    if (srcTable == null || srcTable.isBlank()) {
      return "Please select a source table.";
    }
    if (tgtTable == null || tgtTable.isBlank()) {
      return "Please specify a target table.";
    }
    return null;
  }

  private boolean confirmNewTargetTable(String tgtTable) {
    boolean isNew = tgtTableRefs.stream().noneMatch(t -> t.tableName().equalsIgnoreCase(tgtTable));
    if (!isNew) {
      return true;
    }
    var alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle("Create table?");
    alert.setHeaderText(null);
    alert.setContentText(
        "Table \""
            + tgtTable
            + "\" does not exist in target.\n\n"
            + "The engine will attempt to create it automatically on the first transfer.\n\n"
            + "Continue?");
    var btn = alert.showAndWait();
    return btn.isPresent() && btn.get() == ButtonType.OK;
  }

  @FXML
  void onStart() {
    String srcTable = cmbSrcTable.getValue();
    String tgtTable = resolvedTgtTableName();
    String validationError = validateStartInputs(srcTable, tgtTable);
    if (validationError != null) {
      showAlert(validationError);
      return;
    }
    if (!confirmNewTargetTable(tgtTable)) {
      return;
    }
    cancelRequested = false;
    vm.resetProgress();
    btnStart.setDisable(true);
    btnCancel.setDisable(false);
    try {
      var job = buildJob(srcTable, tgtTable);
      var engine = new DefaultTransferEngine(new SyncPipeline(), null);
      new Thread(() -> runTransfer(job, engine), "rr-transfer").start();
    } catch (Exception e) {
      vm.markFailed(e.getMessage());
      btnStart.setDisable(false);
      btnCancel.setDisable(true);
    }
  }

  @FXML
  void onCancel() {
    cancelRequested = true;
    btnCancel.setDisable(true);
    lblStatus.setText("Cancelling…");
  }

  @FXML
  void onBrowseMapping() {
    var fc = new FileChooser();
    fc.setTitle("Select Mapping File");
    fc.getExtensionFilters()
        .add(new FileChooser.ExtensionFilter("Mapping files", "*.json", "*.yaml", "*.yml"));
    var file = fc.showOpenDialog(btnStart.getScene().getWindow());
    if (file != null) {
      vm.mappingFileProperty().set(file.getAbsolutePath());
    }
  }

  // ── Transfer execution ─────────────────────────────────────────────────────

  private void runTransfer(TransferJob job, DefaultTransferEngine engine) {
    try {
      engine.transfer(job, buildListener());
    } catch (Exception e) {
      vm.markFailed(e.getMessage());
    } finally {
      javafx.application.Platform.runLater(
          () -> {
            btnStart.setDisable(false);
            btnCancel.setDisable(true);
          });
    }
  }

  private TransferJob buildJob(String srcTableName, String tgtTableName) throws Exception {
    var srcP = srcProfile != null ? srcProfile : resolver.resolve(cmbSrcConn.getValue());
    var tgtP = tgtProfile != null ? tgtProfile : resolver.resolve(cmbTgtConn.getValue());

    String srcDbName = cmbSrcDb.getValue();
    String tgtDbName = cmbTgtDb.getValue();

    var srcDbRef = new DatabaseRef(srcDbName != null ? srcDbName : srcP.database(), srcP.type());
    var tgtDbRef = new DatabaseRef(tgtDbName != null ? tgtDbName : tgtP.database(), tgtP.type());

    var srcRef = new TableRef(srcDbRef, srcDbName != null ? srcDbName : "", srcTableName);
    var tgtRef = new TableRef(tgtDbRef, tgtDbName != null ? tgtDbName : "", tgtTableName);

    var colMappings = buildColumnMappings();
    var mapping =
        new MappingDefinition(
            UUID.randomUUID().toString(), srcRef, tgtRef, colMappings, MappingFormat.DIRECT, null);
    var mode = TransferMode.valueOf(vm.modeProperty().get());
    var isolation = TransactionIsolation.valueOf(vm.isolationProperty().get());
    var batchSize = (int) vm.chunkSizeProperty().get();
    return new TransferJob(
        UUID.randomUUID().toString(),
        "desktop-transfer",
        srcP,
        tgtP,
        mapping,
        mode,
        batchSize,
        isolation,
        TransferOptions.defaults());
  }

  private List<ColumnMapping> buildColumnMappings() throws Exception {
    String path = vm.mappingFileProperty().get();
    if (path != null && !path.isBlank()) {
      return loadColumnMappingsFromFile(path);
    }
    var items = tblColumns.getItems();
    if (items.isEmpty()) {
      return List.of();
    }
    return items.stream()
        .filter(ColumnMappingRow::isEnabled)
        .map(ColumnMappingRow::toColumnMapping)
        .toList();
  }

  @SuppressWarnings("unchecked")
  private List<ColumnMapping> loadColumnMappingsFromFile(String path) throws Exception {
    var file = new File(path);
    if (!file.exists()) {
      return List.of();
    }
    var mapper = new ObjectMapper();
    var type = mapper.getTypeFactory().constructCollectionType(List.class, Map.class);
    List<Map<String, String>> rows = mapper.readValue(file, type);
    var result = new ArrayList<ColumnMapping>();
    for (var m : rows) {
      result.add(new ColumnMapping(m.get("source"), m.get("target"), null));
    }
    return result;
  }

  private TransferProgressListener buildListener() {
    return new TransferProgressListener() {
      @Override
      public void onStart(TransferJob j) {}

      @Override
      public void onProgress(TransferJob j, long done, long total) {
        vm.updateProgress(done, total, 0);
      }

      @Override
      public void onComplete(TransferResult result) {
        vm.markComplete(result.transferredCount());
      }

      @Override
      public void onError(TransferJob j, Exception e) {
        vm.markFailed(e.getMessage());
      }
    };
  }

  // ── Binding helpers ────────────────────────────────────────────────────────

  private void bindJobSettings() {
    tfMappingFile.textProperty().bindBidirectional(vm.mappingFileProperty());
    cmbMode.setItems(FXCollections.observableArrayList("SYNC", "ASYNC", "BATCH"));
    cmbMode.valueProperty().bindBidirectional(vm.modeProperty());
    cmbIsolation.setItems(
        FXCollections.observableArrayList(
            "READ_UNCOMMITTED", "READ_COMMITTED", "REPEATABLE_READ", "SERIALIZABLE"));
    cmbIsolation.valueProperty().bindBidirectional(vm.isolationProperty());
    tfChunkSize
        .textProperty()
        .addListener(
            (obs, o, n) -> {
              try {
                vm.chunkSizeProperty().set(Long.parseLong(n.trim()));
              } catch (NumberFormatException ignored) {
              }
            });
    tfChunkSize.setText(String.valueOf(vm.chunkSizeProperty().get()));
  }

  private void bindProgress() {
    progressBar.progressProperty().bind(vm.progressProperty());
    lblStatus.textProperty().bind(vm.statusTextProperty());
    lblTransferred
        .textProperty()
        .bind(Bindings.format("Transferred: %,d records", vm.transferredProperty()));
    lblError.textProperty().bind(vm.errorProperty());
    lblError.visibleProperty().bind(vm.errorProperty().isNotEmpty());
    lblError.managedProperty().bind(vm.errorProperty().isNotEmpty());
    btnCancel.setDisable(true);
  }

  private void loadConnections() {
    try {
      var names = FXCollections.observableArrayList(store.load().getConnections().keySet());
      cmbSrcConn.setItems(names);
      cmbTgtConn.setItems(FXCollections.observableArrayList(names));
    } catch (Exception e) {
      showControllerError("Failed to load connections: " + e.getMessage());
    }
  }

  // ── State helpers ──────────────────────────────────────────────────────────

  private void clearSrcBelow(boolean includeDb) {
    if (includeDb) {
      cmbSrcDb.getItems().clear();
      cmbSrcDb.setValue(null);
      cmbSrcDb.setDisable(true);
      srcProfile = null;
    }
    cmbSrcTable.getItems().clear();
    cmbSrcTable.setValue(null);
    cmbSrcTable.setDisable(true);
    srcTableRefs = List.of();
    hideColumnMapping();
  }

  private void clearTgtBelow(boolean includeDb) {
    if (includeDb) {
      cmbTgtDb.getItems().clear();
      cmbTgtDb.setValue(null);
      cmbTgtDb.setDisable(true);
      tgtProfile = null;
    }
    cmbTgtTable.getItems().clear();
    cmbTgtTable.setValue(null);
    cmbTgtTable.setDisable(true);
    tgtTableRefs = List.of();
    setVisible(lblTgtNotFound, false);
  }

  private void showColumnMapping() {
    paneColumnMapping.setVisible(true);
    paneColumnMapping.setManaged(true);
  }

  private void hideColumnMapping() {
    paneColumnMapping.setVisible(false);
    paneColumnMapping.setManaged(false);
    tblColumns.getItems().clear();
  }

  private void setLoading(Label lbl, boolean loading) {
    lbl.setVisible(loading);
    lbl.setManaged(loading);
  }

  private void setVisible(Label lbl, boolean visible) {
    lbl.setVisible(visible);
    lbl.setManaged(visible);
  }

  private String resolvedTgtTableName() {
    String value = cmbTgtTable.getValue();
    if (value != null && !value.isBlank()) {
      return value;
    }
    return cmbTgtTable.getEditor().getText();
  }

  private void showError(String message) {
    vm.reportError(message);
  }

  private void showControllerError(String message) {
    if (lblError != null) {
      lblError.setText(message);
      lblError.setVisible(true);
      lblError.setManaged(true);
    }
  }

  private void showAlert(String message) {
    var alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle("Validation");
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }
}
