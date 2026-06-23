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
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.table.JBTable;
import io.recordrelay.cli.engine.ConnProfileResolver;
import io.recordrelay.cli.engine.QueryRunner;
import io.recordrelay.core.domain.QueryResult;
import io.recordrelay.core.i18n.Messages;
import io.recordrelay.plugin.service.RecordRelayService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Vector;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import org.jetbrains.annotations.NotNull;

/** Tool-window panel for running read-only SELECT queries against a saved connection. */
public final class QueryPanel extends JPanel {

  private final Project project;

  private final ComboBox<String> cmbConn = new ComboBox<>();
  private final JBTextArea taSql = new JBTextArea(6, 60);
  private final JButton btnRun = new JButton(Messages.get("query.btn.run"));
  private final JLabel lblStatus = new JLabel(Messages.get("status.ready"));

  private final DefaultTableModel tableModel =
      new DefaultTableModel(0, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
          return false;
        }
      };
  private final JBTable tblResults = new JBTable(tableModel);

  /** Creates the Query Analyzer panel bound to the given project. */
  public QueryPanel(Project project) {
    this.project = project;
    setLayout(new BorderLayout(0, 8));
    setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    add(buildTopPanel(), BorderLayout.NORTH);
    add(new JBScrollPane(tblResults), BorderLayout.CENTER);
    add(lblStatus, BorderLayout.SOUTH);

    loadConnections();
    btnRun.addActionListener(e -> onRun());
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private JPanel buildTopPanel() {
    var panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    var connRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    connRow.add(new JLabel("Connection:"));
    connRow.add(cmbConn);
    connRow.add(btnRun);
    panel.add(connRow);

    panel.add(Box.createVerticalStrut(6));

    taSql.setLineWrap(false);
    taSql.setText("");
    var sqlScroll = new JBScrollPane(taSql);
    sqlScroll.setBorder(BorderFactory.createTitledBorder("SQL"));
    panel.add(sqlScroll);

    return panel;
  }

  private void loadConnections() {
    cmbConn.removeAllItems();
    try {
      var service = project.getService(RecordRelayService.class);
      service.configStore().load().getConnections().keySet().forEach(cmbConn::addItem);
    } catch (Exception e) {
      lblStatus.setText("Config error: " + e.getMessage());
    }
  }

  private void onRun() {
    String conn = (String) cmbConn.getSelectedItem();
    String sql = taSql.getText().trim();

    if (conn == null || conn.isBlank()) {
      lblStatus.setText("Select a connection first.");
      return;
    }
    if (sql.isBlank()) {
      lblStatus.setText("Enter a SELECT query.");
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
                  var service = project.getService(RecordRelayService.class);
                  var resolver = new ConnProfileResolver(service.configStore());
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
    List<String> columns = result.columns();
    var colVec = new Vector<>(columns);
    tableModel.setColumnIdentifiers(colVec);
    tableModel.setRowCount(0);

    for (var row : result.rows()) {
      tableModel.addRow(new Vector<>(row));
    }

    lblStatus.setText(Messages.get("query.rows", result.rowCount()));
  }
}
