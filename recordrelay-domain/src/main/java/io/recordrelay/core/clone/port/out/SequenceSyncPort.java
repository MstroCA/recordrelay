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

import io.recordrelay.core.clone.domain.IdentityMapping;
import io.recordrelay.core.domain.ConnectionProfile;

/** Advances database sequences past the highest allocated identity value. */
public interface SequenceSyncPort {

  /**
   * Advances sequences in {@code target} so they are above the highest value in {@code mapping}.
   */
  void synchronize(ConnectionProfile target, IdentityMapping mapping);
}
