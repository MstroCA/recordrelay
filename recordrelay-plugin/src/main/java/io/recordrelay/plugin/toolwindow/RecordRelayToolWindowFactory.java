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
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Factory that populates the RecordRelay tool window with tabs: Connections, Discovery, Monitor.
 */
public final class RecordRelayToolWindowFactory implements ToolWindowFactory {

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    var factory = ContentFactory.getInstance();
    var mgr = toolWindow.getContentManager();
    mgr.addContent(factory.createContent(new CloneContextPanel(project), "Clone", false));
    mgr.addContent(factory.createContent(new ConnectionsPanel(project), "Connections", false));
    mgr.addContent(factory.createContent(new DiscoveryPanel(project), "Discovery", false));
    mgr.addContent(factory.createContent(new MonitorPanel(project), "Monitor", false));
  }
}
