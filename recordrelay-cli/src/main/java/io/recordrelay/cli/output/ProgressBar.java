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
package io.recordrelay.cli.output;

/**
 * Renders an ANSI progress bar to stdout during long-running transfers.
 *
 * <p>Automatically disables itself when stdout is not a TTY (CI/piped environments), so scripts can
 * safely capture CLI output without ANSI noise.
 */
public final class ProgressBar {

  private static final int BAR_WIDTH = 40;
  private static final int CLEAR_WIDTH = 80;

  private final boolean enabled;

  /** Creates a progress bar that auto-detects TTY availability. */
  public ProgressBar() {
    this(System.console() != null);
  }

  /** Creates a progress bar with explicit TTY control (for testing). */
  ProgressBar(boolean enabled) {
    this.enabled = enabled;
  }

  /** Updates the bar. When {@code total} is negative the count is shown without a percentage. */
  public void update(long transferred, long total) {
    if (!enabled) {
      return;
    }
    if (total > 0) {
      renderWithPercentage(transferred, total);
    } else {
      System.out.printf("\rTransferred: %,d records", transferred);
    }
    System.out.flush();
  }

  private void renderWithPercentage(long transferred, long total) {
    int pct = (int) (transferred * 100 / total);
    int filled = pct * BAR_WIDTH / 100;
    System.out.printf(
        "\r[%-" + BAR_WIDTH + "s] %3d%% (%,d / %,d)", "#".repeat(filled), pct, transferred, total);
  }

  /** Clears the bar and prints the final transferred count on its own line. */
  public void complete(long transferred) {
    if (!enabled) {
      return;
    }
    System.out.printf("\rTransferred: %,d records%n", transferred);
    System.out.flush();
  }

  /** Erases the bar line (used when an error occurs mid-transfer). */
  public void clear() {
    if (!enabled) {
      return;
    }
    System.out.print("\r" + " ".repeat(CLEAR_WIDTH) + "\r");
    System.out.flush();
  }
}
