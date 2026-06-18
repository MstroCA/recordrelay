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
package io.recordrelay.cli.config;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DatabaseType;
import java.util.Map;

/** POJO representing a database connection entry stored in the CLI config file. */
public class ConnectionEntry {

  private String environment = "default";
  private String type;
  private String host;
  private int port;
  private String database;
  private String user;

  /** Password stored as {@code ENC(AES256:...)} — never plaintext. */
  private String encryptedPassword;

  public ConnectionEntry() {}

  /** Converts this entry to a {@link ConnectionProfile} using the supplied decrypted password. */
  public ConnectionProfile toProfile(String name, String plainPassword) {
    return new ConnectionProfile(
        name,
        name,
        environment != null ? environment : "default",
        DatabaseType.valueOf(type.toUpperCase()),
        host,
        port,
        database,
        new Credentials(user, plainPassword),
        Map.of());
  }

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getDatabase() {
    return database;
  }

  public void setDatabase(String database) {
    this.database = database;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public String getEncryptedPassword() {
    return encryptedPassword;
  }

  public void setEncryptedPassword(String encryptedPassword) {
    this.encryptedPassword = encryptedPassword;
  }
}
