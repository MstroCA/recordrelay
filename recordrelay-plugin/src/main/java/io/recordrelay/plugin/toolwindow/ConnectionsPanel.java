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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import io.recordrelay.cli.config.ConfigStore;
import io.recordrelay.cli.config.ConnectionEntry;
import io.recordrelay.plugin.service.RecordRelayService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import org.jetbrains.annotations.Nullable;

/** Tool-window panel for managing database connection profiles. */
public final class ConnectionsPanel extends JPanel {

  private static final String[] COL_NAMES = {"Name", "Type", "Host", "Port", "Database", "User"};
  private static final List<String> DB_TYPES =
      List.of(
          "POSTGRESQL",
          "MYSQL",
          "MARIADB",
          "ORACLE",
          "SQLSERVER",
          "MONGODB",
          "CASSANDRA",
          "REDIS",
          "ELASTICSEARCH");

  private final Project project;
  private final DefaultTableModel model;
  private final JBTable table;

  /** Creates the panel for the given project. */
  public ConnectionsPanel(Project project) {
    super(new BorderLayout());
    this.project = project;
    this.model =
        new DefaultTableModel(COL_NAMES, 0) {
          @Override
          public boolean isCellEditable(int r, int c) {
            return false;
          }
        };
    this.table = new JBTable(model);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    var north = new JPanel(new BorderLayout());
    north.add(
        new PanelHeader("Connections", "Manage database connection profiles"), BorderLayout.NORTH);
    north.add(buildToolbar(), BorderLayout.SOUTH);
    add(north, BorderLayout.NORTH);
    add(new JBScrollPane(table), BorderLayout.CENTER);
    loadData();
  }

  private JPanel buildToolbar() {
    var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
    var btnAdd = new JButton("Add");
    var btnEdit = new JButton("Edit");
    var btnRemove = new JButton("Remove");
    btnAdd.addActionListener(e -> onAdd());
    btnEdit.addActionListener(e -> onEdit());
    btnRemove.addActionListener(e -> onRemove());
    panel.add(btnAdd);
    panel.add(btnEdit);
    panel.add(btnRemove);
    return panel;
  }

  private void loadData() {
    model.setRowCount(0);
    try {
      var config = RecordRelayService.getInstance().configStore().load();
      config
          .getConnections()
          .forEach(
              (name, e) ->
                  model.addRow(
                      new Object[] {
                        name, e.getType(), e.getHost(), e.getPort(), e.getDatabase(), e.getUser()
                      }));
    } catch (Exception ex) {
      Messages.showErrorDialog(project, ex.getMessage(), "RecordRelay — Connections");
    }
  }

  private void onAdd() {
    var dlg = new ConnDialog(project, "Add Connection", "", null);
    if (dlg.showAndGet()) {
      saveEntry("", dlg);
    }
  }

  private void onEdit() {
    int row = table.getSelectedRow();
    if (row < 0) {
      return;
    }
    String name = (String) model.getValueAt(row, 0);
    try {
      var entry = RecordRelayService.getInstance().configStore().load().getConnections().get(name);
      var dlg = new ConnDialog(project, "Edit Connection", name, entry);
      if (dlg.showAndGet()) {
        RecordRelayService.getInstance().configStore().removeConnection(name);
        saveEntry(name, dlg);
      }
    } catch (Exception ex) {
      Messages.showErrorDialog(project, ex.getMessage(), "RecordRelay — Connections");
    }
    loadData();
  }

  private void saveEntry(String oldName, ConnDialog dlg) {
    try {
      var store = RecordRelayService.getInstance().configStore();
      store.addConnection(dlg.connName(), dlg.buildEntry(store, oldName));
      loadData();
    } catch (Exception ex) {
      Messages.showErrorDialog(project, ex.getMessage(), "RecordRelay — Connections");
    }
  }

  private void onRemove() {
    int row = table.getSelectedRow();
    if (row < 0) {
      return;
    }
    String name = (String) model.getValueAt(row, 0);
    int choice =
        Messages.showYesNoDialog(
            project, "Remove connection '" + name + "'?", "RecordRelay", Messages.getWarningIcon());
    if (choice == Messages.YES) {
      try {
        RecordRelayService.getInstance().configStore().removeConnection(name);
        loadData();
      } catch (Exception ex) {
        Messages.showErrorDialog(project, ex.getMessage(), "RecordRelay — Connections");
      }
    }
  }

  private static final class ConnDialog extends DialogWrapper {

    private final JBTextField tfName = new JBTextField(20);
    private final ComboBox<String> cmbType = new ComboBox<>(DB_TYPES.toArray(new String[0]));
    private final JBTextField tfHost = new JBTextField(20);
    private final JBTextField tfPort = new JBTextField("5432", 6);
    private final JBTextField tfDatabase = new JBTextField(20);
    private final JBTextField tfUser = new JBTextField(20);
    private final JBTextField tfSchema = new JBTextField(20);
    private final JBPasswordField tfPassword = new JBPasswordField();
    private final String oldName;

    ConnDialog(Project project, String title, String oldName, @Nullable ConnectionEntry init) {
      super(project, true);
      this.oldName = oldName;
      setTitle(title);
      if (init != null) {
        fillFrom(init, oldName);
      }
      init();
    }

    private void fillFrom(ConnectionEntry entry, String name) {
      tfName.setText(name);
      if (entry.getType() != null) {
        cmbType.setSelectedItem(entry.getType());
      }
      if (entry.getHost() != null) {
        tfHost.setText(entry.getHost());
      }
      tfPort.setText(String.valueOf(entry.getPort()));
      if (entry.getDatabase() != null) {
        tfDatabase.setText(entry.getDatabase());
      }
      if (entry.getUser() != null) {
        tfUser.setText(entry.getUser());
      }
      if (entry.getSchema() != null) {
        tfSchema.setText(entry.getSchema());
      }
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
      var grid = new JPanel(new GridLayout(8, 2, 6, 6));
      var pwdHint = new JLabel("(leave blank to keep existing)");
      pwdHint.setForeground(JBColor.GRAY);
      grid.add(new JLabel("Name:"));
      grid.add(tfName);
      grid.add(new JLabel("Type:"));
      grid.add(cmbType);
      grid.add(new JLabel("Host:"));
      grid.add(tfHost);
      grid.add(new JLabel("Port:"));
      grid.add(tfPort);
      grid.add(new JLabel("Database:"));
      grid.add(tfDatabase);
      grid.add(new JLabel("User:"));
      grid.add(tfUser);
      grid.add(new JLabel("Schema:"));
      grid.add(tfSchema);
      grid.add(new JLabel("Password:"));
      grid.add(buildPasswordPanel(pwdHint));
      return grid;
    }

    private JPanel buildPasswordPanel(JLabel hint) {
      var panel = new JPanel(new BorderLayout(4, 0));
      panel.add(tfPassword, BorderLayout.CENTER);
      panel.add(hint, BorderLayout.SOUTH);
      return panel;
    }

    String connName() {
      return tfName.getText().trim();
    }

    ConnectionEntry buildEntry(ConfigStore store, String existingName) throws Exception {
      var entry = new ConnectionEntry();
      entry.setType(Objects.requireNonNull((String) cmbType.getSelectedItem()));
      entry.setHost(tfHost.getText().trim());
      entry.setPort(Integer.parseInt(tfPort.getText().trim()));
      entry.setDatabase(tfDatabase.getText().trim());
      entry.setUser(tfUser.getText().trim());
      entry.setSchema(tfSchema.getText().trim());
      char[] pwd = tfPassword.getPassword();
      if (pwd.length > 0) {
        entry.setEncryptedPassword(store.encryptor().encrypt(new String(pwd)));
        Arrays.fill(pwd, '\0');
      } else if (!existingName.isBlank()) {
        var existing = store.load().getConnections().get(existingName);
        if (existing != null) {
          entry.setEncryptedPassword(existing.getEncryptedPassword());
        }
      }
      return entry;
    }
  }
}
