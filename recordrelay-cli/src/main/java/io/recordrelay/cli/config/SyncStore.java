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
import com.fasterxml.jackson.databind.type.CollectionType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the scheduled-sync list at {@code ~/.recordrelay/syncs.json}.
 *
 * <p>Each entry is a {@link ScheduledSync}. Operations load the full list, mutate in memory, and
 * persist atomically.
 */
public final class SyncStore {

  private static final String SYNCS_FILE = "syncs.json";

  private final Path syncsFile;
  private final ObjectMapper mapper;
  private final CollectionType listType;

  /** Creates a store backed by the default directory {@code ~/.recordrelay}. */
  public SyncStore() throws Exception {
    this(Path.of(System.getProperty("user.home"), ".recordrelay"));
  }

  /** Creates a store backed by the given directory. */
  public SyncStore(Path dir) throws Exception {
    this.syncsFile = dir.resolve(SYNCS_FILE);
    this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    this.listType =
        mapper.getTypeFactory().constructCollectionType(List.class, ScheduledSync.class);
  }

  /** Loads all scheduled syncs; returns an empty list when the file does not exist. */
  public List<ScheduledSync> loadAll() throws Exception {
    if (!Files.exists(syncsFile)) {
      return new ArrayList<>();
    }
    return mapper.readValue(syncsFile.toFile(), listType);
  }

  /** Saves the full list to disk, creating the parent directory if needed. */
  public void saveAll(List<ScheduledSync> syncs) throws Exception {
    Files.createDirectories(syncsFile.getParent());
    mapper.writeValue(syncsFile.toFile(), syncs);
  }

  /** Adds a new sync entry (replaces by name if one already exists). */
  public void addOrReplace(ScheduledSync sync) throws Exception {
    var all = loadAll();
    all.removeIf(s -> s.getName().equals(sync.getName()));
    all.add(sync);
    saveAll(all);
  }

  /** Removes a sync entry by name; returns {@code true} when the entry existed. */
  public boolean remove(String name) throws Exception {
    var all = loadAll();
    boolean removed = all.removeIf(s -> s.getName().equals(name));
    if (removed) {
      saveAll(all);
    }
    return removed;
  }

  /** Finds a sync by name. */
  public java.util.Optional<ScheduledSync> findByName(String name) throws Exception {
    return loadAll().stream().filter(s -> s.getName().equals(name)).findFirst();
  }

  /** Updates last-run metadata for the given sync name and persists. */
  public void updateRunResult(String name, String isoTimestamp, String status) throws Exception {
    var all = loadAll();
    for (var s : all) {
      if (s.getName().equals(name)) {
        s.setLastRunAt(isoTimestamp);
        s.setLastStatus(status);
        break;
      }
    }
    saveAll(all);
  }
}
