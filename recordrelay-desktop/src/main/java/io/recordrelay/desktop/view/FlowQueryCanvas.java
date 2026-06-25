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

import io.recordrelay.cli.flow.QueryFlowModel;
import io.recordrelay.cli.flow.QueryFlowModel.JoinEntry;
import io.recordrelay.cli.flow.QueryFlowModel.JoinType;
import io.recordrelay.cli.flow.QueryFlowModel.NodeEntry;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * JavaFX canvas for the visual query flow builder.
 *
 * <p>Table node cards are absolute-positioned children. JOIN connections are rendered as Bezier
 * curves. Supports drag-to-reposition and click-port-to-connect interactions.
 */
public final class FlowQueryCanvas extends Pane {

  private static final int GRID_SIZE = 20;
  static final double NODE_W = 200;
  static final double HEADER_H = 30;
  static final double ROW_H = 24;
  private static final double MIN_W = 800;
  private static final double MIN_H = 520;
  private static final Color PORT_COLOR = Color.web("#42A5F5");
  private static final Color PORT_ACTIVE = Color.web("#FF7043");
  private static final Color GRID_DOT = Color.web("#DDE3EC");
  private static final Color BG_COLOR = Color.web("#F5F7FA");

  private final QueryFlowModel model;
  private final Canvas gridCanvas = new Canvas();
  private final Pane connectionLayer = new Pane();
  private final Pane nodeLayer = new Pane();
  private final Map<String, VBox> cardMap = new LinkedHashMap<>();
  private final double[] dragDelta = {0, 0};

  // Connect-mode state
  private String pendingAlias;
  private String pendingCol;
  private Line pendingLine;

  /** Creates a canvas bound to the given flow model. */
  public FlowQueryCanvas(QueryFlowModel model) {
    this.model = model;
    gridCanvas.setMouseTransparent(true);
    connectionLayer.setMouseTransparent(true);
    getChildren().addAll(gridCanvas, connectionLayer, nodeLayer);

    widthProperty().addListener((obs, old, w) -> drawGrid());
    heightProperty().addListener((obs, old, h) -> drawGrid());

    setOnMouseMoved(e -> {
      if (pendingLine != null) {
        pendingLine.setEndX(e.getX());
        pendingLine.setEndY(e.getY());
      }
    });
    setOnMouseClicked(e -> {
      if (pendingAlias != null) {
        cancelConnect();
      }
    });

    model.addChangeListener(this::redrawConnections);
    setPrefSize(MIN_W, MIN_H);
    setMinSize(MIN_W, MIN_H);
  }

  // ── Public API ───────────────────────────────────────────────────────────────

  /**
   * Adds a new table node card at the given position on the canvas.
   *
   * @param entry the model entry from {@link QueryFlowModel#addNode}
   * @param x     left edge in canvas coordinates
   * @param y     top edge in canvas coordinates
   */
  public void addTableNode(NodeEntry entry, double x, double y) {
    var card = buildCard(entry, x, y);
    cardMap.put(entry.alias(), card);
    nodeLayer.getChildren().add(card);
    expandCanvas(x + NODE_W + 40, y + card.prefHeight(NODE_W) + 40);
    redrawConnections();
  }

  /** Removes all nodes, connections, and resets connect-mode state. */
  public void clear() {
    nodeLayer.getChildren().clear();
    cardMap.clear();
    connectionLayer.getChildren().clear();
    cancelConnect();
    setPrefSize(MIN_W, MIN_H);
    setMinSize(MIN_W, MIN_H);
    drawGrid();
  }

  // ── Node building ────────────────────────────────────────────────────────────

  private VBox buildCard(NodeEntry entry, double x, double y) {
    var card = new VBox(0);
    card.setStyle(
        "-fx-background-color: white; -fx-border-color: #C9D3E0;"
            + " -fx-border-radius: 6; -fx-background-radius: 6;");
    card.setLayoutX(x);
    card.setLayoutY(y);
    card.setPrefWidth(NODE_W);
    card.getChildren().add(buildCardHeader(entry));
    for (var col : entry.columns()) {
      card.getChildren().add(
          buildColumnRow(entry, col.name(), col.nativeType(), col.primaryKey()));
    }
    installDrag(card);
    return card;
  }

  private HBox buildCardHeader(NodeEntry entry) {
    var header = new HBox(4);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setPadding(new Insets(4, 8, 4, 8));
    header.setStyle("-fx-background-color: #1E88E5; -fx-background-radius: 6 6 0 0;");
    header.setPrefHeight(HEADER_H);

    var title = new Label("[" + entry.alias() + "] " + entry.table().tableName());
    title.setTextFill(Color.WHITE);
    title.setFont(Font.font("System", FontWeight.BOLD, 11));
    title.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(title, Priority.ALWAYS);

    var closeBtn = new Label("×");
    closeBtn.setTextFill(Color.WHITE);
    closeBtn.setFont(Font.font("System", FontWeight.BOLD, 14));
    closeBtn.setCursor(Cursor.HAND);
    closeBtn.setOnMouseClicked(e -> {
      model.removeNode(entry.alias());
      removeCard(entry.alias());
      e.consume();
    });
    header.getChildren().addAll(title, closeBtn);
    return header;
  }

  private HBox buildColumnRow(NodeEntry entry, String colName, String colType, boolean isPk) {
    var row = new HBox(4);
    row.setAlignment(Pos.CENTER_LEFT);
    row.setPadding(new Insets(0, 4, 0, 4));
    row.setPrefHeight(ROW_H);
    row.setMaxHeight(ROW_H);

    var cb = new CheckBox();
    cb.setSelected(entry.isSelected(colName));
    cb.selectedProperty().addListener((obs, old, sel) -> model.toggleColumn(entry.alias(), colName));

    var nameLabel = new Label(isPk ? "🔑 " + colName : colName);
    nameLabel.setFont(Font.font("System", 10));
    HBox.setHgrow(nameLabel, Priority.ALWAYS);

    var typeLabel = new Label(colType);
    typeLabel.setFont(Font.font("System", 9));
    typeLabel.setTextFill(Color.GRAY);

    var port = new Circle(5, PORT_COLOR);
    port.setStroke(PORT_COLOR.darker());
    port.setCursor(Cursor.CROSSHAIR);
    port.setUserData(colName);
    port.setOnMouseClicked(e -> {
      onPortClick(entry.alias(), colName);
      e.consume();
    });
    port.setOnMouseEntered(e -> port.setFill(PORT_ACTIVE));
    port.setOnMouseExited(e -> port.setFill(PORT_COLOR));

    row.getChildren().addAll(cb, nameLabel, typeLabel, port);
    return row;
  }

  private void removeCard(String alias) {
    var card = cardMap.remove(alias);
    if (card != null) {
      nodeLayer.getChildren().remove(card);
    }
    redrawConnections();
  }

  // ── Drag ─────────────────────────────────────────────────────────────────────

  private void installDrag(VBox card) {
    card.setOnMousePressed(e -> {
      if (pendingAlias != null) {
        return;
      }
      dragDelta[0] = card.getLayoutX() - e.getSceneX();
      dragDelta[1] = card.getLayoutY() - e.getSceneY();
      card.setCursor(Cursor.MOVE);
      e.consume();
    });
    card.setOnMouseDragged(e -> {
      if (pendingAlias != null) {
        return;
      }
      double nx = Math.max(0, snapToGrid(e.getSceneX() + dragDelta[0]));
      double ny = Math.max(0, snapToGrid(e.getSceneY() + dragDelta[1]));
      card.setLayoutX(nx);
      card.setLayoutY(ny);
      expandCanvas(nx + NODE_W + 40, ny + card.prefHeight(NODE_W) + 40);
      redrawConnections();
      e.consume();
    });
    card.setOnMouseReleased(e -> card.setCursor(Cursor.DEFAULT));
  }

  private static double snapToGrid(double val) {
    return Math.round(val / GRID_SIZE) * GRID_SIZE;
  }

  // ── Grid ──────────────────────────────────────────────────────────────────────

  private void drawGrid() {
    double w = getWidth();
    double h = getHeight();
    if (w <= 0 || h <= 0) {
      return;
    }
    gridCanvas.setWidth(w);
    gridCanvas.setHeight(h);
    var gc = gridCanvas.getGraphicsContext2D();
    gc.setFill(BG_COLOR);
    gc.fillRect(0, 0, w, h);
    gc.setFill(GRID_DOT);
    for (double x = GRID_SIZE; x < w; x += GRID_SIZE) {
      for (double y = GRID_SIZE; y < h; y += GRID_SIZE) {
        gc.fillOval(x - 1, y - 1, 2, 2);
      }
    }
  }

  private void expandCanvas(double needW, double needH) {
    double newW = Math.max(getPrefWidth(), needW);
    double newH = Math.max(getPrefHeight(), needH);
    if (newW > getPrefWidth() || newH > getPrefHeight()) {
      setPrefSize(newW, newH);
      setMinSize(newW, newH);
    }
  }

  // ── Connections ───────────────────────────────────────────────────────────────

  private void redrawConnections() {
    connectionLayer.getChildren().clear();
    if (pendingLine != null) {
      connectionLayer.getChildren().add(pendingLine);
    }
    for (var join : model.joins()) {
      drawJoinCurve(join);
    }
  }

  private void drawJoinCurve(JoinEntry join) {
    var fromPt = portCenter(join.fromAlias(), join.fromColumn());
    var toPt = portCenter(join.toAlias(), join.toColumn());
    if (fromPt == null || toPt == null) {
      return;
    }
    Color color = joinColor(join.joinType());
    double cpOff = Math.max(60, Math.abs(toPt.getX() - fromPt.getX()) / 2);
    var curve = new CubicCurve(
        fromPt.getX(), fromPt.getY(),
        fromPt.getX() + cpOff, fromPt.getY(),
        toPt.getX() - cpOff, toPt.getY(),
        toPt.getX(), toPt.getY());
    curve.setFill(Color.TRANSPARENT);
    curve.setStroke(color);
    curve.setStrokeWidth(2);
    connectionLayer.getChildren().add(curve);
    addJoinLabel(fromPt, toPt, join.joinType().keyword, color);
  }

  private void addJoinLabel(Point2D from, Point2D to, String text, Color color) {
    double mx = (from.getX() + to.getX()) / 2;
    double my = (from.getY() + to.getY()) / 2;
    String hex = toHex(color);
    var lbl = new Label(text);
    lbl.setFont(Font.font("System", 9));
    lbl.setTextFill(Color.WHITE);
    lbl.setStyle("-fx-background-color: " + hex + "; -fx-padding: 1 4 1 4;"
        + " -fx-background-radius: 3;");
    lbl.setLayoutX(mx - 26);
    lbl.setLayoutY(my - 9);
    connectionLayer.getChildren().add(lbl);
  }

  private Point2D portCenter(String alias, String colName) {
    var card = cardMap.get(alias);
    if (card == null) {
      return null;
    }
    NodeEntry entry = model.nodes().stream()
        .filter(n -> n.alias().equals(alias))
        .findFirst().orElse(null);
    if (entry == null) {
      return null;
    }
    int idx = colIndex(entry, colName);
    double x = card.getLayoutX() + NODE_W - 6;
    double y = card.getLayoutY() + HEADER_H + idx * ROW_H + ROW_H / 2;
    return new Point2D(x, y);
  }

  private static int colIndex(NodeEntry entry, String colName) {
    var cols = entry.columns();
    for (int i = 0; i < cols.size(); i++) {
      if (cols.get(i).name().equals(colName)) {
        return i;
      }
    }
    return 0;
  }

  // ── Connect mode ──────────────────────────────────────────────────────────────

  private void onPortClick(String alias, String colName) {
    if (pendingAlias == null) {
      startConnect(alias, colName);
    } else if (!alias.equals(pendingAlias)) {
      finishConnect(alias, colName);
    } else {
      cancelConnect();
    }
  }

  private void startConnect(String alias, String colName) {
    pendingAlias = alias;
    pendingCol = colName;
    var pt = portCenter(alias, colName);
    if (pt != null) {
      pendingLine = new Line(pt.getX(), pt.getY(), pt.getX(), pt.getY());
      pendingLine.setStroke(PORT_ACTIVE);
      pendingLine.setStrokeWidth(1.5);
      pendingLine.getStrokeDashArray().addAll(6.0, 4.0);
      connectionLayer.getChildren().add(pendingLine);
    }
    highlightPorts(alias);
    setCursor(Cursor.CROSSHAIR);
  }

  private void finishConnect(String toAlias, String toCol) {
    String fromAlias = pendingAlias;
    String fromCol = pendingCol;
    cancelConnect();
    showJoinDialog(fromAlias, fromCol, toAlias, toCol);
  }

  private void showJoinDialog(String fromAlias, String fromCol, String toAlias, String toCol) {
    var choices = Arrays.stream(JoinType.values()).map(Enum::name).toList();
    var dialog = new ChoiceDialog<>(choices.get(0), choices);
    dialog.setTitle("Join Type");
    dialog.setHeaderText(null);
    dialog.setContentText("Join type:");
    dialog.showAndWait().ifPresent(chosen ->
        model.addJoin(fromAlias, fromCol, toAlias, toCol, JoinType.valueOf(chosen)));
  }

  private void cancelConnect() {
    pendingAlias = null;
    pendingCol = null;
    if (pendingLine != null) {
      connectionLayer.getChildren().remove(pendingLine);
      pendingLine = null;
    }
    resetPortColors();
    setCursor(Cursor.DEFAULT);
  }

  private void highlightPorts(String excludeAlias) {
    for (var entry : cardMap.entrySet()) {
      if (entry.getKey().equals(excludeAlias)) {
        continue;
      }
      entry.getValue().lookupAll(".circle").forEach(n -> {
        if (n instanceof Circle c) {
          c.setFill(PORT_ACTIVE);
        }
      });
    }
  }

  private void resetPortColors() {
    cardMap.values().forEach(card -> card.lookupAll(".circle").forEach(n -> {
      if (n instanceof Circle c) {
        c.setFill(PORT_COLOR);
      }
    }));
  }

  // ── Helpers ───────────────────────────────────────────────────────────────────

  private static Color joinColor(JoinType type) {
    return switch (type) {
      case INNER -> Color.web("#42A5F5");
      case LEFT -> Color.web("#66BB6A");
      case RIGHT -> Color.web("#FFA726");
    };
  }

  private static String toHex(Color c) {
    return String.format("#%02X%02X%02X",
        (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
  }
}
