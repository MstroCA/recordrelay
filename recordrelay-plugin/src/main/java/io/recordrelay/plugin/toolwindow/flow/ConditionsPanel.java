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

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import io.recordrelay.cli.flow.QueryFlowModel;
import io.recordrelay.cli.flow.QueryFlowModel.NodeEntry;
import io.recordrelay.cli.flow.QueryFlowModel.OrderByEntry;
import io.recordrelay.cli.flow.QueryFlowModel.WhereFilter;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Right-side panel in the flow builder for configuring WHERE filters, ORDER BY, and row limit.
 *
 * <p>Rebuilds its content when the model changes (new tables added / removed). Each WHERE row maps
 * directly to a {@link WhereFilter} entry in the model.
 */
public final class ConditionsPanel extends JScrollPane {

  private static final String[] OPERATORS = {
    "=", "!=", "<", ">", "<=", ">=", "LIKE", "IS NULL", "IS NOT NULL"
  };

  private static final JBColor SECTION_BG = new JBColor(0xF0F4FA, 0x2B2D30);
  private static final JBColor SECTION_TITLE = new JBColor(0x1565C0, 0x64B5F6);
  private static final JBColor ADD_BTN = new JBColor(0x42A5F5, 0x1565C0);
  private static final JBColor ROW_BG = new JBColor(Color.WHITE, new Color(40, 42, 45));
  private static final JBColor ROW_BORDER = new JBColor(0xD0D7E2, 0x4A4D52);

  private final QueryFlowModel model;
  private final JPanel body = new JPanel();

  /** Creates the conditions panel bound to the given model. */
  public ConditionsPanel(QueryFlowModel model) {
    this.model = model;
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setBorder(JBUI.Borders.empty(8));
    body.setBackground(SECTION_BG);

    setViewportView(body);
    setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
    setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
    setBorder(null);
    setPreferredSize(new Dimension(200, 0));

    model.addChangeListener(this::rebuild);
    rebuild();
  }

  // ── Rebuild ────────────────────────────────────────────────────────────────────

  private void rebuild() {
    body.removeAll();

    body.add(buildSectionTitle("WHERE"));
    body.add(Box.createVerticalStrut(4));
    for (int i = 0; i < model.filters().size(); i++) {
      body.add(buildFilterRow(model.filters().get(i), i));
      body.add(Box.createVerticalStrut(2));
    }
    body.add(buildAddButton("+ Filter", this::addEmptyFilter));
    body.add(Box.createVerticalStrut(12));

    body.add(buildSectionTitle("ORDER BY"));
    body.add(Box.createVerticalStrut(4));
    for (int i = 0; i < model.orderBys().size(); i++) {
      body.add(buildOrderByRow(model.orderBys().get(i), i));
      body.add(Box.createVerticalStrut(2));
    }
    body.add(buildAddButton("+ Order", this::addEmptyOrderBy));
    body.add(Box.createVerticalStrut(12));

    body.add(buildSectionTitle("LIMIT"));
    body.add(Box.createVerticalStrut(4));
    body.add(buildLimitRow());
    body.add(Box.createVerticalGlue());

    body.revalidate();
    body.repaint();
  }

  // ── Section helpers ────────────────────────────────────────────────────────────

  private JLabel buildSectionTitle(String text) {
    var lbl = new JLabel(text);
    lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(10f)));
    lbl.setForeground(SECTION_TITLE);
    lbl.setAlignmentX(LEFT_ALIGNMENT);
    return lbl;
  }

  private JButton buildAddButton(String text, Runnable action) {
    var btn = new JButton(text);
    btn.setFont(btn.getFont().deriveFont(JBUI.scaleFontSize(10f)));
    btn.setForeground(ADD_BTN);
    btn.setContentAreaFilled(false);
    btn.setBorderPainted(false);
    btn.setFocusPainted(false);
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btn.setAlignmentX(LEFT_ALIGNMENT);
    btn.addActionListener(e -> action.run());
    return btn;
  }

  private JButton buildRemoveButton(Runnable action) {
    var btn = new JButton("×");
    btn.setFont(btn.getFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(10f)));
    btn.setForeground(JBColor.RED);
    btn.setContentAreaFilled(false);
    btn.setBorderPainted(false);
    btn.setFocusPainted(false);
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btn.addActionListener(e -> action.run());
    return btn;
  }

  // ── Filter row ─────────────────────────────────────────────────────────────────

  private JPanel buildFilterRow(WhereFilter filter, int index) {
    var panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBackground(ROW_BG);
    panel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(ROW_BORDER, 1, true), JBUI.Borders.empty(4, 6)));
    panel.setAlignmentX(LEFT_ALIGNMENT);

    var colCombo = buildColumnCombo(filter.tableAlias() + "." + filter.column());
    var opCombo = new ComboBox<>(OPERATORS);
    opCombo.setSelectedItem(filter.operator());
    opCombo.setFont(opCombo.getFont().deriveFont(JBUI.scaleFontSize(10f)));

    var valField = new JTextField(filter.value(), 8);
    valField.setFont(valField.getFont().deriveFont(JBUI.scaleFontSize(10f)));
    valField.setVisible(!filter.operator().startsWith("IS"));

    opCombo.addActionListener(e -> {
      String op = (String) opCombo.getSelectedItem();
      valField.setVisible(op != null && !op.startsWith("IS"));
      panel.revalidate();
      updateFilter(index, colCombo, opCombo, valField);
    });
    colCombo.addActionListener(e -> updateFilter(index, colCombo, opCombo, valField));
    attachDocListener(valField, () -> updateFilter(index, colCombo, opCombo, valField));

    var row1 = rowPanel(panel.getBackground());
    row1.add(colCombo);
    row1.add(buildRemoveButton(() -> model.removeFilter(index)));

    var row2 = rowPanel(panel.getBackground());
    row2.add(opCombo);
    row2.add(valField);

    panel.add(row1);
    panel.add(row2);
    return panel;
  }

  private static JPanel rowPanel(Color bg) {
    var row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    row.setBackground(bg);
    row.setOpaque(true);
    return row;
  }

  private static void attachDocListener(JTextField field, Runnable action) {
    field.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        action.run();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        action.run();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        action.run();
      }
    });
  }

  private void updateFilter(int index, ComboBox<String> colCombo,
      ComboBox<String> opCombo, JTextField valField) {
    String colFull = (String) colCombo.getSelectedItem();
    String op = (String) opCombo.getSelectedItem();
    if (colFull == null || op == null) {
      return;
    }
    String[] parts = colFull.split("\\.", 2);
    if (parts.length < 2) {
      return;
    }
    if (index < model.filters().size()) {
      model.removeFilter(index);
      model.addFilter(parts[0], parts[1], op, valField.getText());
    }
  }

  // ── ORDER BY row ───────────────────────────────────────────────────────────────

  private JPanel buildOrderByRow(OrderByEntry ob, int index) {
    var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    panel.setBackground(ROW_BG);
    panel.setBorder(BorderFactory.createLineBorder(ROW_BORDER, 1, true));
    panel.setAlignmentX(LEFT_ALIGNMENT);

    var colCombo = buildColumnCombo(ob.tableAlias() + "." + ob.column());
    var dirCombo = new ComboBox<>(new String[]{"ASC", "DESC"});
    dirCombo.setSelectedItem(ob.ascending() ? "ASC" : "DESC");
    dirCombo.setFont(dirCombo.getFont().deriveFont(JBUI.scaleFontSize(10f)));

    java.awt.event.ActionListener onChange = e -> {
      String colFull = (String) colCombo.getSelectedItem();
      String dir = (String) dirCombo.getSelectedItem();
      if (colFull == null || dir == null) {
        return;
      }
      String[] parts = colFull.split("\\.", 2);
      if (parts.length >= 2) {
        model.removeOrderBy(index);
        model.addOrderBy(parts[0], parts[1], "ASC".equals(dir));
      }
    };
    colCombo.addActionListener(onChange);
    dirCombo.addActionListener(onChange);

    panel.add(colCombo);
    panel.add(dirCombo);
    panel.add(buildRemoveButton(() -> model.removeOrderBy(index)));
    return panel;
  }

  // ── LIMIT row ──────────────────────────────────────────────────────────────────

  private JPanel buildLimitRow() {
    var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    panel.setBackground(SECTION_BG);
    panel.setAlignmentX(LEFT_ALIGNMENT);

    var spinner = new JSpinner(new SpinnerNumberModel(model.getLimit(), 0, 100000, 10));
    spinner.setFont(spinner.getFont().deriveFont(JBUI.scaleFontSize(10f)));
    spinner.setPreferredSize(new Dimension(80, 24));
    spinner.addChangeListener(e -> model.setLimit((Integer) spinner.getValue()));

    panel.add(new JLabel("rows:"));
    panel.add(spinner);
    return panel;
  }

  // ── Column combo helper ────────────────────────────────────────────────────────

  private ComboBox<String> buildColumnCombo(String selected) {
    var cols = new ArrayList<String>();
    for (NodeEntry node : model.nodes()) {
      for (var col : node.columns()) {
        cols.add(node.alias() + "." + col.name());
      }
    }
    var combo = new ComboBox<>(cols.toArray(new String[0]));
    combo.setSelectedItem(selected);
    combo.setFont(combo.getFont().deriveFont(JBUI.scaleFontSize(10f)));
    combo.setPreferredSize(new Dimension(130, 22));
    return combo;
  }

  // ── Empty-add helpers ──────────────────────────────────────────────────────────

  private void addEmptyFilter() {
    if (model.nodes().isEmpty()) {
      return;
    }
    var first = model.nodes().get(0);
    String col = first.columns().isEmpty() ? "" : first.columns().get(0).name();
    model.addFilter(first.alias(), col, "=", "");
  }

  private void addEmptyOrderBy() {
    if (model.nodes().isEmpty()) {
      return;
    }
    var first = model.nodes().get(0);
    String col = first.columns().isEmpty() ? "" : first.columns().get(0).name();
    model.addOrderBy(first.alias(), col, true);
  }
}
