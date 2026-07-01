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
import io.recordrelay.cli.config.SatelliteEntry;
import io.recordrelay.core.clone.domain.SatelliteConfig;
import io.recordrelay.core.clone.domain.SatelliteTable;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds a {@link SatelliteConfig} from the companion tables declared for an entity in {@code
 * config.json}, resolving connection names to profiles.
 *
 * <p>Shared by every surface (CLI, Desktop, IntelliJ plugin) so that satellites are defined once,
 * in the config file, and honoured everywhere. The CLI additionally merges ad-hoc {@code
 * --satellite} flags via {@link #parse(String)}.
 */
public final class SatelliteConfigResolver {

  private static final Logger LOG = LoggerFactory.getLogger(SatelliteConfigResolver.class);

  private final ConfigStore store;
  private final ConnProfileResolver resolver;

  public SatelliteConfigResolver(ConfigStore store, ConnProfileResolver resolver) {
    this.store = store;
    this.resolver = resolver;
  }

  /**
   * Returns the satellite tables configured for {@code entityName} in {@code config.json}.
   * Incomplete entries are skipped with a warning. Returns an empty list when none are configured.
   */
  public List<SatelliteTable> forEntity(String entityName) throws Exception {
    var list = new ArrayList<SatelliteTable>();
    if (entityName == null) {
      return list;
    }
    var config = store.load();
    var entries = config.getSatellites() == null ? null : config.getSatellites().get(entityName);
    if (entries == null) {
      return list;
    }
    for (var entry : entries) {
      var satellite = fromEntry(entry, entityName);
      if (satellite != null) {
        list.add(satellite);
      }
    }
    return list;
  }

  /** Convenience wrapper returning a ready {@link SatelliteConfig} for the entity. */
  public SatelliteConfig configForEntity(String entityName) throws Exception {
    var list = forEntity(entityName);
    return list.isEmpty() ? SatelliteConfig.none() : new SatelliteConfig(list);
  }

  private SatelliteTable fromEntry(SatelliteEntry entry, String entityName) throws Exception {
    if (isBlank(entry.getSourceConn())
        || isBlank(entry.getTargetConn())
        || isBlank(entry.getTable())
        || isBlank(entry.getLinkColumn())) {
      LOG.warn("Skipping incomplete satellite entry for entity '{}'", entityName);
      return null;
    }
    return new SatelliteTable(
        resolver.resolve(entry.getSourceConn().trim()),
        resolver.resolve(entry.getTargetConn().trim()),
        entry.getTable().trim(),
        entry.getLinkColumn().trim(),
        isBlank(entry.getPkColumn()) ? null : entry.getPkColumn().trim());
  }

  /**
   * Parses a CLI satellite spec {@code sourceConn>targetConn:table.linkColumn[.pkColumn]} into a
   * {@link SatelliteTable}.
   *
   * @throws IllegalArgumentException when the spec is malformed
   */
  public SatelliteTable parse(String raw) throws Exception {
    var spec = raw == null ? "" : raw.trim();
    int colon = spec.indexOf(':');
    if (colon <= 0) {
      throw new IllegalArgumentException(
          "--satellite: expected 'sourceConn>targetConn:table.linkColumn', got: " + raw);
    }
    var conns = spec.substring(0, colon);
    var body = spec.substring(colon + 1);
    int gt = conns.indexOf('>');
    if (gt <= 0) {
      throw new IllegalArgumentException(
          "--satellite: expected 'sourceConn>targetConn' before ':', got: " + raw);
    }
    var sourceConn = conns.substring(0, gt).trim();
    var targetConn = conns.substring(gt + 1).trim();
    var parts = body.split("\\.");
    if (parts.length < 2 || isBlank(parts[0]) || isBlank(parts[1])) {
      throw new IllegalArgumentException(
          "--satellite: expected 'table.linkColumn[.pkColumn]', got: " + body);
    }
    var pkColumn = parts.length >= 3 && !isBlank(parts[2]) ? parts[2].trim() : null;
    return new SatelliteTable(
        resolver.resolve(sourceConn),
        resolver.resolve(targetConn),
        parts[0].trim(),
        parts[1].trim(),
        pkColumn);
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
