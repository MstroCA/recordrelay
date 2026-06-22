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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Point2D;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * JavaFX canvas that renders a {@link RelationshipGraph} as a layered BFS graph.
 *
 * <p>Root node is drawn in blue at the top; child nodes follow in grey. FK labels are shown on
 * edges.
 */
public final class RelationshipGraphCanvas extends Pane {

  private static final double NODE_W = 140;
  private static final double NODE_H = 36;
  private static final double H_GAP = 30;
  private static final double V_GAP = 70;
  private static final double MARGIN_X = 24;
  private static final double MARGIN_Y = 24;

  private static final Color ROOT_FILL = Color.web("#0969DA");
  private static final Color ROOT_TEXT = Color.WHITE;
  private static final Color NODE_FILL = Color.web("#F0F6FF");
  private static final Color NODE_STROKE = Color.web("#CBD5E0");
  private static final Color NODE_TEXT_COLOR = Color.web("#24292F");
  private static final Color EDGE_COLOR = Color.web("#6E7681");
  private static final Color LABEL_COLOR = Color.web("#57606A");

  /** Renders the given graph with {@code rootTable} at the top. */
  public void render(RelationshipGraph graph, String rootTable) {
    getChildren().clear();
    if (graph.isEmpty()) {
      var msg = new Text("No relationships discovered.");
      msg.setLayoutX(MARGIN_X);
      msg.setLayoutY(MARGIN_Y + 16);
      msg.setFill(LABEL_COLOR);
      getChildren().add(msg);
      return;
    }

    var layers = bfsLayers(graph, rootTable);
    var positions = assignPositions(layers);

    for (var edge : graph.edges()) {
      drawEdge(edge, positions);
    }
    for (var entry : positions.entrySet()) {
      boolean isRoot = entry.getKey().equals(rootTable);
      drawNode(entry.getKey(), entry.getValue(), isRoot);
    }

    double maxX =
        positions.values().stream()
            .mapToDouble(p -> p.getX() + NODE_W + MARGIN_X)
            .max()
            .orElse(400);
    double maxY =
        positions.values().stream()
            .mapToDouble(p -> p.getY() + NODE_H + MARGIN_Y)
            .max()
            .orElse(300);
    setPrefSize(maxX, maxY);
  }

  private List<List<String>> bfsLayers(RelationshipGraph graph, String root) {
    var layers = new ArrayList<List<String>>();
    var visited = new HashSet<String>();
    var queue = new ArrayDeque<String>();

    queue.add(root.toLowerCase());
    visited.add(root.toLowerCase());
    layers.add(new ArrayList<>(List.of(root)));

    while (!queue.isEmpty()) {
      var current = queue.poll();
      var children = new ArrayList<String>();
      for (var edge : graph.edges()) {
        collectNeighbor(
            edge.toNode().tableName(),
            edge.fromNode().tableName(),
            current,
            visited,
            children,
            queue);
        collectNeighbor(
            edge.fromNode().tableName(),
            edge.toNode().tableName(),
            current,
            visited,
            children,
            queue);
      }
      if (!children.isEmpty()) {
        layers.add(children);
      }
    }

    // Add any nodes not reachable from root
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
      java.util.Set<String> visited,
      List<String> children,
      java.util.Deque<String> queue) {
    if (anchor.equalsIgnoreCase(current) && !visited.contains(candidate.toLowerCase())) {
      visited.add(candidate.toLowerCase());
      children.add(candidate);
      queue.add(candidate.toLowerCase());
    }
  }

  private Map<String, Point2D> assignPositions(List<List<String>> layers) {
    var positions = new LinkedHashMap<String, Point2D>();
    double y = MARGIN_Y;
    for (var layer : layers) {
      double totalWidth = layer.size() * NODE_W + (layer.size() - 1) * H_GAP;
      double startX = MARGIN_X;
      for (int i = 0; i < layer.size(); i++) {
        double x = startX + i * (NODE_W + H_GAP);
        positions.put(layer.get(i), new Point2D(x, y));
      }
      y += NODE_H + V_GAP;
    }
    return positions;
  }

  private void drawNode(String tableName, Point2D pos, boolean isRoot) {
    var rect = new Rectangle(pos.getX(), pos.getY(), NODE_W, NODE_H);
    rect.setArcWidth(8);
    rect.setArcHeight(8);
    rect.setFill(isRoot ? ROOT_FILL : NODE_FILL);
    rect.setStroke(isRoot ? ROOT_FILL : NODE_STROKE);
    rect.setStrokeWidth(1.5);

    var label = new Text(tableName);
    label.setFont(Font.font("System", 12));
    label.setFill(isRoot ? ROOT_TEXT : NODE_TEXT_COLOR);
    double textX = pos.getX() + (NODE_W - label.getLayoutBounds().getWidth()) / 2;
    double textY = pos.getY() + NODE_H / 2 + 4;
    label.setLayoutX(textX);
    label.setLayoutY(textY);

    getChildren().addAll(rect, label);
  }

  private void drawEdge(RelationshipEdge edge, Map<String, Point2D> positions) {
    var fromPos = findPosition(positions, edge.fromNode().tableName());
    var toPos = findPosition(positions, edge.toNode().tableName());
    if (fromPos == null || toPos == null) {
      return;
    }

    double x1 = fromPos.getX() + NODE_W / 2;
    double y1 = fromPos.getY() + NODE_H;
    double x2 = toPos.getX() + NODE_W / 2;
    double y2 = toPos.getY();

    var line = new Line(x1, y1, x2, y2);
    line.setStroke(EDGE_COLOR);
    line.setStrokeWidth(1.2);
    line.getStrokeDashArray().addAll(6.0, 4.0);

    var arrow = arrowHead(x1, y1, x2, y2);

    var edgeLabel = edge.fromColumn() + " → " + edge.toColumn();
    var lbl = new Text(edgeLabel);
    lbl.setFont(Font.font("System", 10));
    lbl.setFill(LABEL_COLOR);
    lbl.setLayoutX((x1 + x2) / 2 - lbl.getLayoutBounds().getWidth() / 2);
    lbl.setLayoutY((y1 + y2) / 2 - 3);

    getChildren().addAll(line, arrow, lbl);
  }

  private Point2D findPosition(Map<String, Point2D> positions, String tableName) {
    for (var entry : positions.entrySet()) {
      if (entry.getKey().equalsIgnoreCase(tableName)) {
        return entry.getValue();
      }
    }
    return null;
  }

  private Polygon arrowHead(double x1, double y1, double x2, double y2) {
    double dx = x2 - x1;
    double dy = y2 - y1;
    double len = Math.sqrt(dx * dx + dy * dy);
    if (len < 1) {
      return new Polygon();
    }
    double ux = dx / len;
    double uy = dy / len;
    double tipX = x2;
    double tipY = y2;
    double size = 8;
    double px = -uy * size / 2;
    double py = ux * size / 2;
    var arrow =
        new Polygon(
            tipX,
            tipY,
            tipX - ux * size + px,
            tipY - uy * size + py,
            tipX - ux * size - px,
            tipY - uy * size - py);
    arrow.setFill(EDGE_COLOR);
    return arrow;
  }
}
