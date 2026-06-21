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
import io.recordrelay.core.clone.domain.RelationshipGraph;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.spi.ConnectorRegistry;
import io.recordrelay.engine.clone.JdbcRelationshipResolver;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Controller for the Graph View screen. Discovers and renders table relationship graphs. */
public final class GraphViewController implements Refreshable {

  private static final Logger LOG = LoggerFactory.getLogger(GraphViewController.class);

  @FXML private ComboBox<String> cboSource;
  @FXML private ComboBox<String> cboRootTable;
  @FXML private Button btnDiscover;
  @FXML private Label lblStatus;
  @FXML private ScrollPane graphScroll;
  @FXML private RelationshipGraphCanvas graphCanvas;

  private ConfigStore configStore;
  private ConnProfileResolver resolver;

  @FXML
  void initialize() {
    try {
      configStore = new ConfigStore();
      resolver = new ConnProfileResolver(configStore);
    } catch (Exception e) {
      lblStatus.setText("Config unavailable: " + e.getMessage());
      btnDiscover.setDisable(true);
      return;
    }
    loadConnections();
    cboSource.setOnAction(e -> loadTables());
  }

  @Override
  public void refresh() {
    loadConnections();
  }

  @FXML
  void onDiscover() {
    var connName = cboSource.getValue();
    var rootTable = cboRootTable.getValue();
    if (connName == null || rootTable == null || rootTable.isBlank()) {
      lblStatus.setText("Select a connection and root table first.");
      return;
    }
    btnDiscover.setDisable(true);
    lblStatus.setText("Discovering relationships…");
    new Thread(() -> discoverGraph(connName, rootTable), "rr-graph").start();
  }

  private void loadConnections() {
    try {
      var config = configStore.load();
      var names = config.getConnections().keySet().stream().sorted().toList();
      cboSource.setItems(FXCollections.observableArrayList(names));
      if (!names.isEmpty() && cboSource.getValue() == null) {
        cboSource.setValue(names.get(0));
        loadTables();
      }
    } catch (Exception e) {
      lblStatus.setText("Could not load connections: " + e.getMessage());
    }
  }

  private void loadTables() {
    var connName = cboSource.getValue();
    if (connName == null) {
      return;
    }
    new Thread(
        () -> {
          try {
            var profile = resolver.resolve(connName);
            var connector = ConnectorRegistry.findConnector(profile);
            var dbRef = new io.recordrelay.core.domain.DatabaseRef(profile.database(), profile.type());
            var tables = connector.schemaInspector().listTables(profile, dbRef);
            var tableNames = tables.stream()
                .map(t -> t.tableName())
                .sorted()
                .toList();
            Platform.runLater(
                () -> {
                  cboRootTable.setItems(FXCollections.observableArrayList(tableNames));
                  if (!tableNames.isEmpty()) {
                    cboRootTable.setValue(tableNames.get(0));
                  }
                });
          } catch (Exception e) {
            Platform.runLater(() -> lblStatus.setText("Table load failed: " + e.getMessage()));
          }
        },
        "rr-table-load")
        .start();
  }

  private void discoverGraph(String connName, String rootTable) {
    try {
      var profile = resolver.resolve(connName);
      RelationshipGraph graph;
      try (var graphResolver = new JdbcRelationshipResolver()) {
        graph = graphResolver.resolve(profile, rootTable);
      }
      var finalGraph = graph;
      Platform.runLater(
          () -> {
            graphCanvas.render(finalGraph, rootTable);
            lblStatus.setText(
                String.format(
                    "Graph ready — %d tables, %d relationships",
                    finalGraph.nodeCount(),
                    finalGraph.edgeCount()));
            btnDiscover.setDisable(false);
          });
    } catch (Exception e) {
      LOG.warn("Graph discovery failed", e);
      Platform.runLater(
          () -> {
            lblStatus.setText("Discovery failed: " + e.getMessage());
            btnDiscover.setDisable(false);
          });
    }
  }
}
