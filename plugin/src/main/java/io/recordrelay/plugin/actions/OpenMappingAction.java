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
package io.recordrelay.plugin.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.recordrelay.plugin.editor.MappingValidator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;

/** Action that opens a RecordRelay mapping file in the editor and validates its content. */
public final class OpenMappingAction extends AnAction {

  private static final String NOTIFICATION_GROUP = "RecordRelay";

  /** Opens a file chooser, then opens the chosen mapping file in the editor and validates it. */
  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    if (project == null) {
      return;
    }
    var descriptor =
        FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withTitle("Open RecordRelay Mapping")
            .withDescription("Select a JSON or YAML mapping file");
    VirtualFile file = FileChooser.chooseFile(descriptor, project, null);
    if (file == null) {
      return;
    }
    FileEditorManager.getInstance(project).openFile(file, true);
    validateAndNotify(project, file);
  }

  private static void validateAndNotify(Project project, VirtualFile file) {
    String format = MappingValidator.inferFormat(file.getName());
    if (format == null) {
      return;
    }
    String content = readContent(project, file);
    if (content == null) {
      return;
    }
    String errors = MappingValidator.validate(content, format);
    if (errors == null) {
      notify(project, "Mapping is valid", NotificationType.INFORMATION);
    } else {
      notify(project, "Mapping validation failed:\n" + errors, NotificationType.WARNING);
    }
  }

  private static String readContent(Project project, VirtualFile file) {
    try {
      return new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      notify(project, "Could not read file: " + ex.getMessage(), NotificationType.ERROR);
      return null;
    }
  }

  private static void notify(Project project, String message, NotificationType type) {
    Notifications.Bus.notify(
        new Notification(NOTIFICATION_GROUP, "RecordRelay", message, type), project);
  }
}
