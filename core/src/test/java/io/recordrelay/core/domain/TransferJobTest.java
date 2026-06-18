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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TransferJobTest {

  private static ConnectionProfile profile(String id, DatabaseType type) {
    return new ConnectionProfile(
        id,
        "profile-" + id,
        "env-1",
        type,
        "localhost",
        5432,
        "db",
        new Credentials("user", "pass"),
        Map.of());
  }

  private static MappingDefinition mapping() {
    var src = new TableRef(new DatabaseRef("srcDb", DatabaseType.POSTGRESQL), "public", "users");
    var tgt = new TableRef(new DatabaseRef("tgtDb", DatabaseType.MONGODB), "", "users");
    return new MappingDefinition("map-1", src, tgt, null, null, null);
  }

  @Test
  void shouldApplyDefaultBatchSizeWhenZero() {
    var job =
        new TransferJob(
            "j1",
            "job",
            profile("src", DatabaseType.POSTGRESQL),
            profile("tgt", DatabaseType.MONGODB),
            mapping(),
            null,
            0,
            null,
            null);
    assertThat(job.batchSize()).isEqualTo(TransferJob.DEFAULT_BATCH_SIZE);
  }

  @Test
  void shouldDefaultModeToSync() {
    var job =
        new TransferJob(
            "j1",
            "job",
            profile("src", DatabaseType.POSTGRESQL),
            profile("tgt", DatabaseType.MONGODB),
            mapping(),
            null,
            500,
            null,
            null);
    assertThat(job.mode()).isEqualTo(TransferMode.SYNC);
  }

  @Test
  void shouldDefaultIsolationToReadCommitted() {
    var job =
        new TransferJob(
            "j1",
            "job",
            profile("src", DatabaseType.POSTGRESQL),
            profile("tgt", DatabaseType.MONGODB),
            mapping(),
            TransferMode.ASYNC,
            500,
            null,
            null);
    assertThat(job.isolation()).isEqualTo(TransactionIsolation.READ_COMMITTED);
  }

  @Test
  void shouldRejectNullId() {
    assertThatThrownBy(
            () ->
                new TransferJob(
                    null,
                    "job",
                    profile("src", DatabaseType.POSTGRESQL),
                    profile("tgt", DatabaseType.MONGODB),
                    mapping(),
                    null,
                    0,
                    null,
                    null))
        .isInstanceOf(NullPointerException.class);
  }
}
