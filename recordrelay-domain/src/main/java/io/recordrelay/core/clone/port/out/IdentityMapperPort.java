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
package io.recordrelay.core.clone.port.out;

import io.recordrelay.core.clone.domain.ConflictResolution;
import io.recordrelay.core.clone.domain.IdentityMapping;
import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import java.util.List;
import java.util.Map;

/** Allocates new primary key identities for records being written to the target. */
public interface IdentityMapperPort {

  /** Allocates new primary key values in the target and returns the old-to-new identity mapping. */
  IdentityMapping allocate(
      ConnectionProfile target,
      String rootTable,
      Map<String, List<DataRecord>> records,
      ConflictResolution resolution)
      throws CloneException;

  /**
   * Allocates new primary key values, honouring {@code identityStart} for {@link
   * ConflictResolution#START_AT}. The default implementation ignores the start value and delegates
   * to {@link #allocate(ConnectionProfile, String, Map, ConflictResolution)}.
   *
   * @param identityStart starting value for {@code START_AT}; {@code null} means "not specified"
   */
  default IdentityMapping allocate(
      ConnectionProfile target,
      String rootTable,
      Map<String, List<DataRecord>> records,
      ConflictResolution resolution,
      Long identityStart)
      throws CloneException {
    return allocate(target, rootTable, records, resolution);
  }
}
