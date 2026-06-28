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
package io.recordrelay.synthetic;

import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.DataRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a list of realistic {@link DataRecord}s from a table schema.
 *
 * <pre>{@code
 * List<ColumnMeta> columns = inspector.listColumns(tableRef);
 * List<DataRecord> rows = new SyntheticDataGenerator(columns)
 *     .seed(42)
 *     .generate(500);
 * }</pre>
 */
public final class SyntheticDataGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(SyntheticDataGenerator.class);

  private final List<ColumnMeta> columns;
  private long seed = System.currentTimeMillis();
  private int startId = 1;

  /** Creates a generator for the given column schema. */
  public SyntheticDataGenerator(List<ColumnMeta> columns) {
    if (columns == null || columns.isEmpty()) {
      throw new IllegalArgumentException("columns must not be null or empty");
    }
    this.columns = List.copyOf(columns);
  }

  /** Sets the RNG seed for reproducible generation. */
  public SyntheticDataGenerator seed(long seed) {
    this.seed = seed;
    return this;
  }

  /** Sets the starting value for auto-incremented primary-key columns. */
  public SyntheticDataGenerator startId(int startId) {
    this.startId = startId;
    return this;
  }

  /**
   * Generates {@code count} synthetic rows.
   *
   * @param count number of rows to produce
   * @return immutable list of DataRecords
   */
  public List<DataRecord> generate(int count) {
    if (count <= 0) throw new IllegalArgumentException("count must be > 0");

    var rng = new Random(seed);
    var genMap = new LinkedHashMap<String, ColumnValueGenerator>();
    for (var col : columns) {
      genMap.put(col.name(), Generators.forColumn(col, startId));
    }

    var result = new ArrayList<DataRecord>(count);
    for (int row = 0; row < count; row++) {
      var fields = new LinkedHashMap<String, Object>();
      for (var entry : genMap.entrySet()) {
        fields.put(entry.getKey(), entry.getValue().generate(row, rng));
      }
      result.add(DataRecord.of(fields));
    }
    LOG.debug("Generated {} synthetic rows for {} columns", count, columns.size());
    return List.copyOf(result);
  }
}
