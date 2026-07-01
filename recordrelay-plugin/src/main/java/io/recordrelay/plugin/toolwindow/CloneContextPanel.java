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
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import io.recordrelay.core.clone.domain.BusinessEntity;
import io.recordrelay.core.clone.domain.ConflictResolution;
import io.recordrelay.core.clone.domain.ContextClonePlan;
import io.recordrelay.core.clone.domain.DryRunReport;
import io.recordrelay.core.clone.domain.FieldOverride;
import io.recordrelay.core.clone.domain.FieldOverrideConfig;
import io.recordrelay.core.clone.domain.MaskerType;
import io.recordrelay.core.clone.domain.MaskingConfig;
import io.recordrelay.core.clone.domain.MaskingRule;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.i18n.Messages;
import io.recordrelay.core.spi.ConnectorRegistry;
import io.recordrelay.engine.clone.DefaultContextCloneEngine;
import io.recordrelay.plugin.service.RecordRelayService;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
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
  private final JCheckBox chkMaskPii =
      new JCheckBox("Mask PII (email, phone, IBAN, address, national ID)");
  private final ComboBox<ConflictResolution> cmbConflict =
      new ComboBox<>(ConflictResolution.values());
  private final JBTextField tfIdStart = new JBTextField(8);

  // Step 4: Field Overrides
  private final JBTextArea taOverrides = new JBTextArea(4, 40);

  // Step 5: Satellite (companion) tables in a separate database
  private final JBTextArea taSatellites = new JBTextArea(3, 40);

  // Export output dir (visible only in export mode)
  private final JBTextField tfOutputDir = new JBTextField();
  private final JButton btnBrowseDir = new JButton("Browse…");
  private final JPanel pnlOutputDir = new JPanel(new BorderLayout(4, 0));

  // Actions & log
  private final JButton btnClone = new JButton("Clone Context");
  private final JButton btnExport = new JButton("Export Context");
  private final JButton btnDryRun = new JButton("Preview (Dry Run)");
  private final JBTextArea taLog = new JBTextArea(8, 40);
  private final JLabel lblStatus = new JLabel("Ready");

  /** Creates the panel for the given project. */
  public CloneContextPanel(Project project) {
    super(new BorderLayout(0, 0));
    this.project = project;
    taLog.setEditable(false);
    taLog.setLineWrap(true);
    taOverrides.setLineWrap(false);
    taOverrides.setToolTipText("One override per line: column=value  or  table:column=value");
    taSatellites.setLineWrap(false);
    taSatellites.setToolTipText(
        "One satellite per line: sourceConn>targetConn:table.linkColumn[.pkColumn]");

    setupExportModeToggle();
    btnLoadTables.addActionListener(e -> onLoadTables());

    // Steps 1–4 in a scrollable area
    var stepsPanel = new JPanel();
    stepsPanel.setLayout(new BoxLayout(stepsPanel, BoxLayout.Y_AXIS));
    stepsPanel.setBorder(JBUI.Borders.empty(8));
    stepsPanel.add(section("Step 1 — Connections", buildConnectionsForm()));
    stepsPanel.add(Box.createVerticalStrut(JBUI.scale(8)));
    stepsPanel.add(section("Step 2 — Table & Record", buildTableForm()));
    stepsPanel.add(Box.createVerticalStrut(JBUI.scale(8)));
    stepsPanel.add(section("Step 3 — Options", buildOptionsForm()));
    stepsPanel.add(Box.createVerticalStrut(JBUI.scale(8)));
    stepsPanel.add(section("Step 4 — Field Overrides (optional)", buildOverridesForm()));
    stepsPanel.add(Box.createVerticalStrut(JBUI.scale(8)));
    stepsPanel.add(section("Step 5 — Satellite Tables (optional)", buildSatellitesForm()));

    // Log always visible below the scroll area
    var logSection = section("Log", buildLogForm());

    var center = new JPanel(new BorderLayout(0, JBUI.scale(4)));
    center.add(new JBScrollPane(stepsPanel), BorderLayout.CENTER);
    center.add(logSection, BorderLayout.SOUTH);

    add(
        new PanelHeader("Clone Context", "Copy a record and its FK-linked data between databases"),
        BorderLayout.NORTH);
    add(center, BorderLayout.CENTER);
    add(buildButtonBar(), BorderLayout.SOUTH);

    loadConnections();
  }

  // ── Layout helpers ─────────────────────────────────────────────────────────

  private static JPanel section(String title, JComponent content) {
    var panel = new JPanel(new BorderLayout());
    panel.setBorder(
        BorderFactory.createCompoundBorder(
            IdeBorderFactory.createTitledBorder(title), JBUI.Borders.empty(4, 8, 6, 8)));
    panel.setAlignmentX(Component.LEFT_ALIGNMENT);
    panel.add(content, BorderLayout.CENTER);
    return panel;
  }

  private JPanel buildConnectionsForm() {
    var panel = new JPanel(new GridBagLayout());
    var gbc = defaultGbc();

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    gbc.weightx = 1.0;
    panel.add(chkExportMode, gbc);
    gbc.gridwidth = 1;

    addRow(panel, gbc, 1, "Source:", cmbSource);
    addRow(panel, gbc, 2, "Target:", cmbTarget);

    gbc.gridx = 0;
    gbc.gridy = 3;
    gbc.gridwidth = 2;
    gbc.weightx = 1.0;
    panel.add(pnlOutputDir, gbc);

    return panel;
  }

  private JPanel buildTableForm() {
    var panel = new JPanel(new GridBagLayout());
    var gbc = defaultGbc();

    var loadRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    loadRow.add(btnLoadTables);
    loadRow.add(lblTableCount);
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    gbc.weightx = 1.0;
    panel.add(loadRow, gbc);
    gbc.gridwidth = 1;

    addRow(panel, gbc, 1, "Root table:", cmbRootTable);
    addRow(panel, gbc, 2, "PK column:", tfPkColumn);
    addRow(panel, gbc, 3, "Entity ID:", tfEntityId);
    return panel;
  }

  private JPanel buildOptionsForm() {
    var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
    var depthRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    depthRow.add(new JLabel("Relationship depth:"));
    depthRow.add(spinDepth);
    panel.add(depthRow);
    panel.add(chkMaskPii);
    var conflictRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    conflictRow.add(new JLabel("On conflict:"));
    cmbConflict.setRenderer(
        SimpleListCellRenderer.create(
            (renderer, v, idx) ->
                renderer.setText(
                    v == null
                        ? ""
                        : switch (v) {
                          case REGENERATE_IDENTITIES -> "Regenerate IDs (default)";
                          case SKIP_EXISTING -> "Skip existing rows";
                          case ISOLATE_NAMESPACE -> "Isolate namespace";
                          case FAIL_SAFE -> "Fail if target has data";
                          case SEQUENCE -> "Native sequence (DB-assigned IDs)";
                          case START_AT -> "Start at custom ID";
                        })));
    conflictRow.add(cmbConflict);
    panel.add(conflictRow);
    var idStartRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    idStartRow.add(new JLabel("Start ID (START_AT):"));
    idStartRow.add(tfIdStart);
    panel.add(idStartRow);
    return panel;
  }

  private JComponent buildOverridesForm() {
    var container = new JPanel(new BorderLayout(0, JBUI.scale(4)));
    var hint =
        new JLabel(
            "<html><small>One override per line: <code>column=value</code>"
                + " &nbsp;or&nbsp; <code>table:column=value</code></small></html>");
    container.add(hint, BorderLayout.NORTH);
    taOverrides.setRows(4);
    container.add(new JBScrollPane(taOverrides), BorderLayout.CENTER);
    return container;
  }

  private JComponent buildSatellitesForm() {
    var container = new JPanel(new BorderLayout(0, JBUI.scale(4)));
    var hint =
        new JLabel(
            "<html><small>Sync a companion table from another database after the clone"
                + " (e.g. a Kafka-fed <code>read_model</code>). One per line:"
                + " <code>sourceConn&gt;targetConn:table.linkColumn[.pkColumn]</code>."
                + " The link column is remapped to the new root id; overrides are applied."
                + "</small></html>");
    container.add(hint, BorderLayout.NORTH);
    taSatellites.setRows(3);
    container.add(new JBScrollPane(taSatellites), BorderLayout.CENTER);

    var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    var btnLoad = new JButton("Load from config");
    var btnSave = new JButton("Save to config");
    btnLoad.addActionListener(e -> onLoadSatellites());
    btnSave.addActionListener(e -> onSaveSatellites());
    buttons.add(btnLoad);
    buttons.add(btnSave);
    container.add(buttons, BorderLayout.SOUTH);
    return container;
  }

  private JComponent buildLogForm() {
    var container = new JPanel(new BorderLayout(0, JBUI.scale(4)));
    container.add(lblStatus, BorderLayout.NORTH);
    taLog.setRows(7);
    container.add(new JBScrollPane(taLog), BorderLayout.CENTER);
    return container;
  }

  private static GridBagConstraints defaultGbc() {
    var gbc = new GridBagConstraints();
    gbc.insets = new Insets(3, 4, 3, 4);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    return gbc;
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
    var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
    btnClone.addActionListener(e -> onClone());
    btnExport.addActionListener(e -> onExport());
    btnDryRun.addActionListener(e -> onDryRun());
    panel.add(btnClone);
    panel.add(btnExport);
    panel.add(btnDryRun);
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

  private void onDryRun() {
    String err = validate(false);
    if (err != null) {
      JOptionPane.showMessageDialog(this, err, "Validation", JOptionPane.WARNING_MESSAGE);
      return;
    }
    btnDryRun.setEnabled(false);
    taLog.setText("");
    lblStatus.setText("Running preview…");

    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "RecordRelay — dry run preview…", false) {
              private DryRunReport report;
              private String error;

              @Override
              public void run(@NotNull com.intellij.openapi.progress.ProgressIndicator indicator) {
                try {
                  var resolver = RecordRelayService.getInstance().resolver();
                  var srcProfile = resolver.resolve((String) cmbSource.getSelectedItem());
                  var entity = buildEntity();
                  var entityId = tfEntityId.getText().trim();
                  int depth = (int) spinDepth.getValue();

                  var plan =
                      ContextClonePlan.liveClone(
                          entity, entityId, srcProfile, srcProfile, depth, MaskingConfig.none());
                  report = DefaultContextCloneEngine.createDefault().dryRunContext(plan);
                } catch (Exception ex) {
                  error = ex.getMessage();
                }
              }

              @Override
              public void onSuccess() {
                SwingUtilities.invokeLater(
                    () -> {
                      btnDryRun.setEnabled(true);
                      if (error != null) {
                        lblStatus.setText("Preview failed");
                        appendLog("ERROR: " + error);
                      } else {
                        showDryRunResult(report);
                      }
                    });
              }

              @Override
              public void onFinished() {
                SwingUtilities.invokeLater(() -> btnDryRun.setEnabled(true));
              }
            });
  }

  private void showDryRunResult(DryRunReport report) {
    var sb = new StringBuilder();
    sb.append(
        String.format(
            "PREVIEW: %d table(s), %d row(s) — %d ms%n%n",
            report.tableCount(), report.totalRows(), report.durationMillis()));
    sb.append(String.format("%-40s %8s %6s%n", "TABLE", "ROWS", "DEPTH"));
    sb.append("-".repeat(56)).append("\n");
    for (var entry : report.tables()) {
      sb.append(
          String.format("%-40s %8d %6d%n", entry.tableName(), entry.rowCount(), entry.minDepth()));
    }
    sb.append("%nNo data was written.");
    taLog.setText(sb.toString());
    taLog.setCaretPosition(0);
    lblStatus.setText(
        String.format("Preview: %d tables, %d rows", report.tableCount(), report.totalRows()));
  }

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

      var conflict = (ConflictResolution) cmbConflict.getSelectedItem();
      var satellites = buildSatellites(entity.name());
      if (!satellites.isEmpty()) {
        appendLog("Satellite tables: " + satellites.satellites().size() + " definition(s)");
      }
      var plan =
          ContextClonePlan.liveCloneWithOverrides(
              entity,
              entityId,
              srcProfile,
              tgtProfile,
              depth,
              masking,
              buildFieldOverrides(),
              conflict != null ? conflict : ConflictResolution.REGENERATE_IDENTITIES,
              satellites,
              parseIdStart());

      var report = DefaultContextCloneEngine.createDefault().cloneContext(plan, buildListener());
      logCloneReport(report);
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
    if (rootTable == null || rootTable.isBlank()) {
      rootTable = "records";
    }
    String pk = tfPkColumn.getText().trim();
    if (pk.isBlank()) {
      pk = "id";
    }
    return BusinessEntity.of(rootTable, rootTable, pk, "");
  }

  private FieldOverrideConfig buildFieldOverrides() {
    var list = new ArrayList<FieldOverride>();
    for (var line : taOverrides.getText().lines().toList()) {
      var raw = line.trim();
      if (raw.isBlank() || raw.startsWith("#")) {
        continue;
      }
      int colonIdx = raw.indexOf(':');
      int eqIdx = raw.indexOf('=');
      if (eqIdx < 0) {
        continue;
      }
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

  /**
   * Builds the satellite config from the editor: parses the textarea lines, or, when empty, falls
   * back to whatever is saved for the entity in config.json.
   */
  private io.recordrelay.core.clone.domain.SatelliteConfig buildSatellites(String entityName)
      throws Exception {
    var service = RecordRelayService.getInstance();
    var satResolver =
        new io.recordrelay.cli.engine.SatelliteConfigResolver(
            service.configStore(), service.resolver());
    var lines =
        taSatellites
            .getText()
            .lines()
            .map(String::trim)
            .filter(l -> !l.isBlank() && !l.startsWith("#"))
            .toList();
    if (lines.isEmpty()) {
      return satResolver.configForEntity(entityName);
    }
    var list = new ArrayList<io.recordrelay.core.clone.domain.SatelliteTable>();
    for (var line : lines) {
      list.add(satResolver.parse(line));
    }
    return new io.recordrelay.core.clone.domain.SatelliteConfig(list);
  }

  private void onLoadSatellites() {
    try {
      var entityName = (String) cmbRootTable.getSelectedItem();
      if (entityName == null || entityName.isBlank()) {
        appendLog("Select a root table first.");
        return;
      }
      var config = RecordRelayService.getInstance().configStore().load();
      var entries = config.getSatellites() == null ? null : config.getSatellites().get(entityName);
      if (entries == null || entries.isEmpty()) {
        taSatellites.setText("");
        appendLog("No satellites configured for " + entityName + ".");
        return;
      }
      var sb = new StringBuilder();
      for (var e : entries) {
        sb.append(e.getSourceConn())
            .append('>')
            .append(e.getTargetConn())
            .append(':')
            .append(e.getTable())
            .append('.')
            .append(e.getLinkColumn());
        if (e.getPkColumn() != null && !e.getPkColumn().isBlank()) {
          sb.append('.').append(e.getPkColumn());
        }
        sb.append('\n');
      }
      taSatellites.setText(sb.toString());
    } catch (Exception ex) {
      appendLog("Failed to load satellites: " + ex.getMessage());
    }
  }

  private void onSaveSatellites() {
    try {
      var entityName = (String) cmbRootTable.getSelectedItem();
      if (entityName == null || entityName.isBlank()) {
        appendLog("Select a root table first.");
        return;
      }
      var config = RecordRelayService.getInstance().configStore().load();
      var entries = new ArrayList<io.recordrelay.cli.config.SatelliteEntry>();
      for (var line : taSatellites.getText().lines().map(String::trim).toList()) {
        if (line.isBlank() || line.startsWith("#")) {
          continue;
        }
        var entry = parseSatelliteEntry(line);
        if (entry != null) {
          entries.add(entry);
        }
      }
      if (entries.isEmpty()) {
        config.getSatellites().remove(entityName);
      } else {
        config.getSatellites().put(entityName, entries);
      }
      RecordRelayService.getInstance().configStore().save(config);
      appendLog("Saved " + entries.size() + " satellite(s) for " + entityName + ".");
    } catch (Exception ex) {
      appendLog("Failed to save satellites: " + ex.getMessage());
    }
  }

  /** Structurally parses a satellite spec line into a config entry (no connection resolution). */
  private static io.recordrelay.cli.config.SatelliteEntry parseSatelliteEntry(String spec) {
    int colon = spec.indexOf(':');
    int gt = spec.indexOf('>');
    if (colon <= 0 || gt <= 0 || gt >= colon) {
      return null;
    }
    var parts = spec.substring(colon + 1).split("\\.");
    if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
      return null;
    }
    var entry = new io.recordrelay.cli.config.SatelliteEntry();
    entry.setSourceConn(spec.substring(0, gt).trim());
    entry.setTargetConn(spec.substring(gt + 1, colon).trim());
    entry.setTable(parts[0].trim());
    entry.setLinkColumn(parts[1].trim());
    if (parts.length >= 3 && !parts[2].isBlank()) {
      entry.setPkColumn(parts[2].trim());
    }
    return entry;
  }

  private void logCloneReport(io.recordrelay.core.clone.domain.CloneReport report) {
    appendLog(
        "Clone complete — " + report.totalRecords() + " records in " + report.formattedDuration());
    if (!report.warnings().isEmpty()) {
      appendLog("WARNINGS:");
      report.warnings().forEach(w -> appendLog("  ⚠ " + w));
    }
    var statusText =
        "Done — "
            + report.totalRecords()
            + " records"
            + (report.warnings().isEmpty() ? "" : " (" + report.warnings().size() + " warnings)");
    SwingUtilities.invokeLater(() -> lblStatus.setText(statusText));
  }

  /** Parses the optional Start-ID field; returns {@code null} when blank or non-numeric. */
  private Long parseIdStart() {
    var text = tfIdStart.getText();
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(text.trim());
    } catch (NumberFormatException e) {
      appendLog("Start ID is not numeric, ignored: " + text);
      return null;
    }
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
      public void onTableExtractionCompleted(String tableName, long recordCount) {
        appendLog(Messages.get("clone.fetched", tableName, recordCount));
      }

      @Override
      public void onImportCompleted(String tableName, long recordCount) {
        appendLog(Messages.get("clone.written", tableName, recordCount));
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
