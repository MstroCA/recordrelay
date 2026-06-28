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
import io.recordrelay.core.clone.domain.RelationshipEdge;
import io.recordrelay.core.clone.domain.RelationshipGraph;
import io.recordrelay.core.clone.domain.RelationshipNode;
import io.recordrelay.core.clone.domain.RelationshipSource;
import io.recordrelay.core.spi.ConnectorRegistry;
import io.recordrelay.desktop.graph.CustomRelationshipStore;
import io.recordrelay.engine.clone.JdbcRelationshipResolver;
import java.util.Optional;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Graph View screen. Discovers and renders ERD-style table relationship graphs,
 * and manages manually drawn relationships via the edit mode.
 */
public final class GraphViewController implements Refreshable {

  private static final Logger LOG = LoggerFactory.getLogger(GraphViewController.class);

  @FXML private ComboBox<String> cboSource;
  @FXML private ComboBox<String> cboRootTable;
  @FXML private Button btnDiscover;
  @FXML private Button btnEdit;
  @FXML private Label lblStatus;
  @FXML private ScrollPane graphScroll;
  @FXML private RelationshipGraphCanvas graphCanvas;
  @FXML private Slider sldZoom;

  private ConfigStore configStore;
  private ConnProfileResolver resolver;
  private final CustomRelationshipStore customStore = new CustomRelationshipStore();

  // Last discovered graph — kept so edit mode can re-render after adding/removing edges
  private RelationshipGraph discoveredGraph;
  private String currentConnName;
  private String currentRootTable;
  private boolean editMode = false;

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

    sldZoom.valueProperty().addListener((obs, old, val) -> graphCanvas.setZoom(val.doubleValue()));

    graphScroll.addEventFilter(
        ScrollEvent.SCROLL,
        e -> {
          if (e.isControlDown()) {
            double factor = e.getDeltaY() > 0 ? 1.1 : 1.0 / 1.1;
            double newZoom = Math.max(0.25, Math.min(2.5, graphCanvas.getScaleX() * factor));
            graphCanvas.setZoom(newZoom);
            sldZoom.setValue(newZoom);
            e.consume();
          }
        });

    graphCanvas.setOnEdgeRequested(this::handleEdgeRequested);
    graphCanvas.setOnEdgeDeleteRequested(this::handleEdgeDeleteRequested);

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

  @FXML
  void onEditMode() {
    editMode = !editMode;
    graphCanvas.setEditMode(editMode);
    btnEdit.setText(editMode ? "✓ Edit Mode" : "Edit Relationships");
    if (editMode) {
      lblStatus.setText(
          "Edit mode: click a node to select source, then click another to draw a FK.");
    } else if (discoveredGraph != null) {
      lblStatus.setText(
          String.format(
              "Graph ready — %d tables, %d relationships",
              discoveredGraph.nodeCount(), currentGraphEdgeCount()));
    }
  }

  @FXML
  void onZoomIn() {
    sldZoom.setValue(Math.min(2.5, sldZoom.getValue() + 0.15));
  }

  @FXML
  void onZoomOut() {
    sldZoom.setValue(Math.max(0.25, sldZoom.getValue() - 0.15));
  }

  @FXML
  void onFitToScreen() {
    if (!graphCanvas.hasContent()) return;
    Platform.runLater(
        () -> {
          var vp = graphScroll.getViewportBounds();
          double cW = graphCanvas.getPrefWidth();
          double cH = graphCanvas.getPrefHeight();
          if (cW <= 0 || cH <= 0) return;
          double zoom = Math.min(vp.getWidth() / cW, vp.getHeight() / cH) * 0.9;
          zoom = Math.max(0.25, Math.min(2.5, zoom));
          graphCanvas.setZoom(zoom);
          sldZoom.setValue(zoom);
          graphScroll.setHvalue(0.5);
          graphScroll.setVvalue(0.5);
        });
  }

  // ── Connections & table loading ───────────────────────────────────────────

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
    if (connName == null) return;
    new Thread(
            () -> {
              try {
                var profile = resolver.resolve(connName);
                var connector = ConnectorRegistry.findConnector(profile);
                var dbRef =
                    new io.recordrelay.core.domain.DatabaseRef(profile.database(), profile.type());
                var tables = connector.schemaInspector().listTables(profile, dbRef);
                var tableNames = tables.stream().map(t -> t.tableName()).sorted().toList();
                Platform.runLater(
                    () -> {
                      cboRootTable.setItems(FXCollections.observableArrayList(tableNames));
                      if (!tableNames.isEmpty()) cboRootTable.setValue(tableNames.get(0));
                    });
              } catch (Exception e) {
                Platform.runLater(() -> lblStatus.setText("Table load failed: " + e.getMessage()));
              }
            },
            "rr-table-load")
        .start();
  }

  // ── Discovery ─────────────────────────────────────────────────────────────

  private void discoverGraph(String connName, String rootTable) {
    try {
      var profile = resolver.resolve(connName);
      RelationshipGraph graph;
      try (var graphResolver = new JdbcRelationshipResolver()) {
        graph = graphResolver.resolve(profile, rootTable);
      }
      var merged = mergeCustomEdges(graph, connName);
      Platform.runLater(
          () -> {
            discoveredGraph = graph;
            currentConnName = connName;
            currentRootTable = rootTable;
            graphCanvas.render(merged, rootTable);
            graphCanvas.setEditMode(editMode);
            lblStatus.setText(
                String.format(
                    "Graph ready — %d tables, %d relationships",
                    merged.nodeCount(), merged.edgeCount()));
            btnDiscover.setDisable(false);
            onFitToScreen();
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

  // ── Edit mode callbacks ───────────────────────────────────────────────────

  private void handleEdgeRequested(String fromTable, String toTable) {
    showEdgeDialog(fromTable, toTable)
        .ifPresent(
            cols -> {
              var custom = customStore.load(currentConnName);
              custom.add(
                  new CustomRelationshipStore.CustomEdge(fromTable, cols[0], toTable, cols[1]));
              try {
                customStore.save(currentConnName, custom);
              } catch (Exception e) {
                LOG.warn("Could not save custom edge", e);
              }
              reRenderWithCustom();
            });
  }

  private void handleEdgeDeleteRequested(RelationshipGraphCanvas.EdgeInfo ei) {
    var custom = customStore.load(currentConnName);
    custom.removeIf(
        c ->
            c.fromTable().equalsIgnoreCase(ei.from)
                && c.fromColumn().equalsIgnoreCase(ei.fromCol)
                && c.toTable().equalsIgnoreCase(ei.to));
    try {
      customStore.save(currentConnName, custom);
    } catch (Exception e) {
      LOG.warn("Could not save custom edges after delete", e);
    }
    reRenderWithCustom();
  }

  private void reRenderWithCustom() {
    if (discoveredGraph == null || currentConnName == null) return;
    var merged = mergeCustomEdges(discoveredGraph, currentConnName);
    graphCanvas.render(merged, currentRootTable);
    graphCanvas.setEditMode(editMode);
    lblStatus.setText(
        String.format(
            "Graph ready — %d tables, %d relationships", merged.nodeCount(), merged.edgeCount()));
  }

  private RelationshipGraph mergeCustomEdges(RelationshipGraph base, String connName) {
    var custom = customStore.load(connName);
    if (custom.isEmpty()) return base;
    var builder = RelationshipGraph.builder();
    base.nodes().values().forEach(builder::addNode);
    base.edges().forEach(builder::addEdge);
    for (var c : custom) {
      builder.addEdge(
          new RelationshipEdge(
              new RelationshipNode(c.fromTable()),
              c.fromColumn(),
              new RelationshipNode(c.toTable()),
              c.toColumn(),
              RelationshipSource.MANUAL,
              1.0));
    }
    return builder.build();
  }

  private int currentGraphEdgeCount() {
    if (discoveredGraph == null) return 0;
    return discoveredGraph.edgeCount() + customStore.load(currentConnName).size();
  }

  // ── Dialog ────────────────────────────────────────────────────────────────

  private Optional<String[]> showEdgeDialog(String fromTable, String toTable) {
    var dialog = new Dialog<String[]>();
    dialog.setTitle("Add Relationship");
    dialog.setHeaderText(fromTable + "  →  " + toTable);

    var grid = new GridPane();
    grid.setHgap(12);
    grid.setVgap(8);
    grid.setPadding(new Insets(16, 16, 8, 16));

    var fromColField = new TextField();
    fromColField.setPromptText("e.g. customer_id");
    var toColField = new TextField("id");

    grid.add(new Label("From column (" + fromTable + "):"), 0, 0);
    grid.add(fromColField, 1, 0);
    grid.add(new Label("To column (" + toTable + "):"), 0, 1);
    grid.add(toColField, 1, 1);

    dialog.getDialogPane().setContent(grid);
    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

    var okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
    okBtn.setDisable(true);
    fromColField.textProperty().addListener((obs, o, n) -> okBtn.setDisable(n.isBlank()));

    dialog.setResultConverter(
        bt ->
            bt == ButtonType.OK
                ? new String[] {fromColField.getText().strip(), toColField.getText().strip()}
                : null);

    return dialog.showAndWait();
  }
}
