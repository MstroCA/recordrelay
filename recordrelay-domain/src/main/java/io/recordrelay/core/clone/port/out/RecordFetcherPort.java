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

import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import java.util.List;
import java.util.Optional;

/** Fetches records from a source database during the extraction phase. */
public interface RecordFetcherPort {

  Optional<DataRecord> fetchById(
      ConnectionProfile source, String tableName, String idColumn, String idValue)
      throws CloneException;

  List<DataRecord> fetchByForeignKey(
      ConnectionProfile source, String tableName, String fkColumn, String fkValue)
      throws CloneException;
}
