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
import io.recordrelay.core.clone.domain.ContextClonePlan;
import io.recordrelay.core.clone.domain.FieldOverride;
import io.recordrelay.core.clone.domain.FieldOverrideConfig;
import io.recordrelay.core.clone.domain.MaskerType;
import io.recordrelay.core.clone.domain.MaskingConfig;
import io.recordrelay.core.clone.domain.MaskingRule;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.engine.clone.BuiltinEntityRegistry;
import io.recordrelay.engine.clone.DefaultContextCloneEngine;
import io.recordrelay.plugin.service.RecordRelayService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;

/** Tool-window panel for cloning a business context from source to target environment. */
public final class CloneContextPanel extends JPanel {

  private final Project project;
  private final ComboBox<String> cmbEntity = new ComboBox<>();
  private final JBTextField tfEntityId = new JBTextField();
  private final ComboBox<String> cmbSource = new ComboBox<>();
  private final ComboBox<String> cmbTarget = new ComboBox<>();
  private final JSpinner spinDepth = new JSpinner(new SpinnerNumberModel(3, 1, 10, 1));
  private final JCheckBox chkMaskPii = new JCheckBox("Mask PII");
  private final JBTextArea taOverrides = new JBTextArea(4, 40);
  private final JButton btnClone = new JButton("Clone Context");
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
    populateEntityCombo();
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

  private void populateEntityCombo() {
    BuiltinEntityRegistry.INSTANCE.listAll().forEach(e -> cmbEntity.addItem(e.displayName()));
    cmbEntity.addItem("Custom…");
  }

  private JPanel buildForm() {
    var panel = new JPanel(new GridBagLayout());
    var gbc = new GridBagConstraints();
    gbc.insets = new Insets(3, 4, 3, 4);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    int row = 0;
    addRow(panel, gbc, row++, "Entity type:", cmbEntity);
    addRow(panel, gbc, row++, "Entity ID:", tfEntityId);
    addRow(panel, gbc, row++, "Source env:", cmbSource);
    addRow(panel, gbc, row++, "Target env:", cmbTarget);
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
    panel.add(btnClone);
    panel.add(lblStatus);
    return panel;
  }

  private void loadConnections() {
    try {
      var names = RecordRelayService.getInstance().configStore().load().getConnections().keySet();
      names.forEach(cmbSource::addItem);
      names.forEach(cmbTarget::addItem);
    } catch (Exception ex) {
      appendLog("Could not load connections: " + ex.getMessage());
    }
  }

  private void onClone() {
    String entityId = tfEntityId.getText().trim();
    if (entityId.isBlank()) {
      lblStatus.setText("Entity ID is required.");
      return;
    }
    String srcName = (String) cmbSource.getSelectedItem();
    String tgtName = (String) cmbTarget.getSelectedItem();
    if (srcName == null || tgtName == null) {
      lblStatus.setText("Select source and target connections.");
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
                runClone(srcName, tgtName, entityId);
              }

              @Override
              public void onFinished() {
                SwingUtilities.invokeLater(() -> btnClone.setEnabled(true));
              }
            });
  }

  private void runClone(String srcName, String tgtName, String entityId) {
    try {
      var resolver = RecordRelayService.getInstance().resolver();
      var srcProfile = resolver.resolve(srcName);
      var tgtProfile = resolver.resolve(tgtName);
      var masking = buildMasking();
      int depth = (int) spinDepth.getValue();

      var entityName = resolveEntityName();
      var entity =
          BuiltinEntityRegistry.INSTANCE
              .findByName(entityName)
              .orElseGet(
                  () ->
                      io.recordrelay.core.clone.domain.BusinessEntity.of(
                          entityName, entityName + "s"));

      var plan =
          ContextClonePlan.liveCloneWithOverrides(
              entity, entityId, srcProfile, tgtProfile, depth, masking, buildFieldOverrides());

      appendLog(
          "Cloning "
              + entity.displayName()
              + " #"
              + entityId
              + " from '"
              + srcName
              + "' → '"
              + tgtName
              + "'");

      var engine = DefaultContextCloneEngine.createDefault();
      var report = engine.cloneContext(plan, buildListener());

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

  private String resolveEntityName() {
    String selected = (String) cmbEntity.getSelectedItem();
    if (selected == null || selected.startsWith("Custom")) {
      return "record";
    }
    return selected.toLowerCase();
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

  private void appendLog(String line) {
    SwingUtilities.invokeLater(
        () -> {
          taLog.append(line + "\n");
          taLog.setCaretPosition(taLog.getDocument().getLength());
        });
  }
}
