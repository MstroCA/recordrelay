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
package io.recordrelay.engine.clone;

import io.recordrelay.core.clone.domain.CloneReport;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Lightweight summary of a completed clone operation, stored in {@link CloneHistoryStore}. */
public record CloneHistorySummary(
    String rootTable,
    String rootId,
    String sourceProfile,
    String targetProfile,
    long totalRecords,
    int tableCount,
    long durationMillis,
    boolean success,
    String errorMessage,
    Instant completedAt) {

  private static final DateTimeFormatter FMT =
      DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

  public String formattedDuration() {
    long s = durationMillis / 1_000;
    return s < 60 ? s + "s" : (s / 60) + "m " + (s % 60) + "s";
  }

  public String formattedTime() {
    return FMT.format(completedAt);
  }

  static CloneHistorySummary ofSuccess(CloneReport report, String source, String target) {
    return new CloneHistorySummary(
        report.rootTable(),
        report.rootId(),
        source,
        target,
        report.totalRecords(),
        report.tableCount(),
        report.durationMillis(),
        true,
        null,
        Instant.now());
  }

  static CloneHistorySummary ofFailure(
      String rootTable,
      String rootId,
      String source,
      String target,
      long durationMs,
      String error) {
    return new CloneHistorySummary(
        rootTable, rootId, source, target, 0, 0, durationMs, false, error, Instant.now());
  }
}
