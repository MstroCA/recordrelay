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
import io.recordrelay.core.domain.ConnectionHealthEntry;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.HealthStatus;
import io.recordrelay.core.spi.ConnectorRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Probes all saved connections in parallel and returns a {@link ConnectionHealthEntry} for each.
 *
 * <p>Profiles are resolved on the calling thread first (fast, config-only). Only the live {@code
 * healthCheck()} calls are parallelised, each with a hard timeout of {@value #TIMEOUT_SECONDS}
 * seconds so a hanging host cannot block the entire run.
 */
public final class ConnectionHealthEngine {

  private static final int TIMEOUT_SECONDS = 10;

  private final ConfigStore store;
  private final ConnProfileResolver resolver;

  public ConnectionHealthEngine(ConfigStore store) {
    this.store = store;
    this.resolver = new ConnProfileResolver(store);
  }

  /**
   * Checks every saved connection and returns results ordered by connection name.
   *
   * @return list of health entries, one per saved connection
   */
  public List<ConnectionHealthEntry> checkAll() {
    List<String> names;
    try {
      names = new ArrayList<>(store.load().getConnections().keySet());
    } catch (Exception e) {
      return List.of();
    }
    names.sort(String.CASE_INSENSITIVE_ORDER);

    // Resolve profiles synchronously (config read only — fast)
    Map<String, ConnectionProfile> profiles = new LinkedHashMap<>();
    List<ConnectionHealthEntry> resolveErrors = new ArrayList<>();
    for (String name : names) {
      try {
        profiles.put(name, resolver.resolve(name));
      } catch (Exception e) {
        resolveErrors.add(
            new ConnectionHealthEntry(
                name, "—", null, HealthStatus.Status.DOWN, -1L, "Config error: " + e.getMessage()));
      }
    }

    // Probe live connections in parallel
    ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
    Map<String, Future<ConnectionHealthEntry>> futures = new LinkedHashMap<>();
    for (var entry : profiles.entrySet()) {
      var profile = entry.getValue();
      futures.put(entry.getKey(), pool.submit(() -> probe(entry.getKey(), profile)));
    }
    pool.shutdown();

    var results = new ArrayList<ConnectionHealthEntry>();
    results.addAll(resolveErrors);
    for (var entry : futures.entrySet()) {
      try {
        results.add(entry.getValue().get(TIMEOUT_SECONDS + 2L, TimeUnit.SECONDS));
      } catch (Exception e) {
        var profile = profiles.get(entry.getKey());
        results.add(
            new ConnectionHealthEntry(
                entry.getKey(),
                profile.host() + ":" + profile.port(),
                profile.type(),
                HealthStatus.Status.DOWN,
                -1L,
                "Timeout — no response within " + TIMEOUT_SECONDS + "s"));
      }
    }
    results.sort(
        java.util.Comparator.comparing(
            ConnectionHealthEntry::connName, String.CASE_INSENSITIVE_ORDER));
    return List.copyOf(results);
  }

  private static ConnectionHealthEntry probe(String connName, ConnectionProfile profile) {
    try {
      var connector = ConnectorRegistry.findConnector(profile);
      HealthStatus health = connector.healthCheck(profile);
      return new ConnectionHealthEntry(
          connName,
          profile.host() + ":" + profile.port(),
          profile.type(),
          health.status(),
          health.latencyMs(),
          health.detail());
    } catch (Exception e) {
      return new ConnectionHealthEntry(
          connName,
          profile.host() + ":" + profile.port(),
          profile.type(),
          HealthStatus.Status.DOWN,
          -1L,
          e.getMessage());
    }
  }
}
