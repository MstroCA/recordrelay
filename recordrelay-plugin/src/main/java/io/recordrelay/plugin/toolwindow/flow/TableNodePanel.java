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

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import io.recordrelay.cli.flow.QueryFlowModel;
import io.recordrelay.cli.flow.QueryFlowModel.NodeEntry;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A draggable card representing one database table on the {@link FlowCanvas}.
 *
 * <p>Displays the table alias + name in the header, lists each column as a checkbox row, and
 * exposes a small "link" port button on the right of each row for creating JOIN connections.
 */
final class TableNodePanel extends JPanel {

  static final int NODE_WIDTH = 200;
  static final int HEADER_H = 30;
  static final int ROW_H = 22;

  private static final JBColor HEADER_TOP = new JBColor(0x1E88E5, 0x1565C0);
  private static final JBColor HEADER_BOT = new JBColor(0x1565C0, 0x0D47A1);
  private static final JBColor CARD_BG = new JBColor(0xFFFFFF, 0x2B2D30);
  private static final JBColor CARD_BORDER = new JBColor(0xC9D3E0, 0x4A4D52);
  private static final JBColor PORT_COLOR = new JBColor(0x42A5F5, 0x64B5F6);
  private static final JBColor PORT_ACTIVE = new JBColor(0xFF7043, 0xFF8A65);

  private final NodeEntry node;
  private final QueryFlowModel model;
  private final BiConsumer<String, String> onPortClick;
  private final Map<String, JCheckBox> checkboxes = new LinkedHashMap<>();

  private boolean portHighlight = false;

  TableNodePanel(
      NodeEntry node,
      QueryFlowModel model,
      BiConsumer<String, String> onPortClick,
      Runnable onRemove) {
    this.node = node;
    this.model = model;
    this.onPortClick = onPortClick;

    setLayout(new BorderLayout());
    setPreferredSize(new Dimension(NODE_WIDTH, HEADER_H + node.columns().size() * ROW_H + 4));
    setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1, true));
    setBackground(CARD_BG);
    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

    add(buildHeader(onRemove), BorderLayout.NORTH);
    add(buildColumnList(), BorderLayout.CENTER);
  }

  // ── Header ───────────────────────────────────────────────────────────────────

  private JPanel buildHeader(Runnable onRemove) {
    var header =
        new JPanel(new BorderLayout()) {
          @Override
          protected void paintComponent(Graphics g) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, HEADER_TOP, 0, getHeight(), HEADER_BOT));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
          }
        };
    header.setOpaque(false);
    header.setPreferredSize(new Dimension(NODE_WIDTH, HEADER_H));
    header.setBorder(JBUI.Borders.empty(4, 8, 4, 4));

    var aliasLabel = new JLabel("[" + node.alias() + "] " + node.table().tableName());
    aliasLabel.setFont(aliasLabel.getFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(11f)));
    aliasLabel.setForeground(Color.WHITE);
    header.add(aliasLabel, BorderLayout.CENTER);

    var closeBtn = new JButton("×");
    closeBtn.setFont(closeBtn.getFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f)));
    closeBtn.setForeground(Color.WHITE);
    closeBtn.setContentAreaFilled(false);
    closeBtn.setBorderPainted(false);
    closeBtn.setFocusPainted(false);
    closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    closeBtn.setPreferredSize(new Dimension(20, 20));
    closeBtn.addActionListener(e -> onRemove.run());
    header.add(closeBtn, BorderLayout.EAST);

    return header;
  }

  // ── Column list ──────────────────────────────────────────────────────────────

  private JPanel buildColumnList() {
    var colPanel = new JPanel();
    colPanel.setLayout(new BoxLayout(colPanel, BoxLayout.Y_AXIS));
    colPanel.setBackground(CARD_BG);
    colPanel.setOpaque(true);

    for (var col : node.columns()) {
      colPanel.add(buildColumnRow(col.name(), col.nativeType(), col.primaryKey()));
    }
    return colPanel;
  }

  private JPanel buildColumnRow(String colName, String colType, boolean isPk) {
    var row = new JPanel(new BorderLayout(2, 0));
    row.setBackground(CARD_BG);
    row.setOpaque(true);
    row.setMaximumSize(new Dimension(NODE_WIDTH, ROW_H));
    row.setPreferredSize(new Dimension(NODE_WIDTH, ROW_H));
    row.setBorder(JBUI.Borders.empty(0, 4, 0, 2));

    var cb = new JCheckBox();
    cb.setSelected(node.isSelected(colName));
    cb.setBackground(CARD_BG);
    cb.setOpaque(true);
    cb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    cb.addActionListener(e -> model.toggleColumn(node.alias(), colName));
    checkboxes.put(colName, cb);

    String pkPrefix = isPk ? "🔑 " : "";
    var nameLabel = new JLabel(pkPrefix + colName);
    nameLabel.setFont(nameLabel.getFont().deriveFont(JBUI.scaleFontSize(10f)));

    var typeLabel = new JLabel(colType);
    typeLabel.setFont(typeLabel.getFont().deriveFont(Font.ITALIC, JBUI.scaleFontSize(9f)));
    typeLabel.setForeground(JBColor.GRAY);

    var left = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    left.setBackground(CARD_BG);
    left.setOpaque(true);
    left.add(cb);
    left.add(nameLabel);
    left.add(typeLabel);
    row.add(left, BorderLayout.CENTER);

    var portBtn = new PortButton(colName);
    row.add(portBtn, BorderLayout.EAST);

    return row;
  }

  // ── Port logic ────────────────────────────────────────────────────────────────

  /** Highlights all port buttons (shown when canvas is in connect-target mode). */
  void setPortHighlight(boolean highlight) {
    this.portHighlight = highlight;
    repaint();
  }

  /**
   * Returns the center-right point of the port for a given column, in coordinates relative to this
   * panel. Used by {@link FlowCanvas} to draw connection curves.
   */
  Point getPortPoint(String colName) {
    int idx = indexOfColumn(colName);
    int y = HEADER_H + idx * ROW_H + ROW_H / 2;
    return new Point(NODE_WIDTH - 2, y);
  }

  private int indexOfColumn(String colName) {
    int idx = 0;
    for (var col : node.columns()) {
      if (col.name().equals(colName)) {
        return idx;
      }
      idx++;
    }
    return 0;
  }

  NodeEntry nodeEntry() {
    return node;
  }

  // ── Port button ──────────────────────────────────────────────────────────────

  private final class PortButton extends JPanel {
    private final String colName;
    private boolean hovered = false;

    PortButton(String colName) {
      this.colName = colName;
      setPreferredSize(new Dimension(16, ROW_H));
      setBackground(CARD_BG);
      setOpaque(true);
      setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

      addMouseListener(
          new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
              hovered = true;
              repaint();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
              hovered = false;
              repaint();
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
              onPortClick.accept(node.alias(), colName);
            }
          });
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      var g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int cx = getWidth() / 2;
      int cy = getHeight() / 2;
      int r = 4;
      g2.setColor(portHighlight || hovered ? PORT_ACTIVE : PORT_COLOR);
      g2.fillOval(cx - r, cy - r, r * 2, r * 2);
      g2.setColor(portHighlight || hovered ? PORT_ACTIVE.darker() : PORT_COLOR.darker());
      g2.drawOval(cx - r, cy - r, r * 2, r * 2);
      g2.dispose();
    }
  }
}
