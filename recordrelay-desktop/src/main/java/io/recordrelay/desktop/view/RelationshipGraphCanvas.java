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
import javafx.geometry.Point2D;
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
 * JavaFX canvas that renders a {@link RelationshipGraph} as a professional ERD.
 *
 * <p>Features: hierarchical BFS layout, cubic-bezier edges with smart port selection, gradient node
 * headers, drop-shadow cards, hover-to-highlight interactions, and source-dot + arrowhead edge
 * terminators.
 */
public final class RelationshipGraphCanvas extends Pane {

  // ── Layout ───────────────────────────────────────────────────────────────
  private static final double W = 220;
  private static final double HEADER_H = 38;
  private static final double ROW_H = 22;
  private static final double BODY_PAD = 12;
  private static final double MIN_BODY = 30;
  private static final double ARC = 12;
  private static final double H_GAP = 96;
  private static final double V_GAP = 116;
  private static final double MARGIN = 72;
  private static final int MAX_SHOWN = 7;

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
  private static final Color HR_CLR = Color.web("#A0AEC0");

  // ── Per-render state ──────────────────────────────────────────────────────
  private record NodeGeom(double x, double y, double h, List<String> cols) {}

  private record NodeViz(String name, Rectangle overlay, List<javafx.scene.Node> all) {}

  private record EdgeViz(String from, String to, List<javafx.scene.Node> shapes) {}

  private record EdgePorts(
      double sx,
      double sy,
      double tx,
      double ty,
      double cpx1,
      double cpy1,
      double cpx2,
      double cpy2) {}

  private final List<NodeViz> nodeVizList = new ArrayList<>();
  private final List<EdgeViz> edgeVizList = new ArrayList<>();
  private final Map<String, NodeGeom> geomMap = new LinkedHashMap<>();
  private boolean hasContent = false;

  // ─────────────────────────────────────────────────────────────────────────

  /** Renders the graph with {@code rootTable} at the top of the hierarchy. */
  public void render(RelationshipGraph graph, String rootTable) {
    getChildren().clear();
    nodeVizList.clear();
    edgeVizList.clear();
    geomMap.clear();
    hasContent = false;

    if (graph.isEmpty()) {
      renderEmpty();
      return;
    }

    var fkCols = collectFkCols(graph);
    var layers = bfsLayers(graph, rootTable);
    var pos = computePositions(layers, fkCols);

    for (var entry : pos.entrySet()) {
      var cols = fkCols.getOrDefault(entry.getKey().toLowerCase(), List.of());
      geomMap.put(
          entry.getKey(),
          new NodeGeom(entry.getValue().getX(), entry.getValue().getY(), nodeH(cols.size()), cols));
    }

    for (var edge : graph.edges()) {
      drawEdge(edge);
    }
    for (var entry : geomMap.entrySet()) {
      drawNode(entry.getKey(), entry.getValue(), entry.getKey().equalsIgnoreCase(rootTable));
    }

    setupHover();

    double maxX = MARGIN, maxY = MARGIN;
    for (var g : geomMap.values()) {
      maxX = Math.max(maxX, g.x() + W + MARGIN);
      maxY = Math.max(maxY, g.y() + g.h() + MARGIN);
    }
    setPrefSize(maxX, maxY);
    setMinSize(maxX, maxY);
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

  // ── Helpers ───────────────────────────────────────────────────────────────

  private static double nodeH(int n) {
    if (n == 0) {
      return HEADER_H + MIN_BODY;
    }
    int shown = Math.min(n, MAX_SHOWN);
    double extra = n > MAX_SHOWN ? ROW_H : 0;
    return HEADER_H + shown * ROW_H + extra + BODY_PAD;
  }

  private static Map<String, List<String>> collectFkCols(RelationshipGraph g) {
    var m = new LinkedHashMap<String, List<String>>();
    for (var e : g.edges()) {
      m.computeIfAbsent(e.fromNode().tableName().toLowerCase(), k -> new ArrayList<>())
          .add(e.fromColumn());
    }
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
          addIfNew(e.toNode().tableName(), e.fromNode().tableName(), cur, visited, level, queue);
          addIfNew(e.fromNode().tableName(), e.toNode().tableName(), cur, visited, level, queue);
        }
      }
      if (!level.isEmpty()) {
        layers.add(level);
      }
    }

    var leftover = new ArrayList<String>();
    for (String n : graph.nodes().keySet()) {
      if (!visited.contains(n.toLowerCase())) {
        leftover.add(n);
      }
    }
    if (!leftover.isEmpty()) {
      layers.add(leftover);
    }
    return layers;
  }

  private static void addIfNew(
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
    var pos = new LinkedHashMap<String, Point2D>();
    double maxW =
        layers.stream()
            .mapToDouble(l -> l.size() * W + Math.max(0, l.size() - 1) * H_GAP)
            .max()
            .orElse(W);
    double y = MARGIN;
    for (var layer : layers) {
      double rw = layer.size() * W + Math.max(0, layer.size() - 1) * H_GAP;
      double sx = MARGIN + (maxW - rw) / 2.0;
      double lh =
          layer.stream()
              .mapToDouble(t -> nodeH(fkCols.getOrDefault(t.toLowerCase(), List.of()).size()))
              .max()
              .orElse(nodeH(0));
      for (int i = 0; i < layer.size(); i++) {
        pos.put(layer.get(i), new Point2D(sx + i * (W + H_GAP), y));
      }
      y += lh + V_GAP;
    }
    return pos;
  }

  // ── Node rendering ────────────────────────────────────────────────────────

  private void drawNode(String name, NodeGeom g, boolean isRoot) {
    var shapes = new ArrayList<javafx.scene.Node>();
    shapes.addAll(buildCardShapes(name, g, isRoot));
    shapes.addAll(buildColumnShapes(g));

    var overlay = new Rectangle(g.x(), g.y(), W, g.h());
    overlay.setFill(Color.TRANSPARENT);
    overlay.setArcWidth(ARC * 2);
    overlay.setArcHeight(ARC * 2);
    shapes.add(overlay);

    getChildren().addAll(shapes);
    nodeVizList.add(new NodeViz(name, overlay, shapes));
  }

  private static List<javafx.scene.Node> buildCardShapes(String name, NodeGeom g, boolean isRoot) {
    var shadow = new DropShadow();
    shadow.setRadius(16);
    shadow.setOffsetY(5);
    shadow.setColor(Color.color(0, 0, 0, isRoot ? 0.22 : 0.14));

    var outer = new Rectangle(g.x(), g.y(), W, g.h());
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

    double bodyY = g.y() + HEADER_H;
    var body = new Rectangle(g.x() + 1, bodyY, W - 2, g.h() - HEADER_H - 1.5);
    body.setArcWidth(ARC * 2 - 2);
    body.setArcHeight(ARC * 2 - 2);
    body.setFill(isRoot ? ROOT_BG : NODE_BG);

    var div = new Line(g.x() + 1, bodyY, g.x() + W - 1, bodyY);
    div.setStroke(BORDER);
    div.setStrokeWidth(1);

    var title = new Text(name);
    title.setFont(Font.font(null, FontWeight.BOLD, 13));
    title.setFill(HDR_FG);
    double tw = title.getLayoutBounds().getWidth();
    title.setLayoutX(g.x() + Math.max(10, (W - tw) / 2.0));
    title.setLayoutY(g.y() + HEADER_H / 2.0 + 5);

    return new ArrayList<>(List.of(outer, body, div, title));
  }

  private static List<javafx.scene.Node> buildColumnShapes(NodeGeom g) {
    var shapes = new ArrayList<javafx.scene.Node>();
    var cols = g.cols();
    int shown = Math.min(cols.size(), MAX_SHOWN);
    double bodyY = g.y() + HEADER_H;

    for (int i = 0; i < shown; i++) {
      var arr = new Text("→");
      arr.setFont(Font.font(null, FontWeight.BOLD, 11));
      arr.setFill(ARR_FG);
      arr.setLayoutX(g.x() + 12);
      arr.setLayoutY(bodyY + (i + 1) * ROW_H - 4);

      var col = new Text(cols.get(i));
      col.setFont(Font.font("Monospaced", FontWeight.NORMAL, 11));
      col.setFill(COL_FG);
      col.setLayoutX(g.x() + 30);
      col.setLayoutY(bodyY + (i + 1) * ROW_H - 4);
      shapes.add(arr);
      shapes.add(col);
    }

    if (cols.size() > MAX_SHOWN) {
      var more = new Text("+" + (cols.size() - MAX_SHOWN) + " more");
      more.setFont(Font.font(null, FontPosture.ITALIC, 10));
      more.setFill(HR_CLR);
      more.setLayoutX(g.x() + 14);
      more.setLayoutY(bodyY + (shown + 1) * ROW_H - 4);
      shapes.add(more);
    }

    if (cols.isEmpty()) {
      var hint = new Text("no outgoing FKs");
      hint.setFont(Font.font(null, FontPosture.ITALIC, 11));
      hint.setFill(HR_CLR);
      double hw = hint.getLayoutBounds().getWidth();
      hint.setLayoutX(g.x() + (W - hw) / 2.0);
      hint.setLayoutY(bodyY + MIN_BODY / 2.0 + 4);
      shapes.add(hint);
    }
    return shapes;
  }

  // ── Edge rendering ────────────────────────────────────────────────────────

  private void drawEdge(RelationshipEdge edge) {
    if (edge.fromNode().tableName().equalsIgnoreCase(edge.toNode().tableName())) {
      return;
    }

    var fromG = findGeom(edge.fromNode().tableName());
    var toG = findGeom(edge.toNode().tableName());
    if (fromG == null || toG == null) {
      return;
    }

    int colIdx = fromG.cols().indexOf(edge.fromColumn());
    double fcx = fromG.x() + W / 2;
    double tcx = toG.x() + W / 2;
    double fcy = fromG.y() + fromG.h() / 2;
    double tcy = toG.y() + toG.h() / 2;
    double dx = tcx - fcx;
    double dy = tcy - fcy;
    double tension = Math.max(60, Math.min(Math.hypot(dx, dy) * 0.45, 300));
    var ports = computePorts(fromG, toG, dx, dy, tension, colIdx);

    boolean isFk = edge.source() == RelationshipSource.FOREIGN_KEY;
    Color color = isFk ? FK_CLR : HR_CLR;

    var curve =
        new CubicCurve(
            ports.sx(),
            ports.sy(),
            ports.cpx1(),
            ports.cpy1(),
            ports.cpx2(),
            ports.cpy2(),
            ports.tx(),
            ports.ty());
    curve.setFill(null);
    curve.setStroke(color);
    curve.setStrokeWidth(isFk ? 2.0 : 1.5);
    if (!isFk) {
      curve.getStrokeDashArray().addAll(9.0, 5.0);
    }

    var arrowHead = makeArrow(ports.cpx2(), ports.cpy2(), ports.tx(), ports.ty(), color);
    var dot = new Circle(ports.sx(), ports.sy(), isFk ? 4.0 : 3.0, color);
    var shapes = List.<javafx.scene.Node>of(curve, arrowHead, dot);
    getChildren().addAll(shapes);
    edgeVizList.add(
        new EdgeViz(
            edge.fromNode().tableName(), edge.toNode().tableName(), new ArrayList<>(shapes)));
  }

  private static EdgePorts computePorts(
      NodeGeom fromG, NodeGeom toG, double dx, double dy, double tension, int colIdx) {
    double sx, sy, tx, ty, cpx1, cpy1, cpx2, cpy2;
    if (Math.abs(dy) > Math.abs(dx)) {
      double vsign = dy > 0 ? 1 : -1;
      sx = fromG.x() + W / 2;
      sy = dy > 0 ? fromG.y() + fromG.h() : fromG.y();
      tx = toG.x() + W / 2;
      ty = dy > 0 ? toG.y() : toG.y() + toG.h();
      cpx1 = sx;
      cpy1 = sy + vsign * tension;
      cpx2 = tx;
      cpy2 = ty - vsign * tension;
    } else {
      double hsign = dx > 0 ? 1 : -1;
      double fcy = fromG.y() + fromG.h() / 2;
      sx = dx > 0 ? fromG.x() + W : fromG.x();
      sy = colIdx >= 0 ? fromG.y() + HEADER_H + (colIdx + 0.5) * ROW_H : fcy;
      tx = dx > 0 ? toG.x() : toG.x() + W;
      ty = toG.y() + toG.h() / 2;
      cpx1 = sx + hsign * tension;
      cpy1 = sy;
      cpx2 = tx - hsign * tension;
      cpy2 = ty;
    }
    return new EdgePorts(sx, sy, tx, ty, cpx1, cpy1, cpx2, cpy2);
  }

  private NodeGeom findGeom(String tableName) {
    for (var entry : geomMap.entrySet()) {
      if (entry.getKey().equalsIgnoreCase(tableName)) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static Polygon makeArrow(double ox, double oy, double tx, double ty, Color fill) {
    double dx = tx - ox, dy = ty - oy;
    double len = Math.hypot(dx, dy);
    if (len < 1) {
      return new Polygon();
    }
    double ux = dx / len, uy = dy / len;
    double size = 11, half = 4.5;
    double px = -uy * half, py = ux * half;
    var p =
        new Polygon(
            tx,
            ty,
            tx - ux * size + px,
            ty - uy * size + py,
            tx - ux * size - px,
            ty - uy * size - py);
    p.setFill(fill);
    return p;
  }

  // ── Hover ─────────────────────────────────────────────────────────────────

  private void setupHover() {
    for (var nodeViz : nodeVizList) {
      String name = nodeViz.name();

      var connEdgeIdx = new ArrayList<Integer>();
      var connNames = new HashSet<String>();
      connNames.add(name.toLowerCase());

      for (int ei = 0; ei < edgeVizList.size(); ei++) {
        var ev = edgeVizList.get(ei);
        if (ev.from().equalsIgnoreCase(name) || ev.to().equalsIgnoreCase(name)) {
          connEdgeIdx.add(ei);
          connNames.add(ev.from().toLowerCase());
          connNames.add(ev.to().toLowerCase());
        }
      }

      nodeViz
          .overlay()
          .setOnMouseEntered(
              e -> {
                for (var nv : nodeVizList) {
                  double op = connNames.contains(nv.name().toLowerCase()) ? 1.0 : 0.10;
                  nv.all().forEach(s -> s.setOpacity(op));
                }
                for (int ei = 0; ei < edgeVizList.size(); ei++) {
                  double op = connEdgeIdx.contains(ei) ? 1.0 : 0.06;
                  edgeVizList.get(ei).shapes().forEach(s -> s.setOpacity(op));
                }
              });
      nodeViz
          .overlay()
          .setOnMouseExited(
              e -> {
                nodeVizList.forEach(nv -> nv.all().forEach(s -> s.setOpacity(1.0)));
                edgeVizList.forEach(ev -> ev.shapes().forEach(s -> s.setOpacity(1.0)));
              });
    }
  }

  // ── Empty state ───────────────────────────────────────────────────────────

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
