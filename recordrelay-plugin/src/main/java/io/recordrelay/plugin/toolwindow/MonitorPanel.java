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

  private final Project project;
  private final JLabel lblTransferred = kpiLabel("0");
  private final JLabel lblFailed = kpiLabel("0");
  private final JLabel lblThroughput = kpiLabel("0 rec/s");
  private final JLabel lblDuration = kpiLabel("—");
  private final JLabel lblStatus = new JLabel("● Ready");
  private final JButton btnRefresh = new JButton("Check Health");
  private final JButton btnClear = new JButton("Clear");
  private final DefaultTableModel healthModel =
      new DefaultTableModel(HEALTH_COLS, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
          return false;
        }
      };

  /** Creates the panel for the given project. */
  public MonitorPanel(Project project) {
    super(new BorderLayout(0, 8));
    this.project = project;
    btnRefresh.addActionListener(e -> onRefreshHealth());
    btnClear.addActionListener(e -> onClear());
    add(buildKpiPanel(), BorderLayout.NORTH);
    add(buildHealthTable(), BorderLayout.CENTER);
    add(buildButtonBar(), BorderLayout.SOUTH);
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
    panel.add(kpiCard("Throughput", lblThroughput));
    panel.add(kpiCard("Duration", lblDuration));
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

  private JBScrollPane buildHealthTable() {
    var table = new JBTable(healthModel);
    table.getColumnModel().getColumn(1).setCellRenderer(new StatusCellRenderer());
    return new JBScrollPane(table);
  }

  private JPanel buildButtonBar() {
    var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    panel.add(btnRefresh);
    panel.add(btnClear);
    panel.add(lblStatus);
    return panel;
  }

  /** Updates the transferred-record KPI label (call from any thread via Swing EDT). */
  public void setTransferred(long count) {
    lblTransferred.setText(String.format("%,d", count));
  }

  /** Updates the failed-record KPI label. */
  public void setFailed(long count) {
    lblFailed.setText(String.format("%,d", count));
  }

  /** Updates the throughput KPI label. */
  public void setThroughput(long recsPerSec) {
    lblThroughput.setText(String.format("%,d rec/s", recsPerSec));
  }

  /** Updates the duration KPI label. */
  public void setDuration(String duration) {
    lblDuration.setText(duration);
  }

  private void onClear() {
    healthModel.setRowCount(0);
    lblTransferred.setText("0");
    lblFailed.setText("0");
    lblThroughput.setText("0 rec/s");
    lblDuration.setText("—");
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
