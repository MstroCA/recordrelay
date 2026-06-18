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
package io.recordrelay.cli.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CredentialEncryptorTest {

  private CredentialEncryptor encryptor;

  @BeforeEach
  void setUp() throws Exception {
    var gen = KeyGenerator.getInstance("AES");
    gen.init(256);
    SecretKey key = gen.generateKey();
    encryptor = new CredentialEncryptor(key);
  }

  @Test
  void encryptedValueStartsWithPrefix() throws Exception {
    String token = encryptor.encrypt("s3cr3t");
    assertThat(token).startsWith(CredentialEncryptor.PREFIX);
    assertThat(token).endsWith(CredentialEncryptor.SUFFIX);
  }

  @Test
  void decryptRoundTrip() throws Exception {
    String plain = "my-super-password";
    String token = encryptor.encrypt(plain);
    assertThat(encryptor.decrypt(token)).isEqualTo(plain);
  }

  @Test
  void decryptPlaintextPassesThrough() throws Exception {
    assertThat(encryptor.decrypt("notencrypted")).isEqualTo("notencrypted");
  }

  @Test
  void encryptProducesDifferentTokensEachCall() throws Exception {
    String t1 = encryptor.encrypt("same");
    String t2 = encryptor.encrypt("same");
    assertThat(t1).isNotEqualTo(t2);
  }

  @Test
  void isEncryptedDetectsToken() throws Exception {
    String token = encryptor.encrypt("x");
    assertThat(CredentialEncryptor.isEncrypted(token)).isTrue();
    assertThat(CredentialEncryptor.isEncrypted("plaintext")).isFalse();
    assertThat(CredentialEncryptor.isEncrypted(null)).isFalse();
  }

  @Test
  void emptyStringEncryptDecrypt() throws Exception {
    String token = encryptor.encrypt("");
    assertThat(encryptor.decrypt(token)).isEqualTo("");
  }
}
