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
package io.recordrelay.core.clone.domain;

import java.util.Objects;

/**
 * Represents a named business concept and its database mapping.
 *
 * <p>RecordRelay thinks in business terms, not table names. A {@code BusinessEntity} bridges the
 * human concept ("customer") with the database reality ("customers" table, "id" column).
 *
 * <p>Example built-in entities:
 *
 * <pre>
 * customer → customers.id
 * order    → orders.id
 * user     → users.id
 * product  → products.id
 * invoice  → invoices.id
 * </pre>
 *
 * <p>Custom entities can be registered via {@link
 * io.recordrelay.core.clone.port.out.EntityRegistryPort}.
 */
public record BusinessEntity(
    String name, String displayName, String tableName, String idColumn, String description) {

  public BusinessEntity {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(tableName, "tableName");
    Objects.requireNonNull(idColumn, "idColumn");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (tableName.isBlank()) {
      throw new IllegalArgumentException("tableName must not be blank");
    }
    if (idColumn.isBlank()) {
      throw new IllegalArgumentException("idColumn must not be blank");
    }
    name = name.toLowerCase();
  }

  /**
   * Convenience factory: creates an entity where the table name is the pluralised entity name and
   * the id column is "id".
   *
   * @param name entity name, e.g. "customer"
   * @param tableName e.g. "customers"
   */
  public static BusinessEntity of(String name, String tableName) {
    var display = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    return new BusinessEntity(name, display, tableName, "id", null);
  }

  /**
   * Full factory.
   *
   * @param name entity name, e.g. "customer"
   * @param tableName e.g. "customers"
   * @param idColumn primary-key column, e.g. "customer_id"
   * @param description human-readable description
   */
  public static BusinessEntity of(
      String name, String tableName, String idColumn, String description) {
    var display = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    return new BusinessEntity(name, display, tableName, idColumn, description);
  }
}
