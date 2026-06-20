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

import java.time.Instant;
import java.util.Objects;

/**
 * Reproduction context embedded inside a {@code .rrpkg} bug reproduction package.
 *
 * <p>When an engineer exports a package to reproduce a bug, they can attach this metadata so that
 * anyone who later imports the package understands exactly what issue it captures.
 *
 * <p>Stored in {@code metadata.json} under the {@code bugReport} key.
 */
public record BugReport(
    String id,
    String title,
    String service,
    String environment,
    Instant capturedAt,
    String stepsToReproduce) {

  public BugReport {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(title, "title");
    if (title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    if (capturedAt == null) {
      capturedAt = Instant.now();
    }
  }

  /** Creates a minimal bug report with only required fields. */
  public static BugReport of(String id, String title) {
    return new BugReport(id, title, null, null, Instant.now(), null);
  }

  /** Creates a full bug report. */
  public static BugReport of(
      String id, String title, String service, String environment, String stepsToReproduce) {
    return new BugReport(id, title, service, environment, Instant.now(), stepsToReproduce);
  }
}
