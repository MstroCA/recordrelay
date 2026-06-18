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
package io.recordrelay.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AppResourcesTest {

  private static final String FXML_ROOT = "/io/recordrelay/desktop/fxml/";
  private static final String CSS_ROOT = "/io/recordrelay/desktop/css/";

  @ParameterizedTest
  @ValueSource(
      strings = {
        "main.fxml",
        "environments.fxml",
        "connections.fxml",
        "discovery.fxml",
        "transfer.fxml",
        "monitor.fxml"
      })
  void fxmlResourceExists(String name) {
    var url = AppResourcesTest.class.getResource(FXML_ROOT + name);
    assertThat(url).as("FXML resource " + name + " must exist on classpath").isNotNull();
  }

  @Test
  void cssResourceExists() {
    var url = AppResourcesTest.class.getResource(CSS_ROOT + "recordrelay.css");
    assertThat(url).as("CSS resource must exist on classpath").isNotNull();
  }

  @Test
  void viewModelPackageIsAccessible() {
    assertThat(io.recordrelay.desktop.viewmodel.BaseViewModel.class).isNotNull();
    assertThat(io.recordrelay.desktop.viewmodel.EnvironmentViewModel.class).isNotNull();
    assertThat(io.recordrelay.desktop.viewmodel.ConnectionViewModel.class).isNotNull();
    assertThat(io.recordrelay.desktop.viewmodel.TransferViewModel.class).isNotNull();
    assertThat(io.recordrelay.desktop.viewmodel.MonitorViewModel.class).isNotNull();
    assertThat(io.recordrelay.desktop.viewmodel.DiscoveryViewModel.class).isNotNull();
    assertThat(io.recordrelay.desktop.viewmodel.MappingViewModel.class).isNotNull();
  }

  @Test
  void themeManagerClassIsAccessible() {
    assertThat(io.recordrelay.desktop.theme.ThemeManager.class).isNotNull();
  }
}
