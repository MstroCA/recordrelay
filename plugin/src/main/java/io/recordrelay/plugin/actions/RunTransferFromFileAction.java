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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.recordrelay.plugin.editor.MappingValidator;
import org.jetbrains.annotations.NotNull;

/**
 * Context-menu action that validates and optionally runs a transfer from a selected mapping file.
 *
 * <p>The action is only visible when the selected file has a {@code .json} or {@code .yaml}
 * extension; validation runs in a background thread.
 */
public final class RunTransferFromFileAction extends AnAction {

  private static final String NOTIFICATION_GROUP = "RecordRelay";

  /** Shows the action only for JSON/YAML files that could be mapping files. */
  @Override
  public void update(@NotNull AnActionEvent event) {
    VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
    boolean enabled = file != null && MappingValidator.inferFormat(file.getName()) != null;
    event.getPresentation().setEnabledAndVisible(enabled);
  }

  /** Validates the selected mapping file in a background thread, then reports the result. */
  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
    if (project == null || file == null) {
      return;
    }
    runValidation(project, file);
  }

  private static void runValidation(Project project, VirtualFile file) {
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "Validating mapping file…", false) {
              private String validationError;

              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                try {
                  String content =
                      new String(
                          file.contentsToByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                  String format = MappingValidator.inferFormat(file.getName());
                  validationError = MappingValidator.validate(content, format);
                } catch (Exception ex) {
                  validationError = ex.getMessage();
                }
              }

              @Override
              public void onSuccess() {
                if (validationError == null) {
                  notifySuccess(project, file.getName());
                } else {
                  notifyError(project, file.getName(), validationError);
                }
              }
            });
  }

  private static void notifySuccess(Project project, String fileName) {
    Notifications.Bus.notify(
        new Notification(
            NOTIFICATION_GROUP,
            "RecordRelay",
            fileName + " — mapping is valid. Open the Transfer tab to run.",
            NotificationType.INFORMATION),
        project);
  }

  private static void notifyError(Project project, String fileName, String errors) {
    Notifications.Bus.notify(
        new Notification(
            NOTIFICATION_GROUP,
            "RecordRelay — Validation Failed",
            fileName + ":\n" + errors,
            NotificationType.WARNING),
        project);
  }
}
