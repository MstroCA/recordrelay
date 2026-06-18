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
package io.recordrelay.cli.engine;

import io.recordrelay.cli.config.ConfigStore;
import io.recordrelay.core.domain.ConnectionProfile;

/**
 * Resolves a named connection from the CLI config to a {@link ConnectionProfile}.
 *
 * <p>Decrypts the stored password before building the profile; the plaintext password is never
 * written to logs.
 */
public final class ConnProfileResolver {

  private final ConfigStore store;

  public ConnProfileResolver(ConfigStore store) {
    this.store = store;
  }

  /**
   * Resolves {@code connName} to a {@link ConnectionProfile} with a decrypted password.
   *
   * @throws IllegalArgumentException when {@code connName} is not found in the config
   */
  public ConnectionProfile resolve(String connName) throws Exception {
    var config = store.load();
    var entry = config.getConnections().get(connName);
    if (entry == null) {
      throw new IllegalArgumentException("Connection not found in config: " + connName);
    }
    String plainPassword = store.encryptor().decrypt(entry.getEncryptedPassword());
    return entry.toProfile(connName, plainPassword);
  }
}
