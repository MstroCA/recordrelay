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
package io.recordrelay.plugin.toolwindow.flow;

import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.JBColor;
import io.recordrelay.cli.flow.QueryFlowModel;
import io.recordrelay.cli.flow.QueryFlowModel.JoinType;
import io.recordrelay.cli.flow.QueryFlowModel.NodeEntry;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.SwingUtilities;

/**
 * Canvas for the visual query flow builder.
 *
 * <p>Table nodes ({@link TableNodePanel}) are placed as children with absolute positions. JOIN
 * connections are drawn as Bezier curves between column ports in {@code paintComponent}. Supports
 * drag-to-move nodes and click-to-connect ports for building JOIN relationships.
 */
public final class FlowCanvas extends javax.swing.JPanel {

  private static final int MIN_W = 800;
  private static final int MIN_H = 520;
  private static final int GRID = 20;
  private static final Stroke CONNECTION_STROKE = new BasicStroke(2f);
  private static final Stroke PENDING_STROKE =
      new BasicStroke(
          1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] {6f, 4f}, 0f);

  private static final JBColor CANVAS_BG = new JBColor(0xF5F7FA, 0x1E1F22);
  private static final JBColor GRID_DOT = new JBColor(0xDDE3EC, 0x3A3C40);
  private static final JBColor JOIN_COLOR = new JBColor(0x42A5F5, 0x64B5F6);
  private static final JBColor PENDING_COLOR = new JBColor(0xFF7043, 0xFF8A65);

  private final QueryFlowModel model;
  private final Map<String, TableNodePanel> nodeMap = new LinkedHashMap<>();

  // Drag state
  private TableNodePanel dragging;
  private Point dragOffset;

  // Connect-mode state
  private String pendingFromAlias;
  private String pendingFromCol;
  private Point mousePos;

  public FlowCanvas(QueryFlowModel model) {
    this.model = model;
    setLayout(null);
    setBackground(CANVAS_BG);

    var drag = new DragHandler();
    addMouseListener(drag);
    addMouseMotionListener(drag);

    model.addChangeListener(this::repaint);
  }

  // ── Public API ────────────────────────────────────────────────────────────────

  /**
   * Adds a new table node card at the given canvas position.
   *
   * @param entry the model entry returned by {@link QueryFlowModel#addNode}
   * @param x left edge in canvas coordinates
   * @param y top edge in canvas coordinates
   */
  public void addTableNode(NodeEntry entry, int x, int y) {
    var panel =
        new TableNodePanel(
            entry,
            model,
            this::onPortClick,
            () -> {
              model.removeNode(entry.alias());
              var removed = nodeMap.remove(entry.alias());
              if (removed != null) {
                remove(removed);
              }
              refreshNodePanelBounds();
              revalidate();
              repaint();
            });
    panel.setBounds(x, y, TableNodePanel.NODE_WIDTH, panel.getPreferredSize().height);
    nodeMap.put(entry.alias(), panel);
    add(panel);
    refreshNodePanelBounds();
    revalidate();
    repaint();
  }

  /** Removes all table nodes and resets connection state. */
  public void clear() {
    for (var panel : nodeMap.values()) {
      remove(panel);
    }
    nodeMap.clear();
    pendingFromAlias = null;
    pendingFromCol = null;
    revalidate();
    repaint();
  }

  // ── Layout ────────────────────────────────────────────────────────────────────

  @Override
  public Dimension getPreferredSize() {
    int maxX = MIN_W;
    int maxY = MIN_H;
    for (var panel : nodeMap.values()) {
      maxX = Math.max(maxX, panel.getX() + panel.getWidth() + 40);
      maxY = Math.max(maxY, panel.getY() + panel.getHeight() + 40);
    }
    return new Dimension(maxX, maxY);
  }

  // ── Painting ──────────────────────────────────────────────────────────────────

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    var g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    paintGrid(g2);
    paintConnections(g2);
    paintPendingConnection(g2);

    g2.dispose();
  }

  private void paintGrid(Graphics2D g2) {
    g2.setColor(GRID_DOT);
    for (int x = GRID; x < getWidth(); x += GRID) {
      for (int y = GRID; y < getHeight(); y += GRID) {
        g2.fillOval(x - 1, y - 1, 2, 2);
      }
    }
  }

  private void paintConnections(Graphics2D g2) {
    g2.setStroke(CONNECTION_STROKE);
    for (var join : model.joins()) {
      var fromPanel = nodeMap.get(join.fromAlias());
      var toPanel = nodeMap.get(join.toAlias());
      if (fromPanel == null || toPanel == null) {
        continue;
      }
      var fromPt =
          SwingUtilities.convertPoint(fromPanel, fromPanel.getPortPoint(join.fromColumn()), this);
      var toPt = SwingUtilities.convertPoint(toPanel, toPanel.getPortPoint(join.toColumn()), this);

      Color lineColor = joinColor(join.joinType());
      g2.setColor(lineColor);
      drawBezier(g2, fromPt, toPt);

      paintJoinLabel(g2, fromPt, toPt, join.joinType().keyword, lineColor);
    }
  }

  private void paintJoinLabel(Graphics2D g2, Point from, Point to, String label, Color color) {
    int mx = (from.x + to.x) / 2;
    int my = (from.y + to.y) / 2;
    var fm = g2.getFontMetrics();
    int w = fm.stringWidth(label) + 6;
    int h = fm.getHeight() + 2;
    g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 200));
    g2.fillRoundRect(mx - w / 2, my - h / 2, w, h, 6, 6);
    g2.setColor(Color.WHITE);
    g2.drawString(label, mx - w / 2 + 3, my + fm.getAscent() / 2);
  }

  private void paintPendingConnection(Graphics2D g2) {
    if (pendingFromAlias == null || mousePos == null) {
      return;
    }
    var fromPanel = nodeMap.get(pendingFromAlias);
    if (fromPanel == null) {
      return;
    }
    var fromPt =
        SwingUtilities.convertPoint(fromPanel, fromPanel.getPortPoint(pendingFromCol), this);

    g2.setStroke(PENDING_STROKE);
    g2.setColor(PENDING_COLOR);
    drawBezier(g2, fromPt, mousePos);

    g2.fillOval(fromPt.x - 4, fromPt.y - 4, 8, 8);
    g2.fillOval(mousePos.x - 4, mousePos.y - 4, 8, 8);
  }

  private static void drawBezier(Graphics2D g2, Point from, Point to) {
    int dx = Math.abs(to.x - from.x);
    int cpOffset = Math.max(60, dx / 2);
    var path = new GeneralPath();
    path.moveTo(from.x, from.y);
    path.curveTo(from.x + cpOffset, from.y, to.x - cpOffset, to.y, to.x, to.y);
    g2.draw(path);
  }

  private static Color joinColor(JoinType type) {
    return switch (type) {
      case INNER -> JOIN_COLOR;
      case LEFT -> new JBColor(0x66BB6A, 0x81C784);
      case RIGHT -> new JBColor(0xFFA726, 0xFFB74D);
    };
  }

  // ── Connect-mode ─────────────────────────────────────────────────────────────

  private void onPortClick(String alias, String colName) {
    if (pendingFromAlias == null) {
      pendingFromAlias = alias;
      pendingFromCol = colName;
      nodeMap.values().forEach(p -> p.setPortHighlight(!p.nodeEntry().alias().equals(alias)));
      setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    } else {
      if (!alias.equals(pendingFromAlias)) {
        finishConnection(alias, colName);
      }
      cancelConnect();
    }
  }

  private void finishConnection(String toAlias, String toCol) {
    var items = Arrays.stream(JoinType.values()).map(Enum::name).toList();
    JBPopupFactory.getInstance()
        .createPopupChooserBuilder(items)
        .setTitle("Join Type")
        .setItemChosenCallback(
            chosen ->
                model.addJoin(
                    pendingFromAlias, pendingFromCol, toAlias, toCol, JoinType.valueOf(chosen)))
        .createPopup()
        .showInFocusCenter();
  }

  private void cancelConnect() {
    pendingFromAlias = null;
    pendingFromCol = null;
    nodeMap.values().forEach(p -> p.setPortHighlight(false));
    setCursor(Cursor.getDefaultCursor());
    repaint();
  }

  // ── Drag handler ──────────────────────────────────────────────────────────────

  private final class DragHandler extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      if (pendingFromAlias != null) {
        cancelConnect();
        return;
      }
      var comp = getComponentAt(e.getPoint());
      dragging = findNodePanelAncestor(comp);
      if (dragging != null) {
        var panelPoint = SwingUtilities.convertPoint(FlowCanvas.this, e.getPoint(), dragging);
        dragOffset = panelPoint;
      }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      mousePos = e.getPoint();
      if (dragging != null) {
        int nx = e.getX() - dragOffset.x;
        int ny = e.getY() - dragOffset.y;
        nx = Math.max(0, snapToGrid(nx));
        ny = Math.max(0, snapToGrid(ny));
        dragging.setLocation(nx, ny);
        refreshNodePanelBounds();
        revalidate();
        repaint();
      } else if (pendingFromAlias != null) {
        repaint();
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      dragging = null;
      dragOffset = null;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      mousePos = e.getPoint();
      if (pendingFromAlias != null) {
        repaint();
      }
    }

    private TableNodePanel findNodePanelAncestor(java.awt.Component comp) {
      while (comp != null && comp != FlowCanvas.this) {
        if (comp instanceof TableNodePanel p) {
          return p;
        }
        comp = comp.getParent();
      }
      return null;
    }

    private int snapToGrid(int val) {
      return Math.round((float) val / GRID) * GRID;
    }
  }

  private void refreshNodePanelBounds() {
    for (var panel : nodeMap.values()) {
      panel.setBounds(
          panel.getX(), panel.getY(), TableNodePanel.NODE_WIDTH, panel.getPreferredSize().height);
    }
  }
}
