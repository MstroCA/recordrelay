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
import io.recordrelay.desktop.viewmodel.TransferViewModel;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

/** Controller for the Transfer screen — configures and executes ETL transfer jobs. */
public final class TransferController implements Refreshable {

  @FXML private ComboBox<String> cmbSrcConn;
  @FXML private ComboBox<String> cmbTgtConn;
  @FXML private TextField tfSrcTable;
  @FXML private TextField tfTgtTable;
  @FXML private TextField tfMappingFile;
  @FXML private ComboBox<String> cmbMode;
  @FXML private ComboBox<String> cmbIsolation;
  @FXML private TextField tfChunkSize;
  @FXML private ProgressBar progressBar;
  @FXML private Label lblStatus;
  @FXML private Label lblTransferred;
  @FXML private Label lblError;
  @FXML private Button btnStart;
  @FXML private Button btnCancel;

  private TransferViewModel vm;
  private ConfigStore store;
  private ConnProfileResolver resolver;
  private volatile boolean cancelRequested = false;

  @FXML
  void initialize() {
    try {
      store = new ConfigStore();
      resolver = new ConnProfileResolver(store);
      vm = new TransferViewModel();
    } catch (Exception e) {
      lblError.setText("Config init failed: " + e.getMessage());
      lblError.setVisible(true);
      lblError.setManaged(true);
      return;
    }
    bindForm();
    bindProgress();
    loadConnections();
  }

  /** Reloads connection list; called each time this screen is shown. */
  @Override
  public void refresh() {
    if (store != null) {
      loadConnections();
    }
  }

  @FXML
  void onStart() {
    if (vm.sourceConnProperty().get().isEmpty() || vm.targetConnProperty().get().isEmpty()) {
      showErrorAlert("Source and target connections are required.");
      return;
    }
    cancelRequested = false;
    vm.resetProgress();
    btnStart.setDisable(true);
    btnCancel.setDisable(false);
    try {
      var job = buildJob();
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

  private void runTransfer(TransferJob job, DefaultTransferEngine engine) {
    try {
      engine.transfer(job, buildListener(job));
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

  private TransferJob buildJob() throws Exception {
    var srcProfile = resolver.resolve(vm.sourceConnProperty().get());
    var tgtProfile = resolver.resolve(vm.targetConnProperty().get());
    var srcRef = parseTableRef(vm.sourceTableProperty().get(), srcProfile);
    var tgtRef = parseTableRef(vm.targetTableProperty().get(), tgtProfile);
    var colMappings = loadColumnMappings();
    var mapping =
        new MappingDefinition(
            UUID.randomUUID().toString(), srcRef, tgtRef, colMappings, MappingFormat.DIRECT, null);
    var mode = TransferMode.valueOf(vm.modeProperty().get());
    var isolation = TransactionIsolation.valueOf(vm.isolationProperty().get());
    var batchSize = (int) vm.chunkSizeProperty().get();
    return new TransferJob(
        UUID.randomUUID().toString(),
        "desktop-transfer",
        srcProfile,
        tgtProfile,
        mapping,
        mode,
        batchSize,
        isolation,
        TransferOptions.defaults());
  }

  private TransferProgressListener buildListener(TransferJob job) {
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

  @SuppressWarnings("unchecked")
  private List<ColumnMapping> loadColumnMappings() throws Exception {
    String path = vm.mappingFileProperty().get();
    if (path == null || path.isBlank()) {
      return List.of();
    }
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

  private static TableRef parseTableRef(String tablePath, ConnectionProfile profile) {
    if (tablePath == null || tablePath.isBlank()) {
      throw new IllegalArgumentException("Table path must not be blank");
    }
    String[] parts = tablePath.trim().split("\\.", 2);
    if (parts.length == 2) {
      return new TableRef(new DatabaseRef(parts[0], profile.type()), "", parts[1]);
    }
    return new TableRef(new DatabaseRef(profile.database(), profile.type()), "", parts[0]);
  }

  private void bindForm() {
    cmbSrcConn.valueProperty().bindBidirectional(vm.sourceConnProperty());
    cmbTgtConn.valueProperty().bindBidirectional(vm.targetConnProperty());
    tfSrcTable.textProperty().bindBidirectional(vm.sourceTableProperty());
    tfTgtTable.textProperty().bindBidirectional(vm.targetTableProperty());
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
      lblError.setText("Failed to load connections: " + e.getMessage());
      lblError.setVisible(true);
      lblError.setManaged(true);
    }
  }

  private void showErrorAlert(String message) {
    var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
    alert.setTitle("Error");
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }
}
