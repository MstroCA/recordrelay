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

import io.recordrelay.core.domain.ConnectionProfile;
import java.nio.file.Path;
import java.util.Objects;

/**
 * High-level reproduction plan expressed in business terms.
 *
 * <p>A {@code ContextClonePlan} says "clone customer 123 from production to local". The engine
 * resolves this to the correct table, ID column, depth, and masking configuration — without the
 * caller needing to know any database internals.
 *
 * <p>Unlike {@link CloneRequest} which is table-centric, {@code ContextClonePlan} is
 * entity-centric. The resolver converts one to the other.
 *
 * <p>Optionally carries a {@link BugReport} when the context is being captured for reproduction.
 */
public record ContextClonePlan(
    BusinessEntity entity,
    String entityId,
    ConnectionProfile source,
    ConnectionProfile target,
    int depth,
    MaskingConfig masking,
    Path outputDirectory,
    BugReport bugReport,
    FieldOverrideConfig fieldOverrides) {

  public ContextClonePlan {
    Objects.requireNonNull(entity, "entity");
    Objects.requireNonNull(entityId, "entityId");
    Objects.requireNonNull(source, "source");
    if (entityId.isBlank()) {
      throw new IllegalArgumentException("entityId must not be blank");
    }
    if (depth < 1 || depth > CloneRequest.MAX_DEPTH) {
      throw new IllegalArgumentException("depth must be 1–" + CloneRequest.MAX_DEPTH);
    }
    masking = masking == null ? MaskingConfig.none() : masking;
    fieldOverrides = fieldOverrides == null ? FieldOverrideConfig.none() : fieldOverrides;
  }

  /** Creates a simple live-clone plan (source → target, no package export). */
  public static ContextClonePlan liveClone(
      BusinessEntity entity,
      String entityId,
      ConnectionProfile source,
      ConnectionProfile target,
      int depth,
      MaskingConfig masking) {
    return new ContextClonePlan(
        entity, entityId, source, target, depth, masking, null, null, FieldOverrideConfig.none());
  }

  /** Creates a live-clone plan with field overrides applied in the target. */
  public static ContextClonePlan liveCloneWithOverrides(
      BusinessEntity entity,
      String entityId,
      ConnectionProfile source,
      ConnectionProfile target,
      int depth,
      MaskingConfig masking,
      FieldOverrideConfig fieldOverrides) {
    return new ContextClonePlan(
        entity, entityId, source, target, depth, masking, null, null, fieldOverrides);
  }

  /** Creates a bug-reproduction export plan (source → .rrpkg file, no live target). */
  public static ContextClonePlan bugCapture(
      BusinessEntity entity,
      String entityId,
      ConnectionProfile source,
      Path outputDirectory,
      MaskingConfig masking,
      BugReport bugReport) {
    return new ContextClonePlan(
        entity,
        entityId,
        source,
        null,
        CloneRequest.DEFAULT_DEPTH,
        masking,
        outputDirectory,
        bugReport,
        FieldOverrideConfig.none());
  }

  /** Returns true when this plan targets a live database (not just an export). */
  public boolean isLiveClone() {
    return target != null;
  }

  /** Returns true when this plan carries bug reproduction context. */
  public boolean isBugCapture() {
    return bugReport != null;
  }
}
