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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * A named clone configuration saved by the user — persisted at {@code ~/.recordrelay/presets.json}.
 *
 * <p>A preset captures everything needed to re-run a clone: entity, ID, source, target, depth,
 * masking flag, and optional field overrides. Unlike {@link ScheduledSync} it has no schedule; it
 * is always triggered manually.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClonePreset {

  private String name;
  private String entityName;
  private String entityId;
  private String sourceConn;
  private String targetConn;
  private int depth = 3;
  private boolean maskPii = false;

  /** Optional description shown in the UI. */
  private String description;

  /** Field override strings in {@code column=value} or {@code table:column=value} format. */
  private List<String> fieldOverrides;

  public ClonePreset() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEntityName() {
    return entityName;
  }

  public void setEntityName(String entityName) {
    this.entityName = entityName;
  }

  public String getEntityId() {
    return entityId;
  }

  public void setEntityId(String entityId) {
    this.entityId = entityId;
  }

  public String getSourceConn() {
    return sourceConn;
  }

  public void setSourceConn(String sourceConn) {
    this.sourceConn = sourceConn;
  }

  public String getTargetConn() {
    return targetConn;
  }

  public void setTargetConn(String targetConn) {
    this.targetConn = targetConn;
  }

  public int getDepth() {
    return depth;
  }

  public void setDepth(int depth) {
    this.depth = depth;
  }

  public boolean isMaskPii() {
    return maskPii;
  }

  public void setMaskPii(boolean maskPii) {
    this.maskPii = maskPii;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public List<String> getFieldOverrides() {
    return fieldOverrides;
  }

  public void setFieldOverrides(List<String> fieldOverrides) {
    this.fieldOverrides = fieldOverrides;
  }
}
