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

/**
 * A named scheduled sync configuration — persisted at {@code ~/.recordrelay/syncs.json}.
 *
 * <p>Each entry describes a recurring clone: which entity to pull, from/to which connections, and
 * how often. {@code lastRunAt} and {@code lastStatus} are updated after each run.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduledSync {

  private String name;
  private String entityName;
  private String entityId;
  private String sourceConn;
  private String targetConn;
  private int depth = 3;
  private boolean maskPii = false;

  /** Optional cron expression (5-field, e.g. {@code "0 8 * * *"} for daily at 08:00). */
  private String cronExpression;

  /** Alternative to cron — run every N minutes. */
  private Integer intervalMinutes;

  /** ISO-8601 timestamp of the last successful run (null if never run). */
  private String lastRunAt;

  /** {@code OK}, {@code FAILED}, or {@code NEVER}. */
  private String lastStatus = "NEVER";

  /** Optional webhook URL to POST a notification after each run. */
  private String webhookUrl;

  public ScheduledSync() {}

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

  public String getCronExpression() {
    return cronExpression;
  }

  public void setCronExpression(String cronExpression) {
    this.cronExpression = cronExpression;
  }

  public Integer getIntervalMinutes() {
    return intervalMinutes;
  }

  public void setIntervalMinutes(Integer intervalMinutes) {
    this.intervalMinutes = intervalMinutes;
  }

  public String getLastRunAt() {
    return lastRunAt;
  }

  public void setLastRunAt(String lastRunAt) {
    this.lastRunAt = lastRunAt;
  }

  public String getLastStatus() {
    return lastStatus;
  }

  public void setLastStatus(String lastStatus) {
    this.lastStatus = lastStatus;
  }

  public String getWebhookUrl() {
    return webhookUrl;
  }

  public void setWebhookUrl(String webhookUrl) {
    this.webhookUrl = webhookUrl;
  }

  /** Returns a human-readable schedule description (cron or interval). */
  public String scheduleLabel() {
    if (cronExpression != null && !cronExpression.isBlank()) {
      return "cron: " + cronExpression;
    }
    if (intervalMinutes != null) {
      return "every " + intervalMinutes + "m";
    }
    return "manual";
  }
}
