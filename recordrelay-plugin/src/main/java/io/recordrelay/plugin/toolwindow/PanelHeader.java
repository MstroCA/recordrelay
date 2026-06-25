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

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Branded panel header: blue gradient band with title + subtitle, consistent across all RecordRelay
 * tool-window tabs.
 */
final class PanelHeader extends JPanel {

  private static final JBColor GRAD_TOP = new JBColor(0x1E88E5, 0x1565C0);
  private static final JBColor GRAD_BOT = new JBColor(0x1565C0, 0x0D47A1);
  private static final JBColor BOTTOM_BORDER = new JBColor(0x1350A0, 0x0A3580);

  PanelHeader(String title, String subtitle) {
    super(new BorderLayout());
    setOpaque(false);

    var inner = new JPanel();
    inner.setOpaque(false);
    inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
    inner.setBorder(JBUI.Borders.empty(10, 12, 10, 12));

    var titleLabel = new JLabel(title);
    titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(13f)));
    titleLabel.setForeground(java.awt.Color.WHITE);

    inner.add(titleLabel);

    if (subtitle != null && !subtitle.isBlank()) {
      var subLabel = new JLabel(subtitle);
      subLabel.setFont(subLabel.getFont().deriveFont(Font.PLAIN, JBUI.scaleFontSize(10f)));
      subLabel.setForeground(new java.awt.Color(255, 255, 255, 160));
      inner.add(subLabel);
    }

    add(inner, BorderLayout.CENTER);
    setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BOTTOM_BORDER));
  }

  @Override
  protected void paintComponent(Graphics g) {
    var g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setPaint(new GradientPaint(0, 0, GRAD_TOP, 0, getHeight(), GRAD_BOT));
    g2.fillRect(0, 0, getWidth(), getHeight());
    g2.dispose();
  }
}
