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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256/GCM credential encryption for the CLI config store.
 *
 * <p>The symmetric key is generated on first use and stored at {@code ~/.recordrelay/.key} with
 * owner-only read permissions. Encrypted values use the format {@code ENC(AES256:base64data)} where
 * {@code base64data} encodes the 12-byte IV followed by the ciphertext.
 */
public final class CredentialEncryptor {

  static final String PREFIX = "ENC(AES256:";
  static final String SUFFIX = ")";
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int IV_LENGTH = 12;
  private static final int TAG_LENGTH_BITS = 128;
  private static final int KEY_SIZE_BITS = 256;

  private final SecretKey key;

  /** Creates an encryptor, loading the key from {@code keyFile} or generating a new one. */
  public CredentialEncryptor(Path keyFile) throws Exception {
    this.key = loadOrCreateKey(keyFile);
  }

  /** For testing only — injects a pre-built key. */
  CredentialEncryptor(SecretKey key) {
    this.key = key;
  }

  /**
   * Encrypts {@code plaintext} and returns the {@code ENC(AES256:...)} token.
   *
   * <p>Never log or print the input to this method.
   */
  public String encrypt(String plaintext) throws Exception {
    var iv = new byte[IV_LENGTH];
    new SecureRandom().nextBytes(iv);
    var cipher = Cipher.getInstance(ALGORITHM);
    cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
    var ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
    var combined = combine(iv, ciphertext);
    return PREFIX + Base64.getEncoder().encodeToString(combined) + SUFFIX;
  }

  /** Decrypts an {@code ENC(AES256:...)} token; returns the value unchanged if not encrypted. */
  public String decrypt(String encrypted) throws Exception {
    if (!isEncrypted(encrypted)) {
      return encrypted;
    }
    var b64 = encrypted.substring(PREFIX.length(), encrypted.length() - SUFFIX.length());
    var combined = Base64.getDecoder().decode(b64);
    var iv = new byte[IV_LENGTH];
    System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
    var ciphertext = new byte[combined.length - IV_LENGTH];
    System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);
    var cipher = Cipher.getInstance(ALGORITHM);
    cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
    return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
  }

  /** Returns {@code true} when {@code value} looks like an encrypted token. */
  public static boolean isEncrypted(String value) {
    return value != null && value.startsWith(PREFIX) && value.endsWith(SUFFIX);
  }

  private static byte[] combine(byte[] iv, byte[] ciphertext) {
    var combined = new byte[iv.length + ciphertext.length];
    System.arraycopy(iv, 0, combined, 0, iv.length);
    System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
    return combined;
  }

  private static SecretKey loadOrCreateKey(Path keyFile) throws Exception {
    if (Files.exists(keyFile)) {
      var b64 = Files.readString(keyFile).strip();
      return new SecretKeySpec(Base64.getDecoder().decode(b64), "AES");
    }
    return generateAndSaveKey(keyFile);
  }

  private static SecretKey generateAndSaveKey(Path keyFile) throws Exception {
    var gen = KeyGenerator.getInstance("AES");
    gen.init(KEY_SIZE_BITS, new SecureRandom());
    var newKey = gen.generateKey();
    Files.createDirectories(keyFile.getParent());
    Files.writeString(keyFile, Base64.getEncoder().encodeToString(newKey.getEncoded()));
    var f = keyFile.toFile();
    f.setReadable(false, false);
    f.setReadable(true, true);
    f.setWritable(false, false);
    f.setWritable(true, true);
    return newKey;
  }
}
