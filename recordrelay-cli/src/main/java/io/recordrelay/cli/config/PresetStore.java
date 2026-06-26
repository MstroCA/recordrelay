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
import java.util.Optional;

/** Reads and writes clone presets at {@code ~/.recordrelay/presets.json}. */
public final class PresetStore {

  private static final String PRESETS_FILE = "presets.json";

  private final Path presetsFile;
  private final ObjectMapper mapper;
  private final CollectionType listType;

  public PresetStore() throws Exception {
    this(Path.of(System.getProperty("user.home"), ".recordrelay"));
  }

  public PresetStore(Path dir) throws Exception {
    this.presetsFile = dir.resolve(PRESETS_FILE);
    this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    this.listType = mapper.getTypeFactory().constructCollectionType(List.class, ClonePreset.class);
  }

  public List<ClonePreset> loadAll() throws Exception {
    if (!Files.exists(presetsFile)) {
      return new ArrayList<>();
    }
    return mapper.readValue(presetsFile.toFile(), listType);
  }

  public void saveAll(List<ClonePreset> presets) throws Exception {
    Files.createDirectories(presetsFile.getParent());
    mapper.writeValue(presetsFile.toFile(), presets);
  }

  public void addOrReplace(ClonePreset preset) throws Exception {
    var all = loadAll();
    all.removeIf(p -> p.getName().equals(preset.getName()));
    all.add(preset);
    saveAll(all);
  }

  public boolean remove(String name) throws Exception {
    var all = loadAll();
    boolean removed = all.removeIf(p -> p.getName().equals(name));
    if (removed) {
      saveAll(all);
    }
    return removed;
  }

  public Optional<ClonePreset> findByName(String name) throws Exception {
    return loadAll().stream().filter(p -> p.getName().equals(name)).findFirst();
  }
}
