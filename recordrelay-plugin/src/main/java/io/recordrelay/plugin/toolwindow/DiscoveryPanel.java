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
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.SchemaMatchReport;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.plugin.service.RecordRelayService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import org.jetbrains.annotations.NotNull;

/** Tool-window panel for schema discovery and column mapping analysis. */
public final class DiscoveryPanel extends JPanel {

  private static final String[] MAP_COLS = {
    "Source Column", "Target Column", "Compatible", "Warning"
  };

  private final Project project;
  private final ComboBox<String> cmbSrcConn = new ComboBox<>();
  private final ComboBox<DatabaseRef> cmbSrcDb = new ComboBox<>();
  private final ComboBox<TableRef> cmbSrcTable = new ComboBox<>();
  private final ComboBox<String> cmbTgtConn = new ComboBox<>();
  private final ComboBox<DatabaseRef> cmbTgtDb = new ComboBox<>();
  private final ComboBox<TableRef> cmbTgtTable = new ComboBox<>();
  private final JButton btnDiscover = new JButton("Discover & Map");
  private final JLabel lblStatus = new JLabel(" ");
  private final DefaultTableModel mappingModel =
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
    add(buildSelectionPanel(), BorderLayout.NORTH);
    add(new JBScrollPane(new JBTable(mappingModel)), BorderLayout.CENTER);
    add(buildButtonBar(), BorderLayout.SOUTH);
    loadConnectionNames();
  }

  private void applyRenderers() {
    cmbSrcDb.setRenderer(SimpleListCellRenderer.create("— select database —", DatabaseRef::name));
    cmbTgtDb.setRenderer(SimpleListCellRenderer.create("— select database —", DatabaseRef::name));
    cmbSrcTable.setRenderer(SimpleListCellRenderer.create("— select table —", TableRef::tableName));
    cmbTgtTable.setRenderer(SimpleListCellRenderer.create("— select table —", TableRef::tableName));
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
    panel.add(buildConnGroup("Target", cmbTgtConn, cmbTgtDb, cmbTgtTable));
    return panel;
  }

  private JPanel buildConnGroup(
      String label, ComboBox<String> conn, ComboBox<DatabaseRef> db, ComboBox<TableRef> table) {
    var grid = new JPanel(new GridLayout(4, 1, 0, 4));
    grid.add(new JLabel(label));
    grid.add(conn);
    grid.add(db);
    grid.add(table);
    return grid;
  }

  private JPanel buildButtonBar() {
    var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    btnDiscover.addActionListener(e -> onDiscover());
    panel.add(btnDiscover);
    panel.add(lblStatus);
    return panel;
  }

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
    String conn =
        isSrc ? (String) cmbSrcConn.getSelectedItem() : (String) cmbTgtConn.getSelectedItem();
    if (conn == null || conn.isBlank()) {
      return;
    }
    var dbCombo = isSrc ? cmbSrcDb : cmbTgtDb;
    dbCombo.removeAllItems();
    var tableCombo = isSrc ? cmbSrcTable : cmbTgtTable;
    tableCombo.removeAllItems();
    loadDatabases(conn, isSrc);
  }

  private void onDbSelected(boolean isSrc) {
    var dbRef =
        isSrc ? (DatabaseRef) cmbSrcDb.getSelectedItem() : (DatabaseRef) cmbTgtDb.getSelectedItem();
    if (dbRef == null) {
      return;
    }
    String conn =
        isSrc ? (String) cmbSrcConn.getSelectedItem() : (String) cmbTgtConn.getSelectedItem();
    var tableCombo = isSrc ? cmbSrcTable : cmbTgtTable;
    tableCombo.removeAllItems();
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

  private void onDiscover() {
    var srcTable = (TableRef) cmbSrcTable.getSelectedItem();
    var tgtTable = (TableRef) cmbTgtTable.getSelectedItem();
    if (srcTable == null || tgtTable == null) {
      Messages.showInfoMessage(project, "Select source and target tables first.", "RecordRelay");
      return;
    }
    String srcConn = (String) cmbSrcConn.getSelectedItem();
    String tgtConn = (String) cmbTgtConn.getSelectedItem();
    runDiscover(srcConn, tgtConn, srcTable, tgtTable);
  }

  private void runDiscover(String srcConn, String tgtConn, TableRef srcRef, TableRef tgtRef) {
    var reportRef = new AtomicReference<SchemaMatchReport>();
    btnDiscover.setEnabled(false);
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
                          .analyzeCompatibility(srcProfile, srcRef, tgtProfile, tgtRef));
                } catch (Exception ex) {
                  reportRef.set(null);
                }
              }

              @Override
              public void onSuccess() {
                btnDiscover.setEnabled(true);
                var report = reportRef.get();
                if (report != null) {
                  buildMapping(report);
                } else {
                  lblStatus.setText("Discovery failed.");
                }
              }
            });
  }

  private void buildMapping(SchemaMatchReport report) {
    mappingModel.setRowCount(0);
    for (var cc : report.columnCompatibilities()) {
      mappingModel.addRow(
          new Object[] {
            cc.sourceColumn() != null ? cc.sourceColumn() : "(none)",
            cc.targetColumn(),
            cc.typeCompatible() ? "Yes" : "No",
            cc.warning() != null ? cc.warning() : ""
          });
    }
    String summary = report.isFullyCompatible() ? "Fully compatible" : "Compatibility issues found";
    lblStatus.setText(summary + " — " + report.columnCompatibilities().size() + " column(s)");
  }
}
