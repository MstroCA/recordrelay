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

import io.recordrelay.core.clone.domain.MaskerType;
import io.recordrelay.core.clone.domain.MaskingConfig;
import io.recordrelay.core.clone.domain.MaskingRule;
import io.recordrelay.core.domain.DataRecord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DefaultMaskingServiceTest {

  private final DefaultMaskingService service = new DefaultMaskingService();

  @Test
  void emptyMaskingConfigReturnsOriginalRecord() {
    var record = DataRecord.of(Map.of("email", "john@example.com"));
    var result = service.mask(record, MaskingConfig.none());
    assertThat(result.get("email")).isEqualTo("john@example.com");
  }

  @Test
  void emailMaskerProducesValidSyntheticEmail() {
    var masked = service.maskValue("john@example.com", MaskerType.EMAIL);
    assertThat(masked).startsWith("user_").endsWith("@example.test");
  }

  @Test
  void emailMaskerIsDeterministic() {
    var first = service.maskValue("john@example.com", MaskerType.EMAIL);
    var second = service.maskValue("john@example.com", MaskerType.EMAIL);
    assertThat(first).isEqualTo(second);
  }

  @Test
  void emailMaskerDifferentInputsProduceDifferentOutputs() {
    var a = service.maskValue("alice@example.com", MaskerType.EMAIL);
    var b = service.maskValue("bob@example.com", MaskerType.EMAIL);
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void phoneMaskerPreservesLength() {
    var phone = "555-123-4567";
    var masked = service.maskValue(phone, MaskerType.PHONE);
    assertThat(masked).hasSameSizeAs(phone);
  }

  @Test
  void phoneMaskerIsDeterministic() {
    var first = service.maskValue("+1-800-555-0100", MaskerType.PHONE);
    var second = service.maskValue("+1-800-555-0100", MaskerType.PHONE);
    assertThat(first).isEqualTo(second);
  }

  @Test
  void ibanMaskerPreservesCountryCode() {
    var iban = "DE89370400440532013000";
    var masked = service.maskValue(iban, MaskerType.IBAN);
    assertThat(masked).startsWith("DE");
  }

  @Test
  void ibanMaskerIsDeterministic() {
    var first = service.maskValue("GB82WEST12345698765432", MaskerType.IBAN);
    var second = service.maskValue("GB82WEST12345698765432", MaskerType.IBAN);
    assertThat(first).isEqualTo(second);
  }

  @ParameterizedTest
  @EnumSource(MaskerType.class)
  void allMaskersAreDeterministic(MaskerType type) {
    var value = "test-input-42";
    assertThat(service.maskValue(value, type)).isEqualTo(service.maskValue(value, type));
  }

  @Test
  void maskRecordReplacesMatchingColumnLeavesOthersUnchanged() {
    var record = DataRecord.of(Map.of("email", "alice@example.com", "name", "Alice"));
    var config = new MaskingConfig(List.of(new MaskingRule("email", MaskerType.EMAIL)));

    var result = service.mask(record, config);

    assertThat(result.get("name")).isEqualTo("Alice");
    assertThat(result.get("email")).asString().endsWith("@example.test");
  }

  @Test
  void maskRecordNullValueLeftAsNull() {
    var fields = new HashMap<String, Object>();
    fields.put("email", null);
    var record = new DataRecord(fields);
    var config = new MaskingConfig(List.of(new MaskingRule("email", MaskerType.EMAIL)));

    var result = service.mask(record, config);
    assertThat(result.get("email")).isNull();
  }

  @Test
  void countMaskedFieldsCountsMatchingColumns() {
    var record = DataRecord.of(Map.of("email", "a@b.com", "phone", "555", "name", "Alice"));
    var config =
        new MaskingConfig(
            List.of(
                new MaskingRule("email", MaskerType.EMAIL),
                new MaskingRule("phone", MaskerType.PHONE)));

    assertThat(service.countMaskedFields(record, config)).isEqualTo(2);
  }

  @Test
  void sha256HexIsStable() {
    assertThat(DefaultMaskingService.sha256Hex("hello"))
        .isEqualTo(DefaultMaskingService.sha256Hex("hello"))
        .hasSize(64);
  }
}
