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
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import io.recordrelay.core.spi.ConnectorRegistry;
import io.recordrelay.engine.clone.CloneHistoryStore;
import io.recordrelay.plugin.service.RecordRelayService;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import org.jetbrains.annotations.NotNull;

/** Tool-window panel showing DB connection health. */
public final class MonitorPanel extends JPanel {

  private static final String[] HEALTH_COLS = {"Connection", "Status", "Detail"};
  private static final String[] HISTORY_COLS = {
    "Time", "Table", "ID", "Source", "Target", "Records", "Duration", "Status"
  };

  private final Project project;
  private final JLabel lblTransferred = kpiLabel("0");
  private final JLabel lblFailed = kpiLabel("0");
  private final JLabel lblOperations = kpiLabel("0");
  private final JLabel lblDuration = kpiLabel("—");
  private final JLabel lblStatus = new JLabel("● Ready");
  private final JButton btnRefresh = new JButton("Check Health");
  private final JButton btnClear = new JButton("Clear History");
  private final DefaultTableModel healthModel =
      new DefaultTableModel(HEALTH_COLS, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
          return false;
        }
      };
  private final DefaultTableModel historyModel =
      new DefaultTableModel(HISTORY_COLS, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
          return false;
        }
      };

  /** Creates the panel for the given project. */
  public MonitorPanel(Project project) {
    super(new BorderLayout(0, 0));
    this.project = project;
    btnRefresh.addActionListener(e -> onRefreshHealth());
    btnClear.addActionListener(e -> onClear());
    var body = new JPanel(new BorderLayout(0, 8));
    body.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 0, 0, 0));
    body.add(buildKpiPanel(), BorderLayout.NORTH);
    body.add(buildTablesPanel(), BorderLayout.CENTER);
    add(
        new PanelHeader("Monitor", "Clone history and connection health overview"),
        BorderLayout.NORTH);
    add(body, BorderLayout.CENTER);
    add(buildButtonBar(), BorderLayout.SOUTH);
    refreshHistory();
  }

  private static JLabel kpiLabel(String text) {
    var lbl = new JLabel(text);
    lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 18f));
    return lbl;
  }

  private JPanel buildKpiPanel() {
    var panel = new JPanel(new GridLayout(1, 4, 8, 0));
    panel.add(kpiCard("Transferred", lblTransferred));
    panel.add(kpiCard("Failed", lblFailed));
    panel.add(kpiCard("Operations", lblOperations));
    panel.add(kpiCard("Last Duration", lblDuration));
    return panel;
  }

  private JPanel buildTablesPanel() {
    var panel = new JPanel(new GridLayout(2, 1, 0, 6));
    var historyTable = new JBTable(historyModel);
    historyTable.getColumnModel().getColumn(7).setCellRenderer(new StatusCellRenderer());
    var historyScroll = new JBScrollPane(historyTable);
    historyScroll.setBorder(javax.swing.BorderFactory.createTitledBorder("Clone History"));
    panel.add(historyScroll);

    var healthTable = new JBTable(healthModel);
    healthTable.getColumnModel().getColumn(1).setCellRenderer(new StatusCellRenderer());
    var healthScroll = new JBScrollPane(healthTable);
    healthScroll.setBorder(javax.swing.BorderFactory.createTitledBorder("Connection Health"));
    panel.add(healthScroll);
    return panel;
  }

  private static JPanel kpiCard(String title, JLabel value) {
    var card = new JPanel(new BorderLayout(0, 2));
    var titleLbl = new JLabel(title.toUpperCase());
    titleLbl.setFont(titleLbl.getFont().deriveFont(Font.PLAIN, 10f));
    titleLbl.setForeground(JBColor.GRAY);
    card.add(titleLbl, BorderLayout.NORTH);
    card.add(value, BorderLayout.CENTER);
    return card;
  }

  private JPanel buildButtonBar() {
    var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    panel.add(btnRefresh);
    panel.add(btnClear);
    panel.add(lblStatus);
    return panel;
  }

  /** Refreshes the clone history table from the in-memory store. */
  public void refreshHistory() {
    historyModel.setRowCount(0);
    var store = CloneHistoryStore.getInstance();
    for (var entry : store.recent(20)) {
      historyModel.addRow(
          new Object[] {
            entry.formattedTime(),
            entry.rootTable(),
            entry.rootId(),
            entry.sourceProfile(),
            entry.targetProfile(),
            String.format("%,d", entry.totalRecords()),
            entry.formattedDuration(),
            entry.success() ? "✓ OK" : "✗ Error"
          });
    }
    lblTransferred.setText(
        String.format(
            "%,d",
            store.recent(200).stream()
                .filter(e -> e.success())
                .mapToLong(e -> e.totalRecords())
                .sum()));
    lblFailed.setText(String.format("%,d", store.sessionFailedTotal()));
    lblOperations.setText(String.format("%,d", store.operationCount()));
    var recent = store.recent(1);
    lblDuration.setText(recent.isEmpty() ? "—" : recent.get(0).formattedDuration());
  }

  private void onClear() {
    CloneHistoryStore.getInstance().clear();
    healthModel.setRowCount(0);
    refreshHistory();
    lblStatus.setText("● Ready");
  }

  private void onRefreshHealth() {
    btnRefresh.setEnabled(false);
    lblStatus.setText("Checking…");
    healthModel.setRowCount(0);
    var results = new ArrayList<String[]>();
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "Checking connection health…", false) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                checkAllConnections(results);
              }

              @Override
              public void onSuccess() {
                results.forEach(row -> healthModel.addRow(row));
                lblStatus.setText("● Ready");
                btnRefresh.setEnabled(true);
              }
            });
  }

  private void checkAllConnections(List<String[]> results) {
    try {
      var store = RecordRelayService.getInstance().configStore();
      var resolver = RecordRelayService.getInstance().resolver();
      for (var name : store.load().getConnections().keySet()) {
        results.add(checkOne(resolver, name));
      }
    } catch (Exception ex) {
      results.add(new String[] {"(error)", "✗ Error", ex.getMessage()});
    }
  }

  private static String[] checkOne(
      io.recordrelay.cli.engine.ConnProfileResolver resolver, String name) {
    try {
      var profile = resolver.resolve(name);
      ConnectorRegistry.findConnector(profile).testConnection(profile);
      return new String[] {name, "✓ OK", "Reachable"};
    } catch (Exception ex) {
      String detail = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
      return new String[] {name, "✗ Error", detail};
    }
  }

  private static final class StatusCellRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
      String text = value != null ? value.toString() : "";
      if (text.startsWith("✓")) {
        setForeground(JBColor.GREEN.darker());
        setFont(getFont().deriveFont(Font.BOLD));
      } else if (text.startsWith("✗")) {
        setForeground(JBColor.RED);
        setFont(getFont().deriveFont(Font.BOLD));
      } else {
        setForeground(table.getForeground());
        setFont(getFont().deriveFont(Font.PLAIN));
      }
      return this;
    }
  }
}
