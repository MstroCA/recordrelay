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
package io.recordrelay.core.port.in;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.MigrationDriftReport;

/**
 * Driving port: full database-level migration drift analysis.
 *
 * <p>Compares every table and column visible in the source database against the target database and
 * returns a flat report of all discrepancies — missing tables, extra tables, missing columns, extra
 * columns, and type mismatches.
 */
public interface MigrationDriftUseCase {

  /**
   * Analyses structural drift between two databases.
   *
   * @param sourceProfile connection parameters for the reference (source) database server
   * @param sourceDatabase the source database to inspect
   * @param targetProfile connection parameters for the target database server
   * @param targetDatabase the target database to inspect
   * @return report containing every discrepancy found, or an empty report when fully in sync
   */
  MigrationDriftReport analyzeDrift(
      ConnectionProfile sourceProfile,
      DatabaseRef sourceDatabase,
      ConnectionProfile targetProfile,
      DatabaseRef targetDatabase);
}
