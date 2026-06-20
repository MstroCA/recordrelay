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
package io.recordrelay.plugin.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import io.recordrelay.cli.config.ConfigStore;
import io.recordrelay.cli.engine.ConnProfileResolver;

/**
 * Application-level service holding shared RecordRelay infrastructure.
 *
 * <p>Registered in {@code plugin.xml}. Obtain via {@link #getInstance()}. Both {@link ConfigStore}
 * and {@link ConnProfileResolver} are lazily initialized on first use.
 */
@Service(Service.Level.APP)
public final class RecordRelayService implements Disposable {

  private ConfigStore configStore;
  private ConnProfileResolver resolver;

  /** Returns the application-level singleton. */
  public static RecordRelayService getInstance() {
    return ApplicationManager.getApplication().getService(RecordRelayService.class);
  }

  /**
   * Returns the lazily-initialized {@link ConfigStore} backed by {@code ~/.recordrelay}.
   *
   * @throws Exception when the config directory or key file cannot be created
   */
  public ConfigStore configStore() throws Exception {
    if (configStore == null) {
      configStore = new ConfigStore();
    }
    return configStore;
  }

  /**
   * Returns the lazily-initialized {@link ConnProfileResolver}.
   *
   * @throws Exception when the underlying {@link ConfigStore} cannot be initialized
   */
  public ConnProfileResolver resolver() throws Exception {
    if (resolver == null) {
      resolver = new ConnProfileResolver(configStore());
    }
    return resolver;
  }

  /** No-op — {@link ConfigStore} holds no closeable resources. */
  @Override
  public void dispose() {}
}
