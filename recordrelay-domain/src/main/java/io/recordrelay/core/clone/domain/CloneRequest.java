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
import java.time.Instant;
import java.util.Objects;

/**
 * Specifies what to clone, from where, to where, and how.
 *
 * <p>The clone engine starts at the root record ({@code rootTable} / {@code rootId}), traverses the
 * relationship graph up to {@code depth} hops, and imports all discovered records into the target.
 */
public record CloneRequest(
    ConnectionProfile source,
    ConnectionProfile target,
    String rootTable,
    String rootId,
    int depth,
    MaskingConfig masking,
    ConflictResolution conflictResolution,
    FieldOverrideConfig fieldOverrides,
    Instant asOf,
    SatelliteConfig satellites,
    Long identityStart) {

  /** Default traversal depth when none is specified. */
  public static final int DEFAULT_DEPTH = 3;

  /** Maximum permitted traversal depth. */
  public static final int MAX_DEPTH = 10;

  /** Validates fields and applies defaults. */
  public CloneRequest {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(rootTable, "rootTable");
    Objects.requireNonNull(rootId, "rootId");
    if (rootTable.isBlank()) {
      throw new IllegalArgumentException("rootTable must not be blank");
    }
    if (rootId.isBlank()) {
      throw new IllegalArgumentException("rootId must not be blank");
    }
    if (depth < 1 || depth > MAX_DEPTH) {
      throw new IllegalArgumentException(
          "depth must be between 1 and " + MAX_DEPTH + ", got: " + depth);
    }
    masking = masking == null ? MaskingConfig.none() : masking;
    conflictResolution =
        conflictResolution == null ? ConflictResolution.REGENERATE_IDENTITIES : conflictResolution;
    fieldOverrides = fieldOverrides == null ? FieldOverrideConfig.none() : fieldOverrides;
    satellites = satellites == null ? SatelliteConfig.none() : satellites;
  }

  /** Returns a builder pre-populated with required fields. */
  public static Builder builder(
      ConnectionProfile source, ConnectionProfile target, String rootTable, String rootId) {
    return new Builder(source, target, rootTable, rootId);
  }

  /** Fluent builder for {@link CloneRequest}. */
  public static final class Builder {
    private final ConnectionProfile source;
    private final ConnectionProfile target;
    private final String rootTable;
    private final String rootId;
    private int depth = DEFAULT_DEPTH;
    private MaskingConfig masking = MaskingConfig.none();
    private ConflictResolution conflictResolution = ConflictResolution.REGENERATE_IDENTITIES;
    private FieldOverrideConfig fieldOverrides = FieldOverrideConfig.none();
    private Instant asOf = null;
    private SatelliteConfig satellites = SatelliteConfig.none();
    private Long identityStart = null;

    private Builder(
        ConnectionProfile source, ConnectionProfile target, String rootTable, String rootId) {
      this.source = source;
      this.target = target;
      this.rootTable = rootTable;
      this.rootId = rootId;
    }

    /**
     * Sets the traversal depth (1–{@link CloneRequest#MAX_DEPTH}).
     *
     * @param depth number of relationship hops to follow
     * @return this builder
     */
    public Builder depth(int depth) {
      this.depth = depth;
      return this;
    }

    /**
     * Sets the masking configuration to apply during the clone.
     *
     * @param masking masking rules; {@code null} is treated as {@link MaskingConfig#none()}
     * @return this builder
     */
    public Builder masking(MaskingConfig masking) {
      this.masking = masking;
      return this;
    }

    /**
     * Sets the conflict resolution strategy (default: {@link
     * ConflictResolution#REGENERATE_IDENTITIES}).
     *
     * @param conflictResolution strategy to use when target already has data
     * @return this builder
     */
    public Builder conflictResolution(ConflictResolution conflictResolution) {
      this.conflictResolution = conflictResolution;
      return this;
    }

    /**
     * Sets the field overrides to apply when writing records to the target.
     *
     * @param fieldOverrides override config; {@code null} is treated as {@link
     *     FieldOverrideConfig#none()}
     * @return this builder
     */
    public Builder fieldOverrides(FieldOverrideConfig fieldOverrides) {
      this.fieldOverrides = fieldOverrides;
      return this;
    }

    /**
     * Sets a point-in-time snapshot timestamp. When set, the engine attempts to retrieve the root
     * record as it existed at this instant (via an audit table), falling back to the current record
     * if no audit trail is available.
     *
     * @param asOf the target timestamp; {@code null} means current state
     * @return this builder
     */
    public Builder asOf(Instant asOf) {
      this.asOf = asOf;
      return this;
    }

    /**
     * Sets the companion (satellite) tables to synchronise after the primary clone.
     *
     * @param satellites satellite config; {@code null} is treated as {@link SatelliteConfig#none()}
     * @return this builder
     */
    public Builder satellites(SatelliteConfig satellites) {
      this.satellites = satellites;
      return this;
    }

    /**
     * Sets the starting primary-key value for {@link ConflictResolution#START_AT}.
     *
     * @param identityStart starting value; {@code null} means "not specified"
     * @return this builder
     */
    public Builder identityStart(Long identityStart) {
      this.identityStart = identityStart;
      return this;
    }

    /**
     * Builds and returns an immutable {@link CloneRequest}.
     *
     * @return the constructed request
     */
    public CloneRequest build() {
      return new CloneRequest(
          source,
          target,
          rootTable,
          rootId,
          depth,
          masking,
          conflictResolution,
          fieldOverrides,
          asOf,
          satellites,
          identityStart);
    }
  }
}
