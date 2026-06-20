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

import io.recordrelay.core.clone.domain.BusinessEntity;
import io.recordrelay.core.clone.port.out.EntityRegistryPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of {@link BusinessEntity} definitions, pre-populated with common e-commerce
 * and SaaS domain entities.
 *
 * <p>Built-in entities:
 *
 * <pre>
 * customer     → customers.id
 * order        → orders.id
 * user         → users.id
 * product      → products.id
 * invoice      → invoices.id
 * payment      → payments.id
 * subscription → subscriptions.id
 * ticket       → tickets.id
 * account      → accounts.id
 * session      → sessions.id
 * </pre>
 *
 * <p>Custom entities can be registered via {@link #register(BusinessEntity)}.
 */
public final class BuiltinEntityRegistry implements EntityRegistryPort {

  /** Shared singleton pre-populated with all built-in entities. */
  public static final BuiltinEntityRegistry INSTANCE = new BuiltinEntityRegistry();

  // Common entity constants for direct use in ContextClonePlan builders.
  public static final BusinessEntity CUSTOMER =
      BusinessEntity.of("customer", "customers", "id", "E-commerce or SaaS customer record");
  public static final BusinessEntity ORDER =
      BusinessEntity.of("order", "orders", "id", "Purchase or work order");
  public static final BusinessEntity USER =
      BusinessEntity.of("user", "users", "id", "Application user account");
  public static final BusinessEntity PRODUCT =
      BusinessEntity.of("product", "products", "id", "Catalogue product or SKU");
  public static final BusinessEntity INVOICE =
      BusinessEntity.of("invoice", "invoices", "id", "Billing invoice");
  public static final BusinessEntity PAYMENT =
      BusinessEntity.of("payment", "payments", "id", "Payment transaction");
  public static final BusinessEntity SUBSCRIPTION =
      BusinessEntity.of("subscription", "subscriptions", "id", "Recurring subscription");
  public static final BusinessEntity TICKET =
      BusinessEntity.of("ticket", "tickets", "id", "Support or work ticket");
  public static final BusinessEntity ACCOUNT =
      BusinessEntity.of("account", "accounts", "id", "Organisation or team account");
  public static final BusinessEntity SESSION =
      BusinessEntity.of("session", "sessions", "id", "User session or authentication token");

  private final ConcurrentHashMap<String, BusinessEntity> byName = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, BusinessEntity> byTable = new ConcurrentHashMap<>();

  private BuiltinEntityRegistry() {
    for (var entity :
        List.of(
            CUSTOMER,
            ORDER,
            USER,
            PRODUCT,
            INVOICE,
            PAYMENT,
            SUBSCRIPTION,
            TICKET,
            ACCOUNT,
            SESSION)) {
      index(entity);
    }
  }

  @Override
  public Optional<BusinessEntity> findByName(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT)));
  }

  @Override
  public Optional<BusinessEntity> findByTableName(String tableName) {
    if (tableName == null || tableName.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(byTable.get(tableName.toLowerCase(Locale.ROOT)));
  }

  @Override
  public List<BusinessEntity> listAll() {
    var list = new ArrayList<>(byName.values());
    list.sort((a, b) -> a.name().compareTo(b.name()));
    return List.copyOf(list);
  }

  @Override
  public void register(BusinessEntity entity) {
    index(entity);
  }

  private void index(BusinessEntity entity) {
    byName.put(entity.name().toLowerCase(Locale.ROOT), entity);
    byTable.put(entity.tableName().toLowerCase(Locale.ROOT), entity);
  }
}
