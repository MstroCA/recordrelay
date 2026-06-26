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
package io.recordrelay.plugin.toolwindow;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import io.recordrelay.cli.engine.ConnProfileResolver;
import io.recordrelay.cli.engine.DiscoveryEngine;
import io.recordrelay.cli.engine.QueryRunner;
import io.recordrelay.cli.flow.QueryFlowModel;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.QueryResult;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.i18n.Messages;
import io.recordrelay.plugin.service.RecordRelayService;
import io.recordrelay.plugin.toolwindow.flow.ConditionsPanel;
import io.recordrelay.plugin.toolwindow.flow.FlowCanvas;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.table.DefaultTableModel;
import org.jetbrains.annotations.NotNull;

/**
 * Tool-window panel for running read-only SELECT queries.
 *
 * <p>Offers two modes toggled by a button in the toolbar:
 *
 * <ul>
 *   <li><b>Flow mode</b> – visual query builder: drag tables onto a canvas, check columns, connect
 *       them with JOIN lines, add WHERE/ORDER BY conditions. SQL is generated automatically.
 *   <li><b>SQL mode</b> – classic text-area for hand-written queries.
 * </ul>
 */
public final class QueryPanel extends JPanel {

  private static final String CARD_FLOW = "flow";
  private static final String CARD_SQL = "sql";
  private static final int NODE_SPACING = 240;

  private final Project project;

  // ── Shared toolbar widgets ─────────────────────────────────────────────────────
  private final ComboBox<String> cmbConn = new ComboBox<>();
  private final ComboBox<DatabaseRef> cmbDb = new ComboBox<>();
  private final JToggleButton btnFlow = new JToggleButton("Flow");
  private final JToggleButton btnSql = new JToggleButton("SQL");
  private final JButton btnRun = new JButton(Messages.get("query.btn.run"));
  private final JLabel lblStatus = new JLabel(Messages.get("status.ready"));

  // ── Flow-mode components ──────────────────────────────────────────────────────
  private final QueryFlowModel flowModel = new QueryFlowModel();
  private final FlowCanvas flowCanvas = new FlowCanvas(flowModel);
  private final ConditionsPanel conditionsPanel = new ConditionsPanel(flowModel);
  private final DefaultListModel<TableRef> tableListModel = new DefaultListModel<>();
  private final JBList<TableRef> tableList = new JBList<>(tableListModel);
  private final JBTextArea taSqlPreview = new JBTextArea(4, 0);
  private int nextNodeX = 20;

  // ── SQL-mode components ───────────────────────────────────────────────────────
  private final JBTextArea taSql = new JBTextArea(6, 60);

  // ── Results (shared) ──────────────────────────────────────────────────────────
  private final DefaultTableModel tableModel =
      new DefaultTableModel(0, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
          return false;
        }
      };
  private final JBTable tblResults = new JBTable(tableModel);

  private final CardLayout cardLayout = new CardLayout();
  private final JPanel modeCard = new JPanel(cardLayout);

  /** Creates the Query Analyzer panel bound to the given project. */
  public QueryPanel(Project project) {
    this.project = project;
    setLayout(new BorderLayout(0, 0));

    flowModel.addChangeListener(this::syncSqlPreview);

    add(
        new PanelHeader(
            "Query Analyzer", "Visual flow builder or hand-written SQL — toggled by the toolbar"),
        BorderLayout.NORTH);
    add(buildBody(), BorderLayout.CENTER);

    loadConnections();

    cmbConn.addActionListener(
        e -> {
          cmbDb.removeAllItems();
          flowModel.clear();
          flowCanvas.clear();
          tableListModel.clear();
          nextNodeX = 20;
          loadDatabases();
        });
    cmbDb.addActionListener(e -> loadTables());

    btnFlow.addActionListener(e -> switchMode(true));
    btnSql.addActionListener(e -> switchMode(false));
    btnRun.addActionListener(e -> onRun());
    tableList.addMouseListener(
        new java.awt.event.MouseAdapter() {
          @Override
          public void mouseClicked(java.awt.event.MouseEvent e) {
            if (e.getClickCount() == 2) {
              onAddTableToCanvas();
            }
          }
        });

    switchMode(true);
  }

  // ── Layout ────────────────────────────────────────────────────────────────────

  private JPanel buildBody() {
    var outer = new JPanel(new BorderLayout(0, 0));
    outer.add(buildToolbar(), BorderLayout.NORTH);

    modeCard.add(buildFlowPanel(), CARD_FLOW);
    modeCard.add(buildSqlPanel(), CARD_SQL);

    var resultScroll = new JBScrollPane(tblResults);
    var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, modeCard, buildBottomPanel(resultScroll));
    split.setResizeWeight(0.65);
    split.setDividerSize(5);

    outer.add(split, BorderLayout.CENTER);
    outer.add(buildStatusBar(), BorderLayout.SOUTH);
    return outer;
  }

  private JPanel buildToolbar() {
    var bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new JBColor(0xD0D7E2, 0x4A4D52)));

    cmbDb.setRenderer(
        SimpleListCellRenderer.create(
            (renderer, db, idx) -> renderer.setText(db != null ? db.name() : "— database —")));

    bar.add(new JLabel("Conn:"));
    bar.add(cmbConn);
    bar.add(new JLabel("DB:"));
    bar.add(cmbDb);
    bar.add(Box.createHorizontalStrut(8));

    btnFlow.setFocusPainted(false);
    btnSql.setFocusPainted(false);
    styleToggle(btnFlow);
    styleToggle(btnSql);
    bar.add(btnFlow);
    bar.add(btnSql);
    bar.add(Box.createHorizontalStrut(8));
    bar.add(btnRun);
    return bar;
  }

  private JPanel buildFlowPanel() {
    // Left: table picker
    var pickerPanel = buildTablePicker();
    // Center: canvas
    var canvasScroll = new JBScrollPane(flowCanvas);
    canvasScroll.setHorizontalScrollBarPolicy(JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    canvasScroll.setVerticalScrollBarPolicy(JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    canvasScroll.setBorder(null);
    // Right: conditions
    var centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvasScroll, conditionsPanel);
    centerSplit.setResizeWeight(0.75);
    centerSplit.setDividerSize(4);

    var outerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, pickerPanel, centerSplit);
    outerSplit.setDividerLocation(160);
    outerSplit.setDividerSize(4);

    return wrapInPanel(outerSplit);
  }

  private JPanel buildTablePicker() {
    var panel = new JPanel(new BorderLayout(0, 4));
    panel.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, new JBColor(0xD0D7E2, 0x4A4D52)),
            JBUI.Borders.empty(6)));
    panel.setPreferredSize(new Dimension(160, 0));

    var title = new JLabel("Tables");
    title.setFont(title.getFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(11f)));
    title.setForeground(new JBColor(0x1565C0, 0x64B5F6));
    panel.add(title, BorderLayout.NORTH);

    tableList.setCellRenderer(
        new javax.swing.DefaultListCellRenderer() {
          @Override
          public java.awt.Component getListCellRendererComponent(
              javax.swing.JList<?> list, Object value, int index, boolean sel, boolean focus) {
            var lbl = (JLabel) super.getListCellRendererComponent(list, value, index, sel, focus);
            if (value instanceof TableRef t) {
              lbl.setText(t.tableName());
              lbl.setFont(lbl.getFont().deriveFont(JBUI.scaleFontSize(11f)));
              lbl.setBorder(JBUI.Borders.empty(2, 4));
            }
            return lbl;
          }
        });
    tableList.setToolTipText("Double-click to add table to canvas");
    panel.add(new JBScrollPane(tableList), BorderLayout.CENTER);

    var addBtn = new JButton("+ Add");
    addBtn.setFont(addBtn.getFont().deriveFont(JBUI.scaleFontSize(10f)));
    addBtn.setForeground(new JBColor(0x1565C0, 0x64B5F6));
    addBtn.setContentAreaFilled(false);
    addBtn.setBorderPainted(false);
    addBtn.setFocusPainted(false);
    addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    addBtn.addActionListener(e -> onAddTableToCanvas());
    panel.add(addBtn, BorderLayout.SOUTH);

    return panel;
  }

  private JPanel buildSqlPanel() {
    taSql.setLineWrap(false);
    taSql.setFont(new Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(12)));
    var scroll = new JBScrollPane(taSql);
    scroll.setBorder(BorderFactory.createTitledBorder("SQL"));
    return wrapInPanel(scroll);
  }

  private JPanel buildBottomPanel(JBScrollPane resultScroll) {
    var panel = new JPanel(new BorderLayout(0, 0));

    taSqlPreview.setEditable(false);
    taSqlPreview.setLineWrap(false);
    taSqlPreview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(11)));
    taSqlPreview.setBackground(new JBColor(0xF8FAFD, 0x1A1C1E));
    taSqlPreview.setForeground(new JBColor(0x1565C0, 0x64B5F6));
    var previewScroll = new JBScrollPane(taSqlPreview);
    previewScroll.setBorder(BorderFactory.createTitledBorder("Generated SQL"));

    var sqlSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, previewScroll, resultScroll);
    sqlSplit.setResizeWeight(0.3);
    sqlSplit.setDividerSize(4);
    panel.add(sqlSplit, BorderLayout.CENTER);
    return panel;
  }

  private JPanel buildStatusBar() {
    var bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
    bar.add(lblStatus);
    return bar;
  }

  private static JPanel wrapInPanel(java.awt.Component comp) {
    var p = new JPanel(new BorderLayout());
    p.add(comp, BorderLayout.CENTER);
    return p;
  }

  // ── Mode switching ─────────────────────────────────────────────────────────────

  private void switchMode(boolean flow) {
    btnFlow.setSelected(flow);
    btnSql.setSelected(!flow);
    activateToggle(btnFlow, flow);
    activateToggle(btnSql, !flow);
    cardLayout.show(modeCard, flow ? CARD_FLOW : CARD_SQL);
    syncSqlPreview();
  }

  private boolean isFlowMode() {
    return btnFlow.isSelected();
  }

  private void syncSqlPreview() {
    taSqlPreview.setText(flowModel.toSql());
  }

  // ── Data loading ───────────────────────────────────────────────────────────────

  private void loadConnections() {
    cmbConn.removeAllItems();
    try {
      RecordRelayService.getInstance()
          .configStore()
          .load()
          .getConnections()
          .keySet()
          .forEach(cmbConn::addItem);
    } catch (Exception e) {
      lblStatus.setText("Config error: " + e.getMessage());
    }
  }

  private void loadDatabases() {
    String conn = (String) cmbConn.getSelectedItem();
    if (conn == null || conn.isBlank()) {
      return;
    }
    var ref = new AtomicReference<List<DatabaseRef>>();
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "Loading databases…", false) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                try {
                  var profile = RecordRelayService.getInstance().resolver().resolve(conn);
                  ref.set(new DiscoveryEngine().discoverDatabases(profile));
                } catch (Exception ex) {
                  ref.set(List.of());
                }
              }

              @Override
              public void onSuccess() {
                cmbDb.removeAllItems();
                if (ref.get() != null) {
                  ref.get().forEach(cmbDb::addItem);
                }
              }
            });
  }

  private void loadTables() {
    String conn = (String) cmbConn.getSelectedItem();
    var db = (DatabaseRef) cmbDb.getSelectedItem();
    if (conn == null || db == null) {
      return;
    }
    tableListModel.clear();
    var ref = new AtomicReference<List<TableRef>>();
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "Loading tables…", false) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                try {
                  var profile = RecordRelayService.getInstance().resolver().resolve(conn);
                  ref.set(new DiscoveryEngine().discoverTables(profile, db));
                } catch (Exception ex) {
                  ref.set(List.of());
                }
              }

              @Override
              public void onSuccess() {
                tableListModel.clear();
                if (ref.get() != null) {
                  ref.get().forEach(tableListModel::addElement);
                }
              }
            });
  }

  // ── Flow: add table to canvas ──────────────────────────────────────────────────

  private void onAddTableToCanvas() {
    var selected = tableList.getSelectedValue();
    if (selected == null) {
      return;
    }
    String conn = (String) cmbConn.getSelectedItem();
    if (conn == null) {
      return;
    }
    int posX = nextNodeX;
    int posY = 20;
    nextNodeX += NODE_SPACING;

    var ref = new AtomicReference<io.recordrelay.core.domain.ColumnMeta[]>();
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "Loading columns…", false) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                try {
                  var profile = RecordRelayService.getInstance().resolver().resolve(conn);
                  var cols = new DiscoveryEngine().inspectColumns(profile, selected);
                  ref.set(cols.toArray(new io.recordrelay.core.domain.ColumnMeta[0]));
                } catch (Exception ex) {
                  ref.set(new io.recordrelay.core.domain.ColumnMeta[0]);
                }
              }

              @Override
              public void onSuccess() {
                if (ref.get() == null) {
                  return;
                }
                var entry = flowModel.addNode(selected, List.of(ref.get()));
                flowCanvas.addTableNode(entry, posX, posY);
              }
            });
  }

  // ── Run query ──────────────────────────────────────────────────────────────────

  private void onRun() {
    String conn = (String) cmbConn.getSelectedItem();
    String sql = isFlowMode() ? flowModel.toSql().trim() : taSql.getText().trim();

    if (conn == null || conn.isBlank()) {
      lblStatus.setText("Select a connection first.");
      return;
    }
    if (sql.isBlank() || sql.startsWith("--")) {
      lblStatus.setText("Build a query or enter SQL first.");
      return;
    }

    btnRun.setEnabled(false);
    lblStatus.setText("Running…");
    tableModel.setRowCount(0);
    tableModel.setColumnCount(0);

    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "Query Analyzer", false) {
              QueryResult result;
              String errorMsg;

              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                  var resolver =
                      new ConnProfileResolver(RecordRelayService.getInstance().configStore());
                  var profile = resolver.resolve(conn);
                  result = new QueryRunner().run(profile, sql);
                } catch (Exception ex) {
                  errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName();
                }
              }

              @Override
              public void onSuccess() {
                if (errorMsg != null) {
                  lblStatus.setText(Messages.get("query.error", errorMsg));
                } else {
                  applyResult(result);
                }
                btnRun.setEnabled(true);
              }

              @Override
              public void onThrowable(@NotNull Throwable t) {
                lblStatus.setText(Messages.get("query.error", t.getMessage()));
                btnRun.setEnabled(true);
              }
            });
  }

  private void applyResult(QueryResult result) {
    var colVec = new Vector<>(result.columns());
    tableModel.setColumnIdentifiers(colVec);
    tableModel.setRowCount(0);
    for (var row : result.rows()) {
      tableModel.addRow(new Vector<>(row));
    }
    lblStatus.setText(Messages.get("query.rows", result.rowCount()));
  }

  // ── Toggle styling ─────────────────────────────────────────────────────────────

  private static void styleToggle(JToggleButton btn) {
    btn.setFocusPainted(false);
    btn.setFont(btn.getFont().deriveFont(JBUI.scaleFontSize(11f)));
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    activateToggle(btn, btn.isSelected());
  }

  private static void activateToggle(JToggleButton btn, boolean active) {
    if (active) {
      btn.setBackground(new JBColor(0x1E88E5, 0x1565C0));
      btn.setForeground(Color.WHITE);
      btn.setOpaque(true);
      btn.setBorder(JBUI.Borders.empty(4, 10));
    } else {
      btn.setBackground(null);
      btn.setForeground(new JBColor(0x444444, 0xAAAAAA));
      btn.setOpaque(false);
      btn.setBorder(JBUI.Borders.empty(4, 10));
    }
  }
}
