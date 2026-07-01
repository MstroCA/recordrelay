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
package io.recordrelay.engine.clone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.recordrelay.core.clone.domain.ConflictResolution;
import io.recordrelay.core.clone.domain.FieldOverrideConfig;
import io.recordrelay.core.clone.domain.IdentityMapping;
import io.recordrelay.core.clone.domain.SatelliteTable;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.core.clone.port.out.IdentityMapperPort;
import io.recordrelay.core.clone.port.out.RecordFetcherPort;
import io.recordrelay.core.clone.port.out.SequenceSyncPort;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.Credentials;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.DatabaseType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JdbcSatelliteSyncerTest {

  private final RecordFetcherPort fetcher = org.mockito.Mockito.mock(RecordFetcherPort.class);
  private final IdentityMapperPort identityMapper =
      org.mockito.Mockito.mock(IdentityMapperPort.class);
  private final SequenceSyncPort sequenceSyncer = org.mockito.Mockito.mock(SequenceSyncPort.class);

  private final JdbcSatelliteSyncer syncer =
      new JdbcSatelliteSyncer(fetcher, identityMapper, sequenceSyncer);

  private static ConnectionProfile profile(String id) {
    return new ConnectionProfile(
        id,
        "test",
        "env-1",
        DatabaseType.POSTGRESQL,
        "localhost",
        5432,
        "db",
        new Credentials("user", "pass"),
        Map.of());
  }

  private static DataRecord row(long id, long beyannameId, String vkn) {
    var fields = new LinkedHashMap<String, Object>();
    fields.put("id", id);
    fields.put("beyanname_id", beyannameId);
    fields.put("mukellef_vkn", vkn);
    return new DataRecord(fields);
  }

  private SatelliteTable satellite() {
    return SatelliteTable.of(profile("src"), profile("tgt"), "read_model", "beyanname_id");
  }

  private IdentityMapping rootMapping() {
    var b = IdentityMapping.builder();
    b.register("beyanname", "12", "5001");
    return b.build();
  }

  @Test
  void relinksLinkColumnToNewRootIdBeforeAllocating() throws Exception {
    when(fetcher.fetchByForeignKey(any(), eq("read_model"), eq("beyanname_id"), eq("12")))
        .thenReturn(List.of(row(7L, 12L, "1111111111")));
    when(identityMapper.allocate(any(), any(), any(), any())).thenReturn(IdentityMapping.empty());

    syncer.sync(
        "beyanname",
        List.of("12"),
        rootMapping(),
        List.of(satellite()),
        FieldOverrideConfig.none(),
        new CloneProgressListener() {});

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, List<DataRecord>>> captor = ArgumentCaptor.forClass(Map.class);
    verify(identityMapper)
        .allocate(
            any(),
            eq("read_model"),
            captor.capture(),
            eq(ConflictResolution.REGENERATE_IDENTITIES));
    var relinked = captor.getValue().get("read_model");
    assertThat(relinked).hasSize(1);
    // beyanname_id remapped 12 → 5001, preserving the numeric (Long) type
    assertThat(relinked.get(0).get("beyanname_id")).isEqualTo(5001L);
  }

  @Test
  void writeFailureIsRecordedAsWarningWithoutThrowing() throws Exception {
    // No connector is registered on the engine test classpath, so the write fails fast.
    when(fetcher.fetchByForeignKey(any(), any(), any(), any()))
        .thenReturn(List.of(row(7L, 12L, "1111111111")));
    when(identityMapper.allocate(any(), any(), any(), any())).thenReturn(IdentityMapping.empty());

    var result =
        syncer.sync(
            "beyanname",
            List.of("12"),
            rootMapping(),
            List.of(satellite()),
            FieldOverrideConfig.none(),
            new CloneProgressListener() {});

    assertThat(result.summaries()).isEmpty();
    assertThat(result.warnings()).isNotEmpty();
  }

  @Test
  void noSourceRowsProducesWarningAndSkipsAllocation() throws Exception {
    when(fetcher.fetchByForeignKey(any(), any(), any(), any())).thenReturn(List.of());

    var result =
        syncer.sync(
            "beyanname",
            List.of("12"),
            rootMapping(),
            List.of(satellite()),
            FieldOverrideConfig.none(),
            new CloneProgressListener() {});

    assertThat(result.summaries()).isEmpty();
    assertThat(result.warnings()).anyMatch(w -> w.contains("no source rows"));
    verify(identityMapper, never()).allocate(any(), any(), any(), any());
  }

  @Test
  void emptyRootIdsReturnsEmptyResult() throws Exception {
    var result =
        syncer.sync(
            "beyanname",
            List.of(),
            rootMapping(),
            List.of(satellite()),
            FieldOverrideConfig.none(),
            new CloneProgressListener() {});
    assertThat(result.summaries()).isEmpty();
    assertThat(result.warnings()).isEmpty();
    verify(fetcher, never()).fetchByForeignKey(any(), any(), any(), any());
  }
}
