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
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import io.recordrelay.cli.engine.DiscoveryEngine;
import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.SchemaMatchReport;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.plugin.service.RecordRelayService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.table.DefaultTableModel;
import org.jetbrains.annotations.NotNull;

/** Tool-window panel for schema discovery: column inspection and cross-DB compatibility mapping. */
public final class DiscoveryPanel extends JPanel {

  private static final String[] COL_COLS = {"Column", "Type", "Nullable", "PK"};
  private static final String[] MAP_COLS = {
    "Source Column", "Target Column", "Compatible", "Warning"
  };

  private final Project project;

  // Source selectors
  private final ComboBox<String> cmbSrcConn = new ComboBox<>();
  private final ComboBox<DatabaseRef> cmbSrcDb = new ComboBox<>();
  private final ComboBox<TableRef> cmbSrcTable = new ComboBox<>();

  // Target selectors (for compatibility analysis)
  private final ComboBox<String> cmbTgtConn = new ComboBox<>();
  private final ComboBox<DatabaseRef> cmbTgtDb = new ComboBox<>();
  private final ComboBox<TableRef> cmbTgtTable = new ComboBox<>();

  private final JButton btnInspect = new JButton("Inspect Columns");
  private final JButton btnCompare = new JButton("Compare Tables");
  private final JLabel lblStatus = new JLabel(" ");

  private final DefaultTableModel colModel =
      new DefaultTableModel(COL_COLS, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
          return false;
        }
      };
  private final DefaultTableModel mapModel =
      new DefaultTableModel(MAP_COLS, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
          return false;
        }
      };

  /** Creates the panel for the given project. */
  public DiscoveryPanel(Project project) {
    super(new BorderLayout(0, 6));
    this.project = project;
    applyRenderers();

    var colTable = new JBTable(colModel);
    var colScroll = new JBScrollPane(colTable);
    colScroll.setBorder(BorderFactory.createTitledBorder("Columns"));

    var mapTable = new JBTable(mapModel);
    var mapScroll = new JBScrollPane(mapTable);
    mapScroll.setBorder(BorderFactory.createTitledBorder("Compatibility Map"));

    var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, colScroll, mapScroll);
    split.setResizeWeight(0.5);

    var north = new JPanel(new BorderLayout());
    north.add(
        new PanelHeader(
            "Discovery", "Inspect schema columns and analyse cross-database compatibility"),
        BorderLayout.NORTH);
    north.add(buildSelectionPanel(), BorderLayout.SOUTH);
    add(north, BorderLayout.NORTH);
    add(split, BorderLayout.CENTER);
    add(buildButtonBar(), BorderLayout.SOUTH);
    loadConnectionNames();
  }

  // ── Setup ──────────────────────────────────────────────────────────────────

  private void applyRenderers() {
    cmbSrcDb.setRenderer(
        SimpleListCellRenderer.create(
            (renderer, db, idx) ->
                renderer.setText(db != null ? db.name() : "— select database —")));
    cmbTgtDb.setRenderer(
        SimpleListCellRenderer.create(
            (renderer, db, idx) ->
                renderer.setText(db != null ? db.name() : "— select database —")));
    cmbSrcTable.setRenderer(
        SimpleListCellRenderer.create(
            (renderer, t, idx) ->
                renderer.setText(t != null ? t.tableName() : "— select table —")));
    cmbTgtTable.setRenderer(
        SimpleListCellRenderer.create(
            (renderer, t, idx) ->
                renderer.setText(t != null ? t.tableName() : "— select table —")));

    cmbSrcConn.addActionListener(e -> onConnSelected(true));
    cmbTgtConn.addActionListener(e -> onConnSelected(false));
    cmbSrcDb.addActionListener(e -> onDbSelected(true));
    cmbTgtDb.addActionListener(e -> onDbSelected(false));
  }

  private JPanel buildSelectionPanel() {
    var panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
    panel.add(buildConnGroup("Source", cmbSrcConn, cmbSrcDb, cmbSrcTable));
    panel.add(Box.createHorizontalStrut(16));
    panel.add(buildConnGroup("Target (for comparison)", cmbTgtConn, cmbTgtDb, cmbTgtTable));
    return panel;
  }

  private JPanel buildConnGroup(
      String label, ComboBox<String> conn, ComboBox<DatabaseRef> db, ComboBox<TableRef> table) {
    var grid = new JPanel(new GridLayout(4, 1, 0, 4));
    var lbl = new JLabel(label);
    lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
    grid.add(lbl);
    grid.add(conn);
    grid.add(db);
    grid.add(table);
    return grid;
  }

  private JPanel buildButtonBar() {
    var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    btnInspect.addActionListener(e -> onInspect());
    btnCompare.addActionListener(e -> onCompare());
    panel.add(btnInspect);
    panel.add(btnCompare);
    panel.add(lblStatus);
    return panel;
  }

  // ── Data loading ───────────────────────────────────────────────────────────

  private void loadConnectionNames() {
    try {
      var names = RecordRelayService.getInstance().configStore().load().getConnections().keySet();
      names.forEach(cmbSrcConn::addItem);
      names.forEach(cmbTgtConn::addItem);
    } catch (Exception ex) {
      Messages.showErrorDialog(project, ex.getMessage(), "RecordRelay — Discovery");
    }
  }

  private void onConnSelected(boolean isSrc) {
    String conn = (String) (isSrc ? cmbSrcConn : cmbTgtConn).getSelectedItem();
    if (conn == null || conn.isBlank()) {
      return;
    }
    var dbCombo = isSrc ? cmbSrcDb : cmbTgtDb;
    var tblCombo = isSrc ? cmbSrcTable : cmbTgtTable;
    dbCombo.removeAllItems();
    tblCombo.removeAllItems();
    loadDatabases(conn, isSrc);
  }

  private void onDbSelected(boolean isSrc) {
    var dbRef = (DatabaseRef) (isSrc ? cmbSrcDb : cmbTgtDb).getSelectedItem();
    String conn = (String) (isSrc ? cmbSrcConn : cmbTgtConn).getSelectedItem();
    if (dbRef == null || conn == null) {
      return;
    }
    (isSrc ? cmbSrcTable : cmbTgtTable).removeAllItems();
    loadTables(conn, dbRef, isSrc);
  }

  private void loadDatabases(String connName, boolean isSrc) {
    var resultRef = new AtomicReference<List<DatabaseRef>>();
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "Loading databases…", false) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                try {
                  var profile = RecordRelayService.getInstance().resolver().resolve(connName);
                  resultRef.set(new DiscoveryEngine().discoverDatabases(profile));
                } catch (Exception ex) {
                  resultRef.set(List.of());
                }
              }

              @Override
              public void onSuccess() {
                var combo = isSrc ? cmbSrcDb : cmbTgtDb;
                combo.removeAllItems();
                var dbs = resultRef.get();
                if (dbs != null) {
                  dbs.forEach(combo::addItem);
                }
              }
            });
  }

  private void loadTables(String connName, DatabaseRef dbRef, boolean isSrc) {
    var resultRef = new AtomicReference<List<TableRef>>();
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "Loading tables…", false) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                try {
                  var profile = RecordRelayService.getInstance().resolver().resolve(connName);
                  resultRef.set(new DiscoveryEngine().discoverTables(profile, dbRef));
                } catch (Exception ex) {
                  resultRef.set(List.of());
                }
              }

              @Override
              public void onSuccess() {
                var combo = isSrc ? cmbSrcTable : cmbTgtTable;
                combo.removeAllItems();
                var tables = resultRef.get();
                if (tables != null) {
                  tables.forEach(combo::addItem);
                }
              }
            });
  }

  // ── Actions ────────────────────────────────────────────────────────────────

  private void onInspect() {
    var srcTable = (TableRef) cmbSrcTable.getSelectedItem();
    String srcConn = (String) cmbSrcConn.getSelectedItem();
    if (srcTable == null || srcConn == null) {
      Messages.showInfoMessage(project, "Select a source table first.", "RecordRelay");
      return;
    }
    btnInspect.setEnabled(false);
    colModel.setRowCount(0);
    mapModel.setRowCount(0);
    lblStatus.setText("Inspecting…");

    var resultRef = new AtomicReference<List<ColumnMeta>>();
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "Inspecting columns…", false) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                try {
                  var profile = RecordRelayService.getInstance().resolver().resolve(srcConn);
                  resultRef.set(new DiscoveryEngine().inspectColumns(profile, srcTable));
                } catch (Exception ex) {
                  resultRef.set(null);
                }
              }

              @Override
              public void onSuccess() {
                btnInspect.setEnabled(true);
                var cols = resultRef.get();
                if (cols == null) {
                  lblStatus.setText("Inspection failed.");
                  return;
                }
                for (var col : cols) {
                  colModel.addRow(
                      new Object[] {
                        col.name(),
                        col.nativeType(),
                        col.nullable() ? "Yes" : "No",
                        col.primaryKey() ? "Yes" : ""
                      });
                }
                lblStatus.setText(srcTable.tableName() + " — " + cols.size() + " column(s)");
              }
            });
  }

  private void onCompare() {
    var srcTable = (TableRef) cmbSrcTable.getSelectedItem();
    var tgtTable = (TableRef) cmbTgtTable.getSelectedItem();
    String srcConn = (String) cmbSrcConn.getSelectedItem();
    String tgtConn = (String) cmbTgtConn.getSelectedItem();
    if (srcTable == null || tgtTable == null || srcConn == null || tgtConn == null) {
      Messages.showInfoMessage(project, "Select source and target tables first.", "RecordRelay");
      return;
    }
    btnCompare.setEnabled(false);
    mapModel.setRowCount(0);
    lblStatus.setText("Comparing…");

    var reportRef = new AtomicReference<SchemaMatchReport>();
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "Analyzing schema compatibility…", false) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                try {
                  var srcProfile = RecordRelayService.getInstance().resolver().resolve(srcConn);
                  var tgtProfile = RecordRelayService.getInstance().resolver().resolve(tgtConn);
                  reportRef.set(
                      new DiscoveryEngine()
                          .analyzeCompatibility(srcProfile, srcTable, tgtProfile, tgtTable));
                } catch (Exception ex) {
                  reportRef.set(null);
                }
              }

              @Override
              public void onSuccess() {
                btnCompare.setEnabled(true);
                var report = reportRef.get();
                if (report == null) {
                  lblStatus.setText("Comparison failed.");
                  return;
                }
                for (var cc : report.columnCompatibilities()) {
                  mapModel.addRow(
                      new Object[] {
                        cc.sourceColumn() != null ? cc.sourceColumn() : "(none)",
                        cc.targetColumn(),
                        cc.typeCompatible() ? "Yes" : "No",
                        cc.warning() != null ? cc.warning() : ""
                      });
                }
                String summary =
                    report.isFullyCompatible() ? "Fully compatible" : "Compatibility issues found";
                lblStatus.setText(
                    summary + " — " + report.columnCompatibilities().size() + " column(s)");
              }
            });
  }
}
