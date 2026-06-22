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
import com.intellij.ui.components.JBTextField;
import io.recordrelay.core.clone.domain.BusinessEntity;
import io.recordrelay.core.clone.domain.ContextClonePlan;
import io.recordrelay.core.clone.domain.FieldOverride;
import io.recordrelay.core.clone.domain.FieldOverrideConfig;
import io.recordrelay.core.clone.domain.MaskerType;
import io.recordrelay.core.clone.domain.MaskingConfig;
import io.recordrelay.core.clone.domain.MaskingRule;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.spi.ConnectorRegistry;
import io.recordrelay.engine.clone.DefaultContextCloneEngine;
import io.recordrelay.plugin.service.RecordRelayService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;

/** Tool-window panel for cloning a business context from source to target environment. */
public final class CloneContextPanel extends JPanel {

  private final Project project;

  // Step 1: Connections
  private final ComboBox<String> cmbSource = new ComboBox<>();
  private final ComboBox<String> cmbTarget = new ComboBox<>();
  private final JCheckBox chkExportMode = new JCheckBox("Export to .rrpkg");

  // Step 2: Tables & Record
  private final JButton btnLoadTables = new JButton("Load Tables");
  private final JLabel lblTableCount = new JLabel("");
  private final ComboBox<String> cmbRootTable = new ComboBox<>();
  private final JBTextField tfPkColumn = new JBTextField("id", 10);
  private final JBTextField tfEntityId = new JBTextField();

  // Step 3: Options
  private final JSpinner spinDepth = new JSpinner(new SpinnerNumberModel(3, 1, 10, 1));
  private final JCheckBox chkMaskPii = new JCheckBox("Mask PII");

  // Step 4: Field Overrides
  private final JBTextArea taOverrides = new JBTextArea(4, 40);

  // Export output dir (visible only in export mode)
  private final JBTextField tfOutputDir = new JBTextField();
  private final JButton btnBrowseDir = new JButton("Browse…");
  private final JPanel pnlOutputDir = new JPanel(new BorderLayout(4, 0));

  // Actions & log
  private final JButton btnClone = new JButton("Clone Context");
  private final JButton btnExport = new JButton("Export Context");
  private final JBTextArea taLog = new JBTextArea(8, 40);
  private final JLabel lblStatus = new JLabel("Ready");

  /** Creates the panel for the given project. */
  public CloneContextPanel(Project project) {
    super(new BorderLayout(0, 6));
    this.project = project;
    taLog.setEditable(false);
    taLog.setLineWrap(true);
    taOverrides.setLineWrap(false);
    taOverrides.setToolTipText("One override per line: column=value  or  table:column=value");

    setupExportModeToggle();
    btnLoadTables.addActionListener(e -> onLoadTables());

    add(buildForm(), BorderLayout.NORTH);

    var centerPanel = new JPanel(new BorderLayout(0, 4));
    var overridesScroll = new JBScrollPane(taOverrides);
    overridesScroll.setBorder(
        BorderFactory.createTitledBorder("Field Overrides (column=value / table:column=value)"));
    centerPanel.add(overridesScroll, BorderLayout.NORTH);
    centerPanel.add(new JBScrollPane(taLog), BorderLayout.CENTER);
    add(centerPanel, BorderLayout.CENTER);
    add(buildButtonBar(), BorderLayout.SOUTH);

    loadConnections();
  }

  // ── Setup ──────────────────────────────────────────────────────────────────

  private void setupExportModeToggle() {
    btnExport.setEnabled(false);
    pnlOutputDir.add(new JLabel("Output dir:"), BorderLayout.WEST);
    pnlOutputDir.add(tfOutputDir, BorderLayout.CENTER);
    pnlOutputDir.add(btnBrowseDir, BorderLayout.EAST);
    pnlOutputDir.setVisible(false);

    btnBrowseDir.addActionListener(
        e -> {
          var chooser = new JFileChooser();
          chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
          if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            tfOutputDir.setText(chooser.getSelectedFile().getAbsolutePath());
          }
        });

    chkExportMode.addActionListener(
        e -> {
          boolean exportOn = chkExportMode.isSelected();
          cmbTarget.setEnabled(!exportOn);
          pnlOutputDir.setVisible(exportOn);
          btnClone.setEnabled(!exportOn);
          btnExport.setEnabled(exportOn);
          revalidate();
          repaint();
        });
  }

  private JPanel buildForm() {
    var panel = new JPanel(new GridBagLayout());
    var gbc = new GridBagConstraints();
    gbc.insets = new Insets(3, 4, 3, 4);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    int row = 0;

    // Export mode toggle (full width)
    gbc.gridx = 0;
    gbc.gridy = row++;
    gbc.gridwidth = 2;
    gbc.weightx = 1.0;
    panel.add(chkExportMode, gbc);
    gbc.gridwidth = 1;

    addRow(panel, gbc, row++, "Source:", cmbSource);
    addRow(panel, gbc, row++, "Target:", cmbTarget);

    // Output dir row (export mode only)
    gbc.gridx = 0;
    gbc.gridy = row++;
    gbc.gridwidth = 2;
    gbc.weightx = 1.0;
    panel.add(pnlOutputDir, gbc);
    gbc.gridwidth = 1;

    // Load tables row
    var loadRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    loadRow.add(btnLoadTables);
    loadRow.add(lblTableCount);
    gbc.gridx = 0;
    gbc.gridy = row++;
    gbc.gridwidth = 2;
    gbc.weightx = 1.0;
    panel.add(loadRow, gbc);
    gbc.gridwidth = 1;

    addRow(panel, gbc, row++, "Root table:", cmbRootTable);
    addRow(panel, gbc, row++, "PK column:", tfPkColumn);
    addRow(panel, gbc, row++, "Entity ID:", tfEntityId);
    addRow(panel, gbc, row++, "Depth:", spinDepth);

    gbc.gridx = 1;
    gbc.gridy = row;
    panel.add(chkMaskPii, gbc);

    return panel;
  }

  private static void addRow(
      JPanel panel, GridBagConstraints gbc, int row, String label, java.awt.Component field) {
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.weightx = 0;
    panel.add(new JLabel(label), gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    panel.add(field, gbc);
  }

  private JPanel buildButtonBar() {
    var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    btnClone.addActionListener(e -> onClone());
    btnExport.addActionListener(e -> onExport());
    panel.add(btnClone);
    panel.add(btnExport);
    panel.add(lblStatus);
    return panel;
  }

  // ── Data loading ───────────────────────────────────────────────────────────

  private void loadConnections() {
    try {
      var names = RecordRelayService.getInstance().configStore().load().getConnections().keySet();
      names.forEach(cmbSource::addItem);
      names.forEach(cmbTarget::addItem);
    } catch (Exception ex) {
      appendLog("Could not load connections: " + ex.getMessage());
    }
  }

  private void onLoadTables() {
    String connName = (String) cmbSource.getSelectedItem();
    if (connName == null || connName.isBlank()) {
      lblTableCount.setText("Select a source first");
      return;
    }
    btnLoadTables.setEnabled(false);
    lblTableCount.setText("Loading…");
    cmbRootTable.removeAllItems();

    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "Loading tables…", false) {
              private List<String> tableNames;
              private String error;

              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                try {
                  var resolver = RecordRelayService.getInstance().resolver();
                  var profile = resolver.resolve(connName);
                  var dbRef = new DatabaseRef(profile.database(), profile.type());
                  var tables =
                      ConnectorRegistry.findConnector(profile)
                          .schemaInspector()
                          .listTables(profile, dbRef);
                  tableNames = tables.stream().map(t -> t.tableName()).sorted().toList();
                } catch (Exception ex) {
                  error = ex.getMessage();
                }
              }

              @Override
              public void onSuccess() {
                if (error != null) {
                  lblTableCount.setText("Failed");
                  appendLog("Could not load tables: " + error);
                } else {
                  tableNames.forEach(cmbRootTable::addItem);
                  lblTableCount.setText(tableNames.size() + " tables");
                  if (!tableNames.isEmpty()) {
                    cmbRootTable.setSelectedIndex(0);
                  }
                }
                btnLoadTables.setEnabled(true);
              }
            });
  }

  // ── Action handlers ────────────────────────────────────────────────────────

  private void onClone() {
    String err = validate(false);
    if (err != null) {
      JOptionPane.showMessageDialog(this, err, "Validation", JOptionPane.WARNING_MESSAGE);
      return;
    }
    btnClone.setEnabled(false);
    taLog.setText("");
    lblStatus.setText("Cloning…");

    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "RecordRelay — cloning context…", false) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                runClone();
              }

              @Override
              public void onFinished() {
                SwingUtilities.invokeLater(() -> btnClone.setEnabled(true));
              }
            });
  }

  private void onExport() {
    String err = validate(true);
    if (err != null) {
      JOptionPane.showMessageDialog(this, err, "Validation", JOptionPane.WARNING_MESSAGE);
      return;
    }
    btnExport.setEnabled(false);
    taLog.setText("");
    lblStatus.setText("Exporting…");

    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "RecordRelay — exporting context…", false) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                runExport();
              }

              @Override
              public void onFinished() {
                SwingUtilities.invokeLater(() -> btnExport.setEnabled(true));
              }
            });
  }

  // ── Clone / Export execution ───────────────────────────────────────────────

  private void runClone() {
    try {
      var resolver = RecordRelayService.getInstance().resolver();
      var srcProfile = resolver.resolve((String) cmbSource.getSelectedItem());
      var tgtProfile = resolver.resolve((String) cmbTarget.getSelectedItem());
      var entity = buildEntity();
      var entityId = tfEntityId.getText().trim();
      var masking = buildMasking();
      int depth = (int) spinDepth.getValue();

      appendLog(
          "Cloning "
              + entity.tableName()
              + " #"
              + entityId
              + " from '"
              + cmbSource.getSelectedItem()
              + "' → '"
              + cmbTarget.getSelectedItem()
              + "' (depth="
              + depth
              + ")");

      var plan =
          ContextClonePlan.liveCloneWithOverrides(
              entity, entityId, srcProfile, tgtProfile, depth, masking, buildFieldOverrides());

      var report = DefaultContextCloneEngine.createDefault().cloneContext(plan, buildListener());

      appendLog(
          "Clone complete — "
              + report.totalRecords()
              + " records in "
              + report.formattedDuration());
      SwingUtilities.invokeLater(
          () -> lblStatus.setText("Done — " + report.totalRecords() + " records"));
    } catch (Exception ex) {
      appendLog("ERROR: " + ex.getMessage());
      SwingUtilities.invokeLater(() -> lblStatus.setText("Failed: " + ex.getMessage()));
    }
  }

  private void runExport() {
    try {
      var resolver = RecordRelayService.getInstance().resolver();
      var srcProfile = resolver.resolve((String) cmbSource.getSelectedItem());
      var entity = buildEntity();
      var entityId = tfEntityId.getText().trim();
      var masking = buildMasking();
      var outDir =
          tfOutputDir.getText().isBlank() ? Path.of(".") : Path.of(tfOutputDir.getText().trim());

      appendLog(
          "Exporting "
              + entity.tableName()
              + " #"
              + entityId
              + " from '"
              + cmbSource.getSelectedItem()
              + "'");

      var plan = ContextClonePlan.bugCapture(entity, entityId, srcProfile, outDir, masking, null);
      var pkgPath = DefaultContextCloneEngine.createDefault().exportContext(plan);

      appendLog("Exported → " + pkgPath.toAbsolutePath());
      SwingUtilities.invokeLater(() -> lblStatus.setText("Exported → " + pkgPath.getFileName()));
    } catch (Exception ex) {
      appendLog("ERROR: " + ex.getMessage());
      SwingUtilities.invokeLater(() -> lblStatus.setText("Failed: " + ex.getMessage()));
    }
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private BusinessEntity buildEntity() {
    String rootTable = (String) cmbRootTable.getSelectedItem();
    if (rootTable == null || rootTable.isBlank()) rootTable = "records";
    String pk = tfPkColumn.getText().trim();
    if (pk.isBlank()) pk = "id";
    return BusinessEntity.of(rootTable, rootTable, pk, "");
  }

  private FieldOverrideConfig buildFieldOverrides() {
    var list = new ArrayList<FieldOverride>();
    for (var line : taOverrides.getText().lines().toList()) {
      var raw = line.trim();
      if (raw.isBlank() || raw.startsWith("#")) continue;
      int colonIdx = raw.indexOf(':');
      int eqIdx = raw.indexOf('=');
      if (eqIdx < 0) continue;
      if (colonIdx > 0 && colonIdx < eqIdx) {
        list.add(
            FieldOverride.forTable(
                raw.substring(0, colonIdx).trim(),
                raw.substring(colonIdx + 1, eqIdx).trim(),
                raw.substring(eqIdx + 1)));
      } else {
        list.add(FieldOverride.global(raw.substring(0, eqIdx).trim(), raw.substring(eqIdx + 1)));
      }
    }
    return new FieldOverrideConfig(list);
  }

  private MaskingConfig buildMasking() {
    if (!chkMaskPii.isSelected()) {
      return MaskingConfig.none();
    }
    return new MaskingConfig(
        List.of(
            new MaskingRule("email", MaskerType.EMAIL),
            new MaskingRule("phone", MaskerType.PHONE),
            new MaskingRule("phone_number", MaskerType.PHONE),
            new MaskingRule("address", MaskerType.ADDRESS),
            new MaskingRule("national_id", MaskerType.NATIONAL_ID),
            new MaskingRule("iban", MaskerType.IBAN)));
  }

  private CloneProgressListener buildListener() {
    return new CloneProgressListener() {
      @Override
      public void onRelationshipsDiscovered(int edgeCount) {
        appendLog("  Relationships discovered: " + edgeCount);
      }

      @Override
      public void onTableExtractionStarted(String tableName) {
        appendLog("  Extracting: " + tableName);
      }

      @Override
      public void onTableExtractionCompleted(String tableName, long recordCount) {
        appendLog("    └─ " + recordCount + " records");
      }

      @Override
      public void onImportStarted(String tableName) {
        appendLog("  Importing: " + tableName);
      }

      @Override
      public void onImportCompleted(String tableName, long recordCount) {
        appendLog("    └─ " + recordCount + " records imported");
      }

      @Override
      public void onWarning(String message) {
        appendLog("  WARN: " + message);
      }
    };
  }

  private String validate(boolean exportMode) {
    if (cmbSource.getSelectedItem() == null || ((String) cmbSource.getSelectedItem()).isBlank()) {
      return "Select a source connection.";
    }
    if (!exportMode
        && (cmbTarget.getSelectedItem() == null
            || ((String) cmbTarget.getSelectedItem()).isBlank())) {
      return "Select a target connection (or enable Export mode).";
    }
    if (cmbRootTable.getSelectedItem() == null
        || ((String) cmbRootTable.getSelectedItem()).isBlank()) {
      return "Load tables and select a root table.";
    }
    if (tfEntityId.getText().isBlank()) {
      return "Entity ID is required.";
    }
    return null;
  }

  private void appendLog(String line) {
    SwingUtilities.invokeLater(
        () -> {
          taLog.append(line + "\n");
          taLog.setCaretPosition(taLog.getDocument().getLength());
        });
  }
}
