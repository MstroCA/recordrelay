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

import io.recordrelay.core.clone.domain.MaskerType;
import io.recordrelay.core.clone.domain.MaskingConfig;
import io.recordrelay.core.clone.domain.MaskingRule;
import io.recordrelay.core.clone.port.out.MaskingServicePort;
import io.recordrelay.core.domain.DataRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Default deterministic masking implementation.
 *
 * <p>All maskers are deterministic: the same input always produces the same output. This preserves
 * referential integrity when the same value appears across multiple related records.
 *
 * <p>Masking is backed by a SHA-256 hash of the raw value, truncated and formatted per masker type.
 */
public final class DefaultMaskingService implements MaskingServicePort {

  @Override
  public DataRecord mask(DataRecord record, MaskingConfig config) {
    if (config.isEmpty()) {
      return record;
    }

    var maskedFields = new LinkedHashMap<String, Object>();
    for (var entry : record.fields().entrySet()) {
      var rule = config.ruleFor(entry.getKey());
      if (rule.isPresent() && entry.getValue() != null) {
        maskedFields.put(entry.getKey(), applyMask(entry.getValue().toString(), rule.get()));
      } else {
        maskedFields.put(entry.getKey(), entry.getValue());
      }
    }
    return new DataRecord(maskedFields);
  }

  private String applyMask(String value, MaskingRule rule) {
    var hash = sha256Hex(value);
    return switch (rule.maskerType()) {
      case EMAIL -> maskEmail(hash);
      case PHONE -> maskPhone(value, hash);
      case ADDRESS -> maskAddress(hash);
      case IBAN -> maskIban(value, hash);
      case NATIONAL_ID -> maskNationalId(value, hash);
        // Non-exhaustive on purpose: an explicit default avoids a java.lang.MatchException
        // reference (absent on IntelliJ 2024.1 / JBR 17, where this engine is bundled).
      default -> maskGeneric(hash);
    };
  }

  private String maskEmail(String hash) {
    return "user_" + hash.substring(0, 5) + "@example.test";
  }

  private String maskPhone(String original, String hash) {
    // Preserve digit count, replace digits deterministically
    var digits = hash.replaceAll("[^0-9]", "").substring(0, Math.min(15, hash.length()));
    var sb = new StringBuilder();
    int digitIdx = 0;
    for (char c : original.toCharArray()) {
      if (Character.isDigit(c)) {
        sb.append(digitIdx < digits.length() ? digits.charAt(digitIdx++) : '0');
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private String maskAddress(String hash) {
    var num = Integer.parseInt(hash.substring(0, 4), 16) % 9999 + 1;
    return num + " Masked St, Anonymized City";
  }

  private String maskIban(String original, String hash) {
    if (original.length() < 2) {
      return "XX" + hash.substring(0, original.length()).toUpperCase(Locale.ROOT);
    }
    // Preserve country code (first 2 chars), mask the rest
    var country = original.substring(0, 2).toUpperCase(Locale.ROOT);
    var masked = hash.substring(0, Math.max(0, original.length() - 2)).toUpperCase(Locale.ROOT);
    return country + masked.substring(0, Math.min(masked.length(), original.length() - 2));
  }

  private String maskNationalId(String original, String hash) {
    // Preserve format length and any separators, replace digits
    var digits = hash.replaceAll("[^0-9]", "");
    var sb = new StringBuilder();
    int digitIdx = 0;
    for (char c : original.toCharArray()) {
      if (Character.isLetterOrDigit(c)) {
        sb.append(digitIdx < digits.length() ? digits.charAt(digitIdx++) : '0');
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  static String sha256Hex(String input) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      var sb = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  @Override
  public long countMaskedFields(DataRecord record, MaskingConfig config) {
    if (config.isEmpty()) {
      return 0L;
    }
    return record.fields().entrySet().stream()
        .filter(e -> config.ruleFor(e.getKey()).isPresent() && e.getValue() != null)
        .count();
  }

  /** Exposes masking for a raw value + type without a DataRecord wrapper (useful for testing). */
  public String maskValue(String value, MaskerType type) {
    var hash = sha256Hex(value);
    return switch (type) {
      case EMAIL -> maskEmail(hash);
      case PHONE -> maskPhone(value, hash);
      case ADDRESS -> maskAddress(hash);
      case IBAN -> maskIban(value, hash);
      case NATIONAL_ID -> maskNationalId(value, hash);
      default -> maskGeneric(hash);
    };
  }

  /** Fallback masker (also keeps the enum switches non-exhaustive to avoid MatchException). */
  private String maskGeneric(String hash) {
    return hash.substring(0, Math.min(12, hash.length()));
  }

  /** Exposes the underlying hash for determinism tests. */
  static Map<String, String> maskAllTypes(String value) {
    var svc = new DefaultMaskingService();
    var result = new LinkedHashMap<String, String>();
    for (var type : MaskerType.values()) {
      result.put(type.name(), svc.maskValue(value, type));
    }
    return result;
  }
}
