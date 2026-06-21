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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process history of clone operations for the current session.
 *
 * <p>Capped at 200 entries (oldest entries are evicted). Thread-safe. Entries survive for the JVM
 * lifetime — they are not persisted to disk. The Desktop and Plugin UIs read this store to populate
 * their Monitor/Dashboard screens.
 */
public final class CloneHistoryStore {

  private static final int MAX_ENTRIES = 200;
  private static final CloneHistoryStore INSTANCE = new CloneHistoryStore();

  private final Deque<CloneHistorySummary> entries = new ArrayDeque<>(MAX_ENTRIES + 1);
  private final AtomicLong sessionSuccessCount = new AtomicLong();
  private final AtomicLong sessionFailureCount = new AtomicLong();

  private CloneHistoryStore() {}

  public static CloneHistoryStore getInstance() {
    return INSTANCE;
  }

  /** Records a completed clone summary and updates session counters. */
  public synchronized void record(CloneHistorySummary summary) {
    entries.addFirst(summary);
    while (entries.size() > MAX_ENTRIES) {
      entries.removeLast();
    }
    if (summary.success()) {
      sessionSuccessCount.incrementAndGet();
    } else {
      sessionFailureCount.incrementAndGet();
    }
  }

  /** Returns up to {@code limit} most-recent entries, newest first. */
  public synchronized List<CloneHistorySummary> recent(int limit) {
    return entries.stream().limit(limit).toList();
  }

  /** Total successful clone operations in this session. */
  public long sessionTransferredTotal() {
    return sessionSuccessCount.get();
  }

  /** Total failed clone operations in this session. */
  public long sessionFailedTotal() {
    return sessionFailureCount.get();
  }

  /** Total operations (success + failure) in this session. */
  public long operationCount() {
    return sessionSuccessCount.get() + sessionFailureCount.get();
  }

  /** Clears all history and resets session counters. */
  public synchronized void clear() {
    entries.clear();
    sessionSuccessCount.set(0);
    sessionFailureCount.set(0);
  }
}
