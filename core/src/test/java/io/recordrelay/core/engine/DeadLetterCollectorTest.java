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
package io.recordrelay.core.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.recordrelay.core.domain.DataRecord;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeadLetterCollectorTest {

  @Test
  void newCollectorHasNoEntries() {
    var collector = new DeadLetterCollector(null);
    assertThat(collector.size()).isEqualTo(0);
    assertThat(collector.hasEntries()).isFalse();
    assertThat(collector.getRecords()).isEmpty();
  }

  @Test
  void collectAddsRecord() {
    var collector = new DeadLetterCollector(null);
    var record = DataRecord.of(Map.of("id", 1));

    collector.collect(record, "error msg", "orders", 1L);

    assertThat(collector.size()).isEqualTo(1);
    assertThat(collector.hasEntries()).isTrue();
    var records = collector.getRecords();
    assertThat(records).hasSize(1);
    assertThat(records.get(0).errorMessage()).isEqualTo("error msg");
    assertThat(records.get(0).tableName()).isEqualTo("orders");
    assertThat(records.get(0).rowNumber()).isEqualTo(1L);
    assertThat(records.get(0).originalRecord()).isEqualTo(record);
  }

  @Test
  void multipleCollectsAreAccumulated() {
    var collector = new DeadLetterCollector("/tmp/dead.csv");
    var r1 = DataRecord.of(Map.of("a", 1));
    var r2 = DataRecord.of(Map.of("b", 2));

    collector.collect(r1, "err1", "t1", 0L);
    collector.collect(r2, "err2", "t2", 1L);

    assertThat(collector.size()).isEqualTo(2);
  }

  @Test
  void deadLetterPathIsRetained() {
    assertThat(new DeadLetterCollector(null).deadLetterPath()).isNull();
    assertThat(new DeadLetterCollector("/path/dl.csv").deadLetterPath()).isEqualTo("/path/dl.csv");
  }

  @Test
  void getRecordsIsUnmodifiable() {
    var collector = new DeadLetterCollector(null);
    collector.collect(DataRecord.of(Map.of("id", 1)), "e", "t", 0L);
    var records = collector.getRecords();
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> records.add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
