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
package io.recordrelay.connector.dynamodb;

import java.math.BigDecimal;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** Converts between DynamoDB {@link AttributeValue} and plain Java objects. */
final class AttributeValues {

  private AttributeValues() {}

  static Object toObject(AttributeValue av) {
    if (av == null) {
      return null;
    }
    if (Boolean.TRUE.equals(av.nul())) {
      return null;
    }
    if (av.s() != null) {
      return av.s();
    }
    if (av.n() != null) {
      return new BigDecimal(av.n());
    }
    if (av.bool() != null) {
      return av.bool();
    }
    if (av.b() != null) {
      return av.b().asByteArray();
    }
    if (av.hasSs()) {
      return av.ss();
    }
    if (av.hasNs()) {
      return av.ns().stream().map(BigDecimal::new).toList();
    }
    if (av.hasL()) {
      return av.l().stream().map(AttributeValues::toObject).toList();
    }
    if (av.hasM()) {
      var map = new java.util.LinkedHashMap<String, Object>();
      av.m().forEach((k, v) -> map.put(k, toObject(v)));
      return map;
    }
    return null;
  }

  static AttributeValue fromObject(Object val) {
    if (val == null) {
      return AttributeValue.builder().nul(true).build();
    }
    if (val instanceof String s) {
      // DynamoDB rejects empty strings in legacy mode; replace with a single space sentinel.
      return AttributeValue.fromS(s.isEmpty() ? " " : s);
    }
    if (val instanceof Number n) {
      return AttributeValue.fromN(n.toString());
    }
    if (val instanceof Boolean b) {
      return AttributeValue.fromBool(b);
    }
    if (val instanceof byte[] bytes) {
      return AttributeValue.fromB(SdkBytes.fromByteArray(bytes));
    }
    // Fallback: coerce to string
    return AttributeValue.fromS(val.toString());
  }
}
