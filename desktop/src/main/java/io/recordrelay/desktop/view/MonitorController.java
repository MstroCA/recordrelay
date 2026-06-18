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
import io.recordrelay.core.spi.ConnectorRegistry;
import io.recordrelay.desktop.viewmodel.MonitorViewModel;
import io.recordrelay.desktop.viewmodel.MonitorViewModel.HealthEntry;
import java.util.ArrayList;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/** Controller for the Dashboard screen — shows live transfer metrics and DB health. */
public final class MonitorController implements Refreshable {

  @FXML private Label lblTransferred;
  @FXML private Label lblFailed;
  @FXML private Label lblThroughput;
  @FXML private Label lblDuration;
  @FXML private Label lblP50;
  @FXML private Label lblP95;
  @FXML private Label lblP99;

  @FXML private LineChart<Number, Number> chart;

  @FXML private TableView<HealthEntry> tblHealth;
  @FXML private TableColumn<HealthEntry, String> colConnName;
  @FXML private TableColumn<HealthEntry, String> colStatus;
  @FXML private TableColumn<HealthEntry, String> colDetail;

  @FXML private Label lblHealthStatus;
  @FXML private Button btnRefresh;

  private MonitorViewModel vm;
  private ConfigStore store;

  @FXML
  void initialize() {
    vm = new MonitorViewModel();
    try {
      store = new ConfigStore();
    } catch (Exception e) {
      lblHealthStatus.setText("Config unavailable: " + e.getMessage());
      btnRefresh.setDisable(true);
    }
    bindKpis();
    bindChart();
    bindHealthTable();
  }

  /** No-op — the dashboard is push-only; data arrives from the Transfer screen. */
  @Override
  public void refresh() {}

  @FXML
  void onRefreshHealth() {
    if (store == null) {
      return;
    }
    btnRefresh.setDisable(true);
    lblHealthStatus.setText("Checking…");
    vm.healthIndicators().clear();
    new Thread(this::runHealthCheck, "rr-health").start();
  }

  @FXML
  void onClear() {
    vm.reset();
    lblHealthStatus.setText("● Ready");
  }

  private void runHealthCheck() {
    var results = new ArrayList<HealthEntry>();
    try {
      var config = store.load();
      var resolver = new ConnProfileResolver(store);
      for (var conn : config.getConnections().entrySet()) {
        results.add(checkSingleConnection(resolver, conn.getKey()));
      }
      Platform.runLater(
          () -> {
            vm.healthIndicators().setAll(results);
            lblHealthStatus.setText("● Ready");
            btnRefresh.setDisable(false);
          });
    } catch (Exception e) {
      Platform.runLater(
          () -> {
            lblHealthStatus.setText("Error: " + e.getMessage());
            btnRefresh.setDisable(false);
          });
    }
  }

  private HealthEntry checkSingleConnection(ConnProfileResolver resolver, String name) {
    try {
      var profile = resolver.resolve(name);
      ConnectorRegistry.findConnector(profile).testConnection(profile);
      return new HealthEntry(name, true, "Reachable");
    } catch (Exception e) {
      String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      return new HealthEntry(name, false, detail);
    }
  }

  private void bindKpis() {
    lblTransferred.textProperty().bind(Bindings.format("%,d", vm.transferredCountProperty()));
    lblFailed.textProperty().bind(Bindings.format("%,d", vm.failedCountProperty()));
    lblThroughput.textProperty().bind(Bindings.format("%,d rec/s", vm.throughputNowProperty()));
    lblDuration.textProperty().bind(vm.durationProperty());
    lblP50.textProperty().bind(vm.latencyP50Property());
    lblP95.textProperty().bind(vm.latencyP95Property());
    lblP99.textProperty().bind(vm.latencyP99Property());
  }

  private void bindChart() {
    chart.setAnimated(false);
    chart.getData().add(vm.throughputSeries());
  }

  private void bindHealthTable() {
    colConnName.setCellValueFactory(r -> new ReadOnlyStringWrapper(r.getValue().name()));
    colStatus.setCellValueFactory(
        r -> new ReadOnlyStringWrapper(r.getValue().reachable() ? "✓  OK" : "✗  Error"));
    colStatus.setCellFactory(col -> new HealthStatusCell());
    colDetail.setCellValueFactory(r -> new ReadOnlyStringWrapper(r.getValue().detail()));
    tblHealth.setItems(vm.healthIndicators());
  }

  private static final class HealthStatusCell extends TableCell<HealthEntry, String> {
    @Override
    protected void updateItem(String item, boolean empty) {
      super.updateItem(item, empty);
      if (item == null || empty) {
        setText(null);
        setStyle("");
      } else if (item.startsWith("✓")) {
        setText(item);
        setStyle("-fx-text-fill: -color-success-fg; -fx-font-weight: bold;");
      } else {
        setText(item);
        setStyle("-fx-text-fill: -color-danger-fg; -fx-font-weight: bold;");
      }
    }
  }
}
