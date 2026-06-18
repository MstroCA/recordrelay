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
package io.recordrelay.core.domain;

/**
 * JDBC transaction isolation levels for SQL source connections.
 *
 * <p>The integer constants match {@link java.sql.Connection} constants; they are inlined here to
 * avoid exposing a JDBC dependency in the core domain.
 */
public enum TransactionIsolation {
  /** Dirty reads, non-repeatable reads, and phantom reads are allowed. */
  READ_UNCOMMITTED(1),
  /** Dirty reads are prevented; non-repeatable reads and phantom reads may occur. */
  READ_COMMITTED(2),
  /** Dirty and non-repeatable reads are prevented; phantom reads may occur. */
  REPEATABLE_READ(4),
  /** All three anomalies are prevented; highest isolation, lowest throughput. */
  SERIALIZABLE(8);

  private final int jdbcConstant;

  TransactionIsolation(int jdbcConstant) {
    this.jdbcConstant = jdbcConstant;
  }

  /** Returns the numeric value matching the corresponding {@link java.sql.Connection} constant. */
  public int jdbcConstant() {
    return jdbcConstant;
  }
}
