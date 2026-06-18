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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads and writes the CLI configuration file at {@code ~/.recordrelay/config.json}.
 *
 * <p>Passwords are stored encrypted via {@link CredentialEncryptor} and must never appear as
 * plaintext in the config file or logs.
 */
public final class ConfigStore {

  private static final String CONFIG_FILE = "config.json";
  private static final String KEY_FILE = ".key";

  private final Path configFile;
  private final CredentialEncryptor encryptor;
  private final ObjectMapper mapper;

  /** Creates a store backed by the default directory {@code ~/.recordrelay}. */
  public ConfigStore() throws Exception {
    this(Path.of(System.getProperty("user.home"), ".recordrelay"));
  }

  /** Creates a store backed by the given directory. */
  public ConfigStore(Path dir) throws Exception {
    this.configFile = dir.resolve(CONFIG_FILE);
    this.encryptor = new CredentialEncryptor(dir.resolve(KEY_FILE));
    this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  }

  /** Loads the config, returning an empty config when the file does not exist. */
  public CliConfig load() throws Exception {
    if (!Files.exists(configFile)) {
      return new CliConfig();
    }
    return mapper.readValue(configFile.toFile(), CliConfig.class);
  }

  /** Saves the config to disk, creating the parent directory if needed. */
  public void save(CliConfig config) throws Exception {
    Files.createDirectories(configFile.getParent());
    mapper.writeValue(configFile.toFile(), config);
  }

  /** Returns the encryptor for encrypting and decrypting passwords. */
  public CredentialEncryptor encryptor() {
    return encryptor;
  }

  /** Adds or replaces an environment entry and saves. */
  public void addEnvironment(String name, EnvironmentEntry entry) throws Exception {
    var config = load();
    config.getEnvironments().put(name, entry);
    save(config);
  }

  /** Removes an environment entry; returns {@code true} when the entry existed. */
  public boolean removeEnvironment(String name) throws Exception {
    var config = load();
    boolean removed = config.getEnvironments().remove(name) != null;
    if (removed) {
      save(config);
    }
    return removed;
  }

  /** Adds or replaces a connection entry (password must already be encrypted) and saves. */
  public void addConnection(String name, ConnectionEntry entry) throws Exception {
    var config = load();
    config.getConnections().put(name, entry);
    save(config);
  }

  /** Removes a connection entry; returns {@code true} when the entry existed. */
  public boolean removeConnection(String name) throws Exception {
    var config = load();
    boolean removed = config.getConnections().remove(name) != null;
    if (removed) {
      save(config);
    }
    return removed;
  }

  /** Adds or replaces a job template and saves. */
  public void addJob(String name, JobEntry entry) throws Exception {
    var config = load();
    config.getJobs().put(name, entry);
    save(config);
  }

  /** Removes a job template; returns {@code true} when it existed. */
  public boolean removeJob(String name) throws Exception {
    var config = load();
    boolean removed = config.getJobs().remove(name) != null;
    if (removed) {
      save(config);
    }
    return removed;
  }
}
