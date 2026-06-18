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
package io.recordrelay.core.clone.port.in;

import io.recordrelay.core.clone.domain.DiffReport;
import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.domain.ConnectionProfile;

/**
 * Driving port: compares the state of a business entity context across two environments.
 *
 * <p>A diff answers: "is customer-123 in staging an accurate reproduction of customer-123 in
 * production?" It surfaces missing records, changed field values, and unexpected additions so that
 * engineers can decide whether to re-clone.
 *
 * <p>CLI usage:
 *
 * <pre>
 * rr diff --customer-id 123 --left prod --right local
 * rr diff --order-id 987 --left staging --right local --depth 5
 * </pre>
 */
public interface DiffUseCase {

  /**
   * Compares the full business context for the given entity ID across two environments.
   *
   * @param entityName logical entity name, e.g. "customer" or "order"
   * @param entityId primary key value in the source (left) environment
   * @param left connection profile for the reference (left / source) environment
   * @param right connection profile for the comparison (right / target) environment
   * @param depth relationship traversal depth (same as for clone)
   * @return a report describing all discrepancies found
   * @throws CloneException if either environment cannot be read
   */
  DiffReport diff(
      String entityName,
      String entityId,
      ConnectionProfile left,
      ConnectionProfile right,
      int depth)
      throws CloneException;
}
