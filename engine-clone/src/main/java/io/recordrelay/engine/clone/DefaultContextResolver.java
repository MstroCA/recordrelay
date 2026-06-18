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

import io.recordrelay.core.clone.domain.CloneRequest;
import io.recordrelay.core.clone.domain.ContextClonePlan;
import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.clone.port.out.ContextResolverPort;
import io.recordrelay.core.clone.port.out.EntityRegistryPort;
import java.util.Objects;

/**
 * Resolves a {@link ContextClonePlan} to a table-level {@link CloneRequest} by looking up the
 * entity's table name and ID column from an {@link EntityRegistryPort}.
 *
 * <p>If the entity is found in the registry, its {@code tableName} and {@code idColumn} are used.
 * If not found, the entity's {@code tableName} and {@code idColumn} fields are used directly (the
 * caller may have constructed a custom {@link io.recordrelay.core.clone.domain.BusinessEntity}
 * inline without registering it).
 */
public final class DefaultContextResolver implements ContextResolverPort {

  private final EntityRegistryPort registry;

  public DefaultContextResolver(EntityRegistryPort registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  /** Creates a resolver backed by the built-in entity registry. */
  public static DefaultContextResolver withBuiltins() {
    return new DefaultContextResolver(BuiltinEntityRegistry.INSTANCE);
  }

  @Override
  public CloneRequest resolve(ContextClonePlan plan) throws CloneException {
    Objects.requireNonNull(plan, "plan");

    // Look up canonical entity definition; fall back to what's in the plan itself.
    var entity = registry.findByName(plan.entity().name()).orElse(plan.entity());

    var target = plan.target();
    if (target == null) {
      throw new CloneException(
          "Cannot resolve to a live CloneRequest: plan has no target profile. "
              + "Use exportContext() for export-only plans.");
    }

    return CloneRequest.builder(plan.source(), target, entity.tableName(), plan.entityId())
        .depth(plan.depth())
        .masking(plan.masking())
        .build();
  }
}
