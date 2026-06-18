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
package io.recordrelay.desktop.theme;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;

/** Applies AtlantaFX themes globally to the running JavaFX application. */
public final class ThemeManager {

  private ThemeManager() {}

  /** Switches to PrimerLight (default) or PrimerDark based on {@code dark}. */
  public static void apply(boolean dark) {
    if (dark) {
      Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
    } else {
      Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
    }
  }

  /** Applies the PrimerLight theme. */
  public static void applyLight() {
    apply(false);
  }

  /** Applies the PrimerDark theme. */
  public static void applyDark() {
    apply(true);
  }
}
