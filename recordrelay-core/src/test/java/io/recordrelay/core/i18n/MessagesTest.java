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
package io.recordrelay.core.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MessagesTest {

  @AfterEach
  void resetLocale() {
    Messages.setLocale(Locale.ENGLISH);
  }

  @Test
  void getReturnsValueForKnownKey() {
    Messages.setLocale(Locale.ENGLISH);
    assertThat(Messages.get("theme.dark")).isEqualTo("Dark");
  }

  @Test
  void getReturnsSentinelForMissingKey() {
    assertThat(Messages.get("no.such.key")).isEqualTo("!no.such.key!");
  }

  @Test
  void getWithArgsFormatsMessage() {
    Messages.setLocale(Locale.ENGLISH);
    String result = Messages.get("clone.error", "timeout");
    assertThat(result).contains("timeout");
  }

  @Test
  void getWithArgsMissingKeyReturnsSentinel() {
    assertThat(Messages.get("missing.key", "arg")).isEqualTo("!missing.key!");
  }

  @Test
  void setLocaleChangesBundle() {
    Messages.setLocale(Locale.ENGLISH);
    String english = Messages.get("theme.dark");

    Messages.setLocale(Locale.of("tr"));
    String turkish = Messages.get("theme.dark");

    assertThat(english).isNotEqualTo(turkish);
  }

  @Test
  void getBundleReturnsSameBundleOnRepeatedCalls() {
    Messages.setLocale(Locale.ENGLISH);
    var b1 = Messages.getBundle();
    var b2 = Messages.getBundle();
    assertThat(b1).isSameAs(b2);
  }

  @Test
  void setLocaleAllowsReRead() {
    Messages.setLocale(Locale.ENGLISH);
    String v1 = Messages.get("theme.dark");
    Messages.setLocale(Locale.ENGLISH);
    String v2 = Messages.get("theme.dark");
    assertThat(v1).isEqualTo(v2);
  }

  @Test
  void unknownLocaleDefaultsToEnglish() {
    Messages.setLocale(Locale.of("xx"));
    assertThat(Messages.get("theme.dark")).isEqualTo("Dark");
  }
}
