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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBTextField;
import io.recordrelay.core.domain.ColumnMapping;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.MappingDefinition;
import io.recordrelay.core.domain.MappingFormat;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.domain.TransactionIsolation;
import io.recordrelay.core.domain.TransferJob;
import io.recordrelay.core.domain.TransferMode;
import io.recordrelay.core.domain.TransferOptions;
import io.recordrelay.core.domain.TransferResult;
import io.recordrelay.core.engine.DefaultTransferEngine;
import io.recordrelay.core.engine.SyncPipeline;
import io.recordrelay.core.port.out.TransferProgressListener;
import io.recordrelay.plugin.service.RecordRelayService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import org.jetbrains.annotations.NotNull;

/** Tool-window panel for configuring and executing ETL transfer jobs. */
public final class TransferPanel extends JPanel {

  private static final String DEFAULT_MODE = "SYNC";
  private static final String DEFAULT_ISOLATION = "READ_COMMITTED";
  private static final int DEFAULT_CHUNK = 1000;

  private final Project project;
  private final ComboBox<String> cmbSrcConn = new ComboBox<>();
  private final ComboBox<String> cmbTgtConn = new ComboBox<>();
  private final JBTextField tfSrcTable = new JBTextField();
  private final JBTextField tfTgtTable = new JBTextField();
  private final TextFieldWithBrowseButton tfMapping = new TextFieldWithBrowseButton();
  private final ComboBox<String> cmbMode = new ComboBox<>(new String[] {"SYNC", "ASYNC", "BATCH"});
  private final ComboBox<String> cmbIsolation = buildIsolationCombo();
  private final JBTextField tfChunk = new JBTextField(String.valueOf(DEFAULT_CHUNK), 8);
  private final JProgressBar progress = new JProgressBar(0, 100);
  private final JLabel lblStatus = new JLabel("Ready");
  private final JButton btnRun = new JButton("Run Transfer");
  private final JButton btnCancel = new JButton("Cancel");
  private volatile boolean cancelRequested = false;

  /** Creates the panel for the given project. */
  public TransferPanel(Project project) {
    super(new BorderLayout(0, 6));
    this.project = project;
    tfMapping.addBrowseFolderListener(
        project,
        FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withTitle("Select Mapping File")
            .withDescription("JSON or YAML column mapping file"));
    cmbMode.setSelectedItem(DEFAULT_MODE);
    cmbIsolation.setSelectedItem(DEFAULT_ISOLATION);
    btnCancel.setEnabled(false);
    btnRun.addActionListener(e -> onRun());
    btnCancel.addActionListener(e -> onCancel());
    add(buildFormPanel(), BorderLayout.NORTH);
    add(buildProgressPanel(), BorderLayout.SOUTH);
    loadConnections();
  }

  private static ComboBox<String> buildIsolationCombo() {
    return new ComboBox<>(
        new String[] {"READ_UNCOMMITTED", "READ_COMMITTED", "REPEATABLE_READ", "SERIALIZABLE"});
  }

  private JPanel buildFormPanel() {
    var grid = new JPanel(new GridLayout(8, 2, 6, 4));
    grid.add(new JLabel("Source connection:"));
    grid.add(cmbSrcConn);
    grid.add(new JLabel("Source table (db.table):"));
    grid.add(tfSrcTable);
    grid.add(new JLabel("Target connection:"));
    grid.add(cmbTgtConn);
    grid.add(new JLabel("Target table (db.table):"));
    grid.add(tfTgtTable);
    grid.add(new JLabel("Mapping file:"));
    grid.add(tfMapping);
    grid.add(new JLabel("Transfer mode:"));
    grid.add(cmbMode);
    grid.add(new JLabel("Transaction isolation:"));
    grid.add(cmbIsolation);
    grid.add(new JLabel("Chunk size:"));
    grid.add(tfChunk);
    return grid;
  }

  private JPanel buildProgressPanel() {
    progress.setStringPainted(true);
    var bar = new JPanel(new BorderLayout(0, 4));
    bar.add(progress, BorderLayout.CENTER);
    bar.add(lblStatus, BorderLayout.SOUTH);
    var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    buttons.add(btnRun);
    buttons.add(btnCancel);
    var panel = new JPanel(new BorderLayout(0, 6));
    panel.add(buttons, BorderLayout.NORTH);
    panel.add(bar, BorderLayout.CENTER);
    return panel;
  }

  private void loadConnections() {
    try {
      var names = RecordRelayService.getInstance().configStore().load().getConnections().keySet();
      names.forEach(cmbSrcConn::addItem);
      names.forEach(cmbTgtConn::addItem);
    } catch (Exception ex) {
      Messages.showErrorDialog(project, ex.getMessage(), "RecordRelay — Transfer");
    }
  }

  private void onCancel() {
    cancelRequested = true;
    btnCancel.setEnabled(false);
    lblStatus.setText("Cancelling…");
  }

  private void onRun() {
    try {
      var job = buildJob();
      cancelRequested = false;
      btnRun.setEnabled(false);
      btnCancel.setEnabled(true);
      progress.setValue(0);
      runTransfer(job);
    } catch (Exception ex) {
      Messages.showErrorDialog(project, ex.getMessage(), "RecordRelay — Transfer");
    }
  }

  private void runTransfer(TransferJob job) {
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "Transferring data…", true) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                try {
                  new DefaultTransferEngine(new SyncPipeline(), null)
                      .transfer(job, buildListener(indicator));
                } catch (Exception ex) {
                  Messages.showErrorDialog(project, ex.getMessage(), "RecordRelay — Transfer");
                }
              }

              @Override
              public void onFinished() {
                btnRun.setEnabled(true);
                btnCancel.setEnabled(false);
              }
            });
  }

  private TransferProgressListener buildListener(ProgressIndicator indicator) {
    return new TransferProgressListener() {
      @Override
      public void onStart(TransferJob j) {
        lblStatus.setText("Running…");
      }

      @Override
      public void onProgress(TransferJob j, long done, long total) {
        if (total > 0) {
          int pct = (int) (100.0 * done / total);
          progress.setValue(pct);
          indicator.setFraction((double) done / total);
        }
        indicator.setText2(String.format("Transferred %,d / %,d", done, total));
        if (cancelRequested) {
          indicator.cancel();
        }
      }

      @Override
      public void onComplete(TransferResult result) {
        progress.setValue(100);
        lblStatus.setText(
            String.format("Done — %,d records transferred", result.transferredCount()));
      }

      @Override
      public void onError(TransferJob j, Exception ex) {
        lblStatus.setText("Error: " + ex.getMessage());
      }
    };
  }

  private TransferJob buildJob() throws Exception {
    var srcProfile = RecordRelayService.getInstance().resolver().resolve(srcConn());
    var tgtProfile = RecordRelayService.getInstance().resolver().resolve(tgtConn());
    var srcRef = parseTableRef(tfSrcTable.getText().trim(), srcProfile);
    var tgtRef = parseTableRef(tfTgtTable.getText().trim(), tgtProfile);
    var mappings = loadMappings();
    var mapping =
        new MappingDefinition(
            UUID.randomUUID().toString(), srcRef, tgtRef, mappings, MappingFormat.DIRECT, null);
    return new TransferJob(
        UUID.randomUUID().toString(),
        "plugin-transfer",
        srcProfile,
        tgtProfile,
        mapping,
        TransferMode.valueOf((String) cmbMode.getSelectedItem()),
        chunkSize(),
        TransactionIsolation.valueOf((String) cmbIsolation.getSelectedItem()),
        TransferOptions.defaults());
  }

  private String srcConn() {
    return (String) cmbSrcConn.getSelectedItem();
  }

  private String tgtConn() {
    return (String) cmbTgtConn.getSelectedItem();
  }

  private int chunkSize() {
    try {
      return Integer.parseInt(tfChunk.getText().trim());
    } catch (NumberFormatException ex) {
      return DEFAULT_CHUNK;
    }
  }

  private static TableRef parseTableRef(String path, ConnectionProfile profile) {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("Table path must not be blank");
    }
    String[] parts = path.split("\\.", 2);
    if (parts.length == 2) {
      return new TableRef(new DatabaseRef(parts[0], profile.type()), "", parts[1]);
    }
    return new TableRef(new DatabaseRef(profile.database(), profile.type()), "", parts[0]);
  }

  @SuppressWarnings("unchecked")
  private List<ColumnMapping> loadMappings() throws Exception {
    String path = tfMapping.getText().trim();
    if (path.isBlank()) {
      return List.of();
    }
    var file = new File(path);
    if (!file.exists()) {
      return List.of();
    }
    var mapper = new ObjectMapper();
    var type = mapper.getTypeFactory().constructCollectionType(List.class, Map.class);
    List<Map<String, String>> rows = mapper.readValue(file, type);
    var result = new ArrayList<ColumnMapping>();
    for (var m : rows) {
      result.add(new ColumnMapping(m.get("source"), m.get("target"), null));
    }
    return result;
  }
}
