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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * JavaFX canvas that renders a {@link RelationshipGraph} as an interactive ERD.
 *
 * <p>Features: wrapped BFS layout (max 4 nodes per row), node dragging, collapse/expand per card,
 * live-updated cubic-bezier edges, hover-highlight, and an edit mode for manually adding/removing
 * relationships.
 */
public final class RelationshipGraphCanvas extends Pane {

  // ── Layout ────────────────────────────────────────────────────────────────
  private static final double W = 220;
  private static final double HEADER_H = 38;
  private static final double ROW_H = 22;
  private static final double BODY_PAD = 12;
  private static final double MIN_BODY = 30;
  private static final double ARC = 12;
  private static final double H_GAP = 80;
  private static final double V_GAP = 100;
  private static final double MARGIN = 60;
  private static final int MAX_SHOWN = 7;
  private static final int MAX_PER_ROW = 4;

  // ── Palette ───────────────────────────────────────────────────────────────
  private static final Color ROOT_A = Color.web("#1E88E5");
  private static final Color ROOT_B = Color.web("#1565C0");
  private static final Color ROOT_BG = Color.web("#EDF4FF");
  private static final Color NODE_A = Color.web("#2D3748");
  private static final Color NODE_B = Color.web("#1A202C");
  private static final Color NODE_BG = Color.WHITE;
  private static final Color BORDER = Color.web("#CBD5E0");
  private static final Color HDR_FG = Color.WHITE;
  private static final Color COL_FG = Color.web("#4A5568");
  private static final Color ARR_FG = Color.web("#3182CE");
  private static final Color FK_CLR = Color.web("#3182CE");
  private static final Color MANUAL_CLR = Color.web("#38A169");
  private static final Color HR_CLR = Color.web("#A0AEC0");
  private static final Color SEL_STROKE = Color.web("#63B3ED");

  // ── Per-node mutable state ────────────────────────────────────────────────
  private static final class NodeInfo {
    String name;
    boolean isRoot;
    List<String> cols;
    double x, y, expandedH;
    boolean collapsed;
    Group group;
    Rectangle outerRect, overlay;
    List<javafx.scene.Node> bodyNodes = new ArrayList<>();
    Text toggleText;
  }

  // ── Per-edge live references ──────────────────────────────────────────────
  static final class EdgeInfo {
    String from, to, fromCol;
    boolean isFk, isManual;
    CubicCurve curve;
    Polygon arrow;
    Circle dot;
    List<javafx.scene.Node> shapes;
    // Delete button — only for MANUAL edges, visible only in edit mode
    Group deleteGroup;
  }

  private final Map<String, NodeInfo> nodes = new LinkedHashMap<>();
  private final List<EdgeInfo> edges = new ArrayList<>();
  private boolean hasContent;

  // ── Edit mode state ───────────────────────────────────────────────────────
  private boolean editMode = false;
  private NodeInfo selectedSource = null;
  private BiConsumer<String, String> onEdgeRequested;
  private Consumer<EdgeInfo> onEdgeDeleteRequested;

  // ─────────────────────────────────────────────────────────────────────────
  // Public API
  // ─────────────────────────────────────────────────────────────────────────

  /** Renders the graph with {@code rootTable} highlighted as the root entity. */
  public void render(RelationshipGraph graph, String rootTable) {
    getChildren().clear();
    nodes.clear();
    edges.clear();
    selectedSource = null;
    hasContent = false;

    if (graph.isEmpty()) {
      renderEmpty();
      return;
    }

    var fkCols = collectFkCols(graph);
    var layers = bfsLayers(graph, rootTable);
    var pos = computePositions(layers, fkCols);

    for (var entry : pos.entrySet()) {
      String name = entry.getKey();
      var cols = fkCols.getOrDefault(name.toLowerCase(), List.of());
      var info =
          buildNode(
              name,
              entry.getValue().getX(),
              entry.getValue().getY(),
              cols,
              name.equalsIgnoreCase(rootTable));
      nodes.put(name.toLowerCase(), info);
    }

    // Edges first (lower Z-order)
    for (var edge : graph.edges()) {
      if (edge.fromNode().tableName().equalsIgnoreCase(edge.toNode().tableName())) continue;
      var ei = buildEdge(edge);
      if (ei != null) {
        edges.add(ei);
        getChildren().addAll(ei.shapes);
      }
    }
    for (var info : nodes.values()) getChildren().add(info.group);

    // Delete buttons on top of everything
    for (var ei : edges) {
      if (ei.deleteGroup != null) {
        ei.deleteGroup.setVisible(editMode);
        getChildren().add(ei.deleteGroup);
      }
    }

    setupHover();
    updateCanvasSize();
    hasContent = true;
  }

  /** Scales the canvas uniformly. */
  public void setZoom(double z) {
    setScaleX(z);
    setScaleY(z);
  }

  /** True after a successful {@link #render} call with non-empty data. */
  public boolean hasContent() {
    return hasContent;
  }

  /** Switches between view mode and edit mode. */
  public void setEditMode(boolean edit) {
    this.editMode = edit;
    clearSourceSelection();
    for (var ei : edges) {
      if (ei.deleteGroup != null) ei.deleteGroup.setVisible(edit);
    }
  }

  /**
   * Callback fired when the user selects source → target in edit mode.
   *
   * @param cb receives (fromTableName, toTableName)
   */
  public void setOnEdgeRequested(BiConsumer<String, String> cb) {
    onEdgeRequested = cb;
  }

  /** Callback fired when the user clicks the delete button on a MANUAL edge in edit mode. */
  public void setOnEdgeDeleteRequested(Consumer<EdgeInfo> cb) {
    onEdgeDeleteRequested = cb;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Layout
  // ─────────────────────────────────────────────────────────────────────────

  private static Map<String, List<String>> collectFkCols(RelationshipGraph g) {
    var m = new LinkedHashMap<String, List<String>>();
    for (var e : g.edges())
      m.computeIfAbsent(e.fromNode().tableName().toLowerCase(), k -> new ArrayList<>())
          .add(e.fromColumn());
    return m;
  }

  private List<List<String>> bfsLayers(RelationshipGraph graph, String root) {
    var layers = new ArrayList<List<String>>();
    var visited = new HashSet<String>();
    var queue = new ArrayDeque<String>();
    queue.add(root.toLowerCase());
    visited.add(root.toLowerCase());
    layers.add(new ArrayList<>(List.of(root)));

    while (!queue.isEmpty()) {
      int sz = queue.size();
      var level = new ArrayList<String>();
      for (int i = 0; i < sz; i++) {
        String cur = queue.poll();
        for (var e : graph.edges()) {
          addNeighbor(e.toNode().tableName(), e.fromNode().tableName(), cur, visited, level, queue);
          addNeighbor(e.fromNode().tableName(), e.toNode().tableName(), cur, visited, level, queue);
        }
      }
      if (!level.isEmpty()) layers.add(level);
    }

    var leftover =
        graph.nodes().keySet().stream().filter(n -> !visited.contains(n.toLowerCase())).toList();
    if (!leftover.isEmpty()) layers.add(new ArrayList<>(leftover));
    return layers;
  }

  private static void addNeighbor(
      String candidate,
      String anchor,
      String cur,
      Set<String> visited,
      List<String> level,
      Deque<String> queue) {
    if (anchor.equalsIgnoreCase(cur) && !visited.contains(candidate.toLowerCase())) {
      visited.add(candidate.toLowerCase());
      level.add(candidate);
      queue.add(candidate.toLowerCase());
    }
  }

  private Map<String, Point2D> computePositions(
      List<List<String>> layers, Map<String, List<String>> fkCols) {
    var allRows = new ArrayList<List<String>>();
    var rowHeights = new ArrayList<Double>();
    for (var layer : layers) {
      for (int i = 0; i < layer.size(); i += MAX_PER_ROW) {
        var row = new ArrayList<>(layer.subList(i, Math.min(i + MAX_PER_ROW, layer.size())));
        allRows.add(row);
        rowHeights.add(
            row.stream()
                .mapToDouble(t -> nodeH(fkCols.getOrDefault(t.toLowerCase(), List.of()).size()))
                .max()
                .orElse(nodeH(0)));
      }
    }

    double maxRowW =
        allRows.stream()
            .mapToDouble(r -> r.size() * W + Math.max(0, r.size() - 1) * H_GAP)
            .max()
            .orElse(W);

    var pos = new LinkedHashMap<String, Point2D>();
    double y = MARGIN;
    for (int ri = 0; ri < allRows.size(); ri++) {
      var row = allRows.get(ri);
      double rw = row.size() * W + Math.max(0, row.size() - 1) * H_GAP;
      double sx = MARGIN + (maxRowW - rw) / 2.0;
      for (int i = 0; i < row.size(); i++)
        pos.put(row.get(i), new Point2D(sx + i * (W + H_GAP), y));
      y += rowHeights.get(ri) + V_GAP;
    }
    return pos;
  }

  private static double nodeH(int n) {
    if (n == 0) return HEADER_H + MIN_BODY;
    int shown = Math.min(n, MAX_SHOWN);
    return HEADER_H + shown * ROW_H + (n > MAX_SHOWN ? ROW_H : 0) + BODY_PAD;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Node building
  // ─────────────────────────────────────────────────────────────────────────

  private NodeInfo buildNode(String name, double x, double y, List<String> cols, boolean isRoot) {
    var info = new NodeInfo();
    info.name = name;
    info.isRoot = isRoot;
    info.cols = new ArrayList<>(cols);
    info.x = x;
    info.y = y;
    info.expandedH = nodeH(cols.size());

    var group = new Group();
    group.setLayoutX(x);
    group.setLayoutY(y);
    info.group = group;

    var shadow = new DropShadow();
    shadow.setRadius(16);
    shadow.setOffsetY(5);
    shadow.setColor(Color.color(0, 0, 0, isRoot ? 0.22 : 0.14));

    var outer = new Rectangle(0, 0, W, info.expandedH);
    outer.setArcWidth(ARC * 2);
    outer.setArcHeight(ARC * 2);
    outer.setFill(
        isRoot
            ? new LinearGradient(
                0, 0, 0, 1, true, CycleMethod.NO_CYCLE, new Stop(0, ROOT_A), new Stop(1, ROOT_B))
            : new LinearGradient(
                0, 0, 0, 1, true, CycleMethod.NO_CYCLE, new Stop(0, NODE_A), new Stop(1, NODE_B)));
    outer.setEffect(shadow);
    outer.setStroke(isRoot ? Color.web("#0D47A1") : Color.web("#111827"));
    outer.setStrokeWidth(0.5);
    info.outerRect = outer;

    var body = new Rectangle(1, HEADER_H, W - 2, info.expandedH - HEADER_H - 1.5);
    body.setArcWidth(ARC * 2 - 2);
    body.setArcHeight(ARC * 2 - 2);
    body.setFill(isRoot ? ROOT_BG : NODE_BG);
    info.bodyNodes.add(body);

    var div = new Line(1, HEADER_H, W - 1, HEADER_H);
    div.setStroke(BORDER);
    div.setStrokeWidth(1);
    info.bodyNodes.add(div);

    int shown = Math.min(cols.size(), MAX_SHOWN);
    for (int i = 0; i < shown; i++) {
      var arr = new Text("→");
      arr.setFont(Font.font(null, FontWeight.BOLD, 11));
      arr.setFill(ARR_FG);
      arr.setLayoutX(12);
      arr.setLayoutY(HEADER_H + (i + 1) * ROW_H - 4);
      var col = new Text(cols.get(i));
      col.setFont(Font.font("Monospaced", FontWeight.NORMAL, 11));
      col.setFill(COL_FG);
      col.setLayoutX(30);
      col.setLayoutY(HEADER_H + (i + 1) * ROW_H - 4);
      info.bodyNodes.add(arr);
      info.bodyNodes.add(col);
    }
    if (cols.size() > MAX_SHOWN) {
      var more = new Text("+" + (cols.size() - MAX_SHOWN) + " more");
      more.setFont(Font.font(null, FontPosture.ITALIC, 10));
      more.setFill(HR_CLR);
      more.setLayoutX(14);
      more.setLayoutY(HEADER_H + (shown + 1) * ROW_H - 4);
      info.bodyNodes.add(more);
    }
    if (cols.isEmpty()) {
      var hint = new Text("no outgoing FKs");
      hint.setFont(Font.font(null, FontPosture.ITALIC, 11));
      hint.setFill(HR_CLR);
      double hw = hint.getLayoutBounds().getWidth();
      hint.setLayoutX((W - hw) / 2.0);
      hint.setLayoutY(HEADER_H + MIN_BODY / 2.0 + 4);
      info.bodyNodes.add(hint);
    }

    var title = new Text(name);
    title.setFont(Font.font(null, FontWeight.BOLD, 13));
    title.setFill(HDR_FG);
    double tw = title.getLayoutBounds().getWidth();
    title.setLayoutX(Math.max(8, (W - tw) / 2.0 - 10));
    title.setLayoutY(HEADER_H / 2.0 + 5);

    var toggleCircle = new Circle(W - 14, HEADER_H / 2.0, 9, Color.web("#FFFFFF", 0.18));
    toggleCircle.setStroke(Color.web("#FFFFFF", 0.45));
    toggleCircle.setStrokeWidth(1);
    var toggleText = new Text("−");
    toggleText.setFont(Font.font(null, FontWeight.BOLD, 14));
    toggleText.setFill(Color.WHITE);
    toggleText.setLayoutX(W - 20);
    toggleText.setLayoutY(HEADER_H / 2.0 + 5.5);
    info.toggleText = toggleText;

    var overlay = new Rectangle(0, 0, W, info.expandedH);
    overlay.setFill(Color.TRANSPARENT);
    overlay.setArcWidth(ARC * 2);
    overlay.setArcHeight(ARC * 2);
    overlay.setCursor(Cursor.MOVE);
    info.overlay = overlay;

    group.getChildren().add(outer);
    group.getChildren().addAll(info.bodyNodes);
    group.getChildren().addAll(title, toggleCircle, toggleText, overlay);

    // Drag
    double[] dragOff = {0, 0};
    overlay.setOnMousePressed(
        e -> {
          var pt = group.getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
          dragOff[0] = pt.getX() - info.x;
          dragOff[1] = pt.getY() - info.y;
          group.toFront();
          e.consume();
        });
    overlay.setOnMouseDragged(
        e -> {
          var pt = group.getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
          info.x = pt.getX() - dragOff[0];
          info.y = pt.getY() - dragOff[1];
          group.setLayoutX(info.x);
          group.setLayoutY(info.y);
          updateConnectedEdges(info.name);
          updateCanvasSize();
          e.consume();
        });

    overlay.setOnMouseClicked(
        e -> {
          // Collapse toggle
          if (e.getX() >= W - 26 && e.getY() <= HEADER_H) {
            toggleCollapse(info);
            e.consume();
            return;
          }
          // Edit mode: source → target selection
          if (editMode) {
            handleEditClick(info);
            e.consume();
          }
        });

    overlay.setOnMouseMoved(
        e ->
            overlay.setCursor(
                e.getX() >= W - 26 && e.getY() <= HEADER_H ? Cursor.HAND : Cursor.MOVE));

    return info;
  }

  private void toggleCollapse(NodeInfo info) {
    info.collapsed = !info.collapsed;
    double newH = info.collapsed ? HEADER_H : info.expandedH;
    info.outerRect.setHeight(newH);
    info.overlay.setHeight(newH);
    info.bodyNodes.forEach(n -> n.setVisible(!info.collapsed));
    info.toggleText.setText(info.collapsed ? "+" : "−");
    updateConnectedEdges(info.name);
    updateCanvasSize();
  }

  // ── Edit mode ─────────────────────────────────────────────────────────────

  private void handleEditClick(NodeInfo clicked) {
    if (selectedSource == null) {
      selectedSource = clicked;
      setNodeSelected(clicked, true);
    } else if (selectedSource == clicked) {
      clearSourceSelection();
    } else {
      if (onEdgeRequested != null) {
        onEdgeRequested.accept(selectedSource.name, clicked.name);
      }
      clearSourceSelection();
    }
  }

  private void clearSourceSelection() {
    if (selectedSource != null) {
      setNodeSelected(selectedSource, false);
      selectedSource = null;
    }
  }

  private void setNodeSelected(NodeInfo info, boolean selected) {
    info.outerRect.setStroke(selected ? SEL_STROKE : defaultStroke(info));
    info.outerRect.setStrokeWidth(selected ? 2.5 : 0.5);
  }

  private static Color defaultStroke(NodeInfo info) {
    return info.isRoot ? Color.web("#0D47A1") : Color.web("#111827");
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Edge building & live updates
  // ─────────────────────────────────────────────────────────────────────────

  private EdgeInfo buildEdge(RelationshipEdge edge) {
    var from = nodes.get(edge.fromNode().tableName().toLowerCase());
    var to = nodes.get(edge.toNode().tableName().toLowerCase());
    if (from == null || to == null) return null;

    boolean isFk = edge.source() == RelationshipSource.FOREIGN_KEY;
    boolean isManual = edge.source() == RelationshipSource.MANUAL;
    Color color = isFk ? FK_CLR : (isManual ? MANUAL_CLR : HR_CLR);

    var curve = new CubicCurve();
    curve.setFill(null);
    curve.setStroke(color);
    curve.setStrokeWidth(isFk || isManual ? 2.0 : 1.5);
    if (!isFk && !isManual) curve.getStrokeDashArray().addAll(9.0, 5.0);

    var arrow = new Polygon();
    arrow.setFill(color);
    var dot = new Circle(isFk || isManual ? 4.0 : 3.0, color);

    var ei = new EdgeInfo();
    ei.from = edge.fromNode().tableName();
    ei.to = edge.toNode().tableName();
    ei.fromCol = edge.fromColumn();
    ei.isFk = isFk;
    ei.isManual = isManual;
    ei.curve = curve;
    ei.arrow = arrow;
    ei.dot = dot;
    ei.shapes = List.of(curve, arrow, dot);

    if (isManual) ei.deleteGroup = buildDeleteGroup(ei);

    updateEdge(ei);
    return ei;
  }

  private Group buildDeleteGroup(EdgeInfo ei) {
    var circle = new Circle(10, Color.web("#FC8181"));
    circle.setStroke(Color.web("#C53030"));
    circle.setStrokeWidth(1.5);
    circle.setCursor(Cursor.HAND);

    var x = new Text("✕");
    x.setFont(Font.font(null, FontWeight.BOLD, 10));
    x.setFill(Color.WHITE);
    x.setLayoutX(-4.5);
    x.setLayoutY(4);

    var g = new Group(circle, x);

    var handler =
        (javafx.event.EventHandler<javafx.scene.input.MouseEvent>)
            e -> {
              if (editMode && onEdgeDeleteRequested != null) {
                onEdgeDeleteRequested.accept(ei);
              }
              e.consume();
            };
    circle.setOnMouseClicked(handler);
    x.setOnMouseClicked(handler);
    return g;
  }

  private void updateEdge(EdgeInfo ei) {
    var from = nodes.get(ei.from.toLowerCase());
    var to = nodes.get(ei.to.toLowerCase());
    if (from == null || to == null) return;

    double fromH = from.collapsed ? HEADER_H : from.expandedH;
    double toH = to.collapsed ? HEADER_H : to.expandedH;
    double fromCX = from.x + W / 2, fromCY = from.y + fromH / 2;
    double toCX = to.x + W / 2, toCY = to.y + toH / 2;
    double dx = toCX - fromCX, dy = toCY - fromCY;
    double tension = Math.max(60, Math.min(Math.hypot(dx, dy) * 0.45, 300));
    int colIdx = from.collapsed ? -1 : from.cols.indexOf(ei.fromCol);

    double sx, sy, tx, ty, cpx1, cpy1, cpx2, cpy2;
    if (Math.abs(dy) > Math.abs(dx)) {
      double vsign = dy > 0 ? 1 : -1;
      sx = from.x + W / 2;
      sy = dy > 0 ? from.y + fromH : from.y;
      tx = to.x + W / 2;
      ty = dy > 0 ? to.y : to.y + toH;
      cpx1 = sx;
      cpy1 = sy + vsign * tension;
      cpx2 = tx;
      cpy2 = ty - vsign * tension;
    } else {
      double hsign = dx > 0 ? 1 : -1;
      sx = dx > 0 ? from.x + W : from.x;
      sy = colIdx >= 0 ? from.y + HEADER_H + (colIdx + 0.5) * ROW_H : from.y + fromH / 2;
      tx = dx > 0 ? to.x : to.x + W;
      ty = to.y + toH / 2;
      cpx1 = sx + hsign * tension;
      cpy1 = sy;
      cpx2 = tx - hsign * tension;
      cpy2 = ty;
    }

    ei.curve.setStartX(sx);
    ei.curve.setStartY(sy);
    ei.curve.setControlX1(cpx1);
    ei.curve.setControlY1(cpy1);
    ei.curve.setControlX2(cpx2);
    ei.curve.setControlY2(cpy2);
    ei.curve.setEndX(tx);
    ei.curve.setEndY(ty);
    ei.dot.setCenterX(sx);
    ei.dot.setCenterY(sy);

    double adx = tx - cpx2, ady = ty - cpy2;
    double len = Math.hypot(adx, ady);
    if (len >= 1) {
      double ux = adx / len, uy = ady / len;
      double size = 11, half = 4.5;
      double px = -uy * half, py = ux * half;
      ei.arrow
          .getPoints()
          .setAll(
              tx,
              ty,
              tx - ux * size + px,
              ty - uy * size + py,
              tx - ux * size - px,
              ty - uy * size - py);
    }

    // Delete button at Bezier midpoint (t = 0.5)
    if (ei.deleteGroup != null) {
      double midX = 0.125 * sx + 0.375 * cpx1 + 0.375 * cpx2 + 0.125 * tx;
      double midY = 0.125 * sy + 0.375 * cpy1 + 0.375 * cpy2 + 0.125 * ty;
      ei.deleteGroup.setLayoutX(midX);
      ei.deleteGroup.setLayoutY(midY);
    }
  }

  private void updateConnectedEdges(String name) {
    for (var ei : edges)
      if (ei.from.equalsIgnoreCase(name) || ei.to.equalsIgnoreCase(name)) updateEdge(ei);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Hover
  // ─────────────────────────────────────────────────────────────────────────

  private void setupHover() {
    for (var info : nodes.values()) {
      var connEdgeIdx = new ArrayList<Integer>();
      var connNames = new HashSet<String>();
      connNames.add(info.name.toLowerCase());
      for (int i = 0; i < edges.size(); i++) {
        var e = edges.get(i);
        if (e.from.equalsIgnoreCase(info.name) || e.to.equalsIgnoreCase(info.name)) {
          connEdgeIdx.add(i);
          connNames.add(e.from.toLowerCase());
          connNames.add(e.to.toLowerCase());
        }
      }
      info.overlay.setOnMouseEntered(
          e -> {
            nodes
                .values()
                .forEach(
                    ni ->
                        ni.group.setOpacity(
                            connNames.contains(ni.name.toLowerCase()) ? 1.0 : 0.10));
            for (int i = 0; i < edges.size(); i++) {
              double op = connEdgeIdx.contains(i) ? 1.0 : 0.06;
              edges.get(i).shapes.forEach(s -> s.setOpacity(op));
            }
          });
      info.overlay.setOnMouseExited(
          e -> {
            nodes.values().forEach(ni -> ni.group.setOpacity(1.0));
            edges.forEach(ei -> ei.shapes.forEach(s -> s.setOpacity(1.0)));
          });
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Canvas size & empty state
  // ─────────────────────────────────────────────────────────────────────────

  private void updateCanvasSize() {
    double maxX = MARGIN, maxY = MARGIN;
    for (var info : nodes.values()) {
      double h = info.collapsed ? HEADER_H : info.expandedH;
      maxX = Math.max(maxX, info.x + W + MARGIN);
      maxY = Math.max(maxY, info.y + h + MARGIN);
    }
    setPrefSize(maxX, maxY);
    setMinSize(maxX, maxY);
  }

  private void renderEmpty() {
    var msg = new Text("Select a connection and root table, then click Discover.");
    msg.setFont(Font.font(null, FontPosture.ITALIC, 13));
    msg.setFill(HR_CLR);
    msg.setLayoutX(MARGIN);
    msg.setLayoutY(MARGIN + 16);
    getChildren().add(msg);
    setPrefSize(560, 100);
    setMinSize(560, 100);
  }
}
