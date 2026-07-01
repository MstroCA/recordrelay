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
package io.recordrelay.core.clone.domain;

import java.util.List;

/**
 * Collection of {@link SatelliteTable} definitions synchronised after the primary clone completes.
 *
 * <p>Satellites let a clone reach beyond the primary database into companion databases whose rows
 * are normally produced asynchronously (e.g. via CDC or a message bus). See {@link SatelliteTable}
 * for the full rationale.
 */
public record SatelliteConfig(List<SatelliteTable> satellites) {

  public SatelliteConfig {
    satellites = satellites == null ? List.of() : List.copyOf(satellites);
  }

  /** Returns a config with no satellites. */
  public static SatelliteConfig none() {
    return new SatelliteConfig(List.of());
  }

  /** Returns true when no satellites are configured. */
  public boolean isEmpty() {
    return satellites.isEmpty();
  }
}
