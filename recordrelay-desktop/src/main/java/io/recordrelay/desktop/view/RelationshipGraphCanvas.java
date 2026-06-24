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

import io.recordrelay.core.clone.domain.RelationshipEdge;
import io.recordrelay.core.clone.domain.RelationshipGraph;
import io.recordrelay.core.clone.domain.RelationshipSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.geometry.Point2D;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * JavaFX canvas that renders a {@link RelationshipGraph} as a UML-style ERD.
 *
 * <p>Each table is shown as a card with a coloured header (name) and a body listing outgoing FK
 * columns. FK-constraint edges are drawn as solid blue lines; heuristic (logical FK) edges are
 * drawn as grey dashed lines. The canvas expands to fit its content and is meant to live inside a
 * pannable {@link javafx.scene.control.ScrollPane}.
 */
public final class RelationshipGraphCanvas extends Pane {

  private static final double NODE_W = 200;
  private static final double HEADER_H = 30;
  private static final double ROW_H = 20;
  private static final double BODY_BOTTOM_PAD = 10;
  private static final double BODY_MIN_H = 14;
  private static final double H_GAP = 60;
  private static final double V_GAP = 90;
  private static final double MARGIN_X = 50;
  private static final double MARGIN_Y = 40;
  private static final double ARC = 10;

  private static final Color ROOT_HEADER = Color.web("#1F6FEB");
  private static final Color ROOT_BODY = Color.web("#EEF5FF");
  private static final Color NODE_HEADER = Color.web("#444D56");
  private static final Color NODE_BODY = Color.web("#FAFBFC");
  private static final Color NODE_STROKE = Color.web("#CBD5E0");
  private static final Color HEADER_TEXT = Color.WHITE;
  private static final Color COL_TEXT = Color.web("#444D56");
  private static final Color FK_EDGE = Color.web("#1F6FEB");
  private static final Color HEUR_EDGE = Color.web("#8A9099");

  /** Renders the graph with {@code rootTable} placed at the top layer. */
  public void render(RelationshipGraph graph, String rootTable) {
    getChildren().clear();
    if (graph.isEmpty()) {
      showEmpty();
      return;
    }

    var fkCols = collectFkColumns(graph);
    var layers = bfsLayers(graph, rootTable);
    var positions = assignPositions(layers, fkCols);

    for (var edge : graph.edges()) {
      drawEdge(edge, positions, fkCols);
    }
    for (var entry : positions.entrySet()) {
      String name = entry.getKey();
      var cols = fkCols.getOrDefault(name.toLowerCase(), List.of());
      drawNode(
          name, entry.getValue(), nodeHeight(cols.size()), name.equalsIgnoreCase(rootTable), cols);
    }

    double maxX = MARGIN_X;
    double maxY = MARGIN_Y;
    for (var e : positions.entrySet()) {
      var cols = fkCols.getOrDefault(e.getKey().toLowerCase(), List.of());
      maxX = Math.max(maxX, e.getValue().getX() + NODE_W + MARGIN_X);
      maxY = Math.max(maxY, e.getValue().getY() + nodeHeight(cols.size()) + MARGIN_Y);
    }
    setPrefSize(maxX, maxY);
    setMinSize(maxX, maxY);
  }

  /** Scales the canvas (zoom in/out). Wrapping in a {@code Group} makes the ScrollPane respond. */
  public void setZoom(double factor) {
    setScaleX(factor);
    setScaleY(factor);
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private static double nodeHeight(int colCount) {
    return HEADER_H + (colCount == 0 ? BODY_MIN_H : colCount * ROW_H + BODY_BOTTOM_PAD);
  }

  private static Map<String, List<String>> collectFkColumns(RelationshipGraph graph) {
    var result = new HashMap<String, List<String>>();
    for (var edge : graph.edges()) {
      result
          .computeIfAbsent(edge.fromNode().tableName().toLowerCase(), k -> new ArrayList<>())
          .add(edge.fromColumn());
    }
    return result;
  }

  private void showEmpty() {
    var msg =
        new Text(
            "No relationships found. Select a connection and root table, then click Discover.");
    msg.setLayoutX(MARGIN_X);
    msg.setLayoutY(MARGIN_Y + 16);
    msg.setFill(HEUR_EDGE);
    getChildren().add(msg);
    setPrefSize(620, 80);
    setMinSize(620, 80);
  }

  private List<List<String>> bfsLayers(RelationshipGraph graph, String root) {
    var layers = new ArrayList<List<String>>();
    var visited = new HashSet<String>();
    var queue = new ArrayDeque<String>();

    queue.add(root.toLowerCase());
    visited.add(root.toLowerCase());
    layers.add(new ArrayList<>(List.of(root)));

    while (!queue.isEmpty()) {
      int levelSize = queue.size();
      var levelNodes = new ArrayList<String>();
      for (int i = 0; i < levelSize; i++) {
        var current = queue.poll();
        for (var edge : graph.edges()) {
          collectNeighbor(
              edge.toNode().tableName(),
              edge.fromNode().tableName(),
              current,
              visited,
              levelNodes,
              queue);
          collectNeighbor(
              edge.fromNode().tableName(),
              edge.toNode().tableName(),
              current,
              visited,
              levelNodes,
              queue);
        }
      }
      if (!levelNodes.isEmpty()) {
        layers.add(levelNodes);
      }
    }

    var remaining = new ArrayList<String>();
    for (var name : graph.nodes().keySet()) {
      if (!visited.contains(name.toLowerCase())) {
        remaining.add(name);
      }
    }
    if (!remaining.isEmpty()) {
      layers.add(remaining);
    }
    return layers;
  }

  private static void collectNeighbor(
      String candidate,
      String anchor,
      String current,
      Set<String> visited,
      List<String> levelNodes,
      Deque<String> queue) {
    if (anchor.equalsIgnoreCase(current) && !visited.contains(candidate.toLowerCase())) {
      visited.add(candidate.toLowerCase());
      levelNodes.add(candidate);
      queue.add(candidate.toLowerCase());
    }
  }

  private Map<String, Point2D> assignPositions(
      List<List<String>> layers, Map<String, List<String>> fkCols) {
    var positions = new LinkedHashMap<String, Point2D>();
    double maxRowWidth =
        layers.stream()
            .mapToDouble(l -> l.size() * NODE_W + Math.max(0, l.size() - 1) * H_GAP)
            .max()
            .orElse(NODE_W);
    double y = MARGIN_Y;
    for (var layer : layers) {
      double rowWidth = layer.size() * NODE_W + Math.max(0, layer.size() - 1) * H_GAP;
      double startX = MARGIN_X + (maxRowWidth - rowWidth) / 2.0;
      double layerH =
          layer.stream()
              .mapToDouble(t -> nodeHeight(fkCols.getOrDefault(t.toLowerCase(), List.of()).size()))
              .max()
              .orElse(nodeHeight(0));
      for (int i = 0; i < layer.size(); i++) {
        positions.put(layer.get(i), new Point2D(startX + i * (NODE_W + H_GAP), y));
      }
      y += layerH + V_GAP;
    }
    return positions;
  }

  private void drawNode(String name, Point2D pos, double h, boolean isRoot, List<String> cols) {
    double x = pos.getX();
    double y = pos.getY();
    Color headerColor = isRoot ? ROOT_HEADER : NODE_HEADER;
    Color bodyColor = isRoot ? ROOT_BODY : NODE_BODY;

    // Full rounded rect — header color fills the header area
    var outer = new Rectangle(x, y, NODE_W, h);
    outer.setArcWidth(ARC);
    outer.setArcHeight(ARC);
    outer.setFill(headerColor);
    outer.setStroke(NODE_STROKE);
    outer.setStrokeWidth(1.5);

    // Body overlay covers everything below the header
    double bodyY = y + HEADER_H;
    var body = new Rectangle(x + 1.5, bodyY, NODE_W - 3, h - HEADER_H - 1.5);
    body.setArcWidth(ARC - 2);
    body.setArcHeight(ARC - 2);
    body.setFill(bodyColor);

    // Divider between header and body
    var divider = new Line(x + 1, bodyY, x + NODE_W - 1, bodyY);
    divider.setStroke(NODE_STROKE);
    divider.setStrokeWidth(0.5);

    // Table name centred in header
    var nameText = new Text(name);
    nameText.setFont(Font.font(null, FontWeight.BOLD, 12));
    nameText.setFill(HEADER_TEXT);
    double nameX = x + (NODE_W - nameText.getLayoutBounds().getWidth()) / 2;
    nameText.setLayoutX(nameX);
    nameText.setLayoutY(y + HEADER_H / 2 + 5);

    getChildren().addAll(outer, body, divider, nameText);

    for (int i = 0; i < cols.size(); i++) {
      var colText = new Text("  → " + cols.get(i));
      colText.setFont(Font.font(null, FontWeight.NORMAL, 11));
      colText.setFill(COL_TEXT);
      colText.setLayoutX(x + 8);
      colText.setLayoutY(bodyY + (i + 1) * ROW_H - 4);
      getChildren().add(colText);
    }
  }

  private void drawEdge(
      RelationshipEdge edge, Map<String, Point2D> positions, Map<String, List<String>> fkCols) {
    var fromPos = findPos(positions, edge.fromNode().tableName());
    var toPos = findPos(positions, edge.toNode().tableName());
    if (fromPos == null || toPos == null) {
      return;
    }

    var fromCols = fkCols.getOrDefault(edge.fromNode().tableName().toLowerCase(), List.of());
    int colIdx = fromCols.indexOf(edge.fromColumn());

    double x1;
    double y1;
    if (colIdx >= 0) {
      x1 = fromPos.getX() + NODE_W;
      y1 = fromPos.getY() + HEADER_H + (colIdx + 0.5) * ROW_H;
    } else {
      x1 = fromPos.getX() + NODE_W / 2;
      y1 = fromPos.getY() + nodeHeight(fromCols.size());
    }

    double x2 = toPos.getX() + NODE_W / 2;
    double y2 = toPos.getY();

    boolean isConstraint = edge.source() == RelationshipSource.FOREIGN_KEY;
    Color edgeColor = isConstraint ? FK_EDGE : HEUR_EDGE;

    var line = new Line(x1, y1, x2, y2);
    line.setStroke(edgeColor);
    line.setStrokeWidth(isConstraint ? 1.5 : 1.2);
    if (!isConstraint) {
      line.getStrokeDashArray().addAll(7.0, 4.0);
    }

    var arrow = arrowHead(x1, y1, x2, y2, edgeColor);
    getChildren().addAll(line, arrow);
  }

  private static Point2D findPos(Map<String, Point2D> positions, String tableName) {
    for (var e : positions.entrySet()) {
      if (e.getKey().equalsIgnoreCase(tableName)) {
        return e.getValue();
      }
    }
    return null;
  }

  private static Polygon arrowHead(double x1, double y1, double x2, double y2, Color color) {
    double dx = x2 - x1;
    double dy = y2 - y1;
    double len = Math.sqrt(dx * dx + dy * dy);
    if (len < 1) {
      return new Polygon();
    }
    double ux = dx / len;
    double uy = dy / len;
    double size = 9;
    double px = -uy * size / 2;
    double py = ux * size / 2;
    var arrow =
        new Polygon(
            x2,
            y2,
            x2 - ux * size + px,
            y2 - uy * size + py,
            x2 - ux * size - px,
            y2 - uy * size - py);
    arrow.setFill(color);
    return arrow;
  }
}
