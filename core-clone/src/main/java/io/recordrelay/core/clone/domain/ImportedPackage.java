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
package io.recordrelay.core.clone.domain;

import io.recordrelay.core.domain.DataRecord;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The in-memory representation of a deserialized {@code .rrpkg} archive.
 *
 * <p>Returned by {@link io.recordrelay.core.clone.port.out.PackageImporterPort} after reading a
 * package file.
 */
public record ImportedPackage(PackageManifest manifest, Map<String, List<DataRecord>> records) {

  /** Validates required fields and produces an immutable records map. */
  public ImportedPackage {
    Objects.requireNonNull(manifest, "manifest");
    Objects.requireNonNull(records, "records");
    records = Map.copyOf(records);
  }

  /** Returns the total number of records across all tables. */
  public long totalRecords() {
    return records.values().stream().mapToLong(List::size).sum();
  }
}
