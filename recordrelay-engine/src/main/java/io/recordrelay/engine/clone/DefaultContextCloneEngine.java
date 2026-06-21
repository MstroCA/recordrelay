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

import io.recordrelay.core.clone.domain.CloneJob;
import io.recordrelay.core.clone.domain.CloneReport;
import io.recordrelay.core.clone.domain.ContextClonePlan;
import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.clone.port.in.ContextCloneUseCase;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.core.clone.port.out.ContextResolverPort;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entity-level clone engine that resolves business context plans to table-level operations and
 * delegates to {@link DefaultCloneEngine}.
 *
 * <p>This is the primary entry point for the {@code rr clone --customer-id 123} workflow.
 *
 * <pre>
 * ContextClonePlan
 *   └─ DefaultContextResolver → CloneRequest
 *        └─ DefaultCloneEngine.clone() / exportPackage()
 * </pre>
 */
public final class DefaultContextCloneEngine implements ContextCloneUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultContextCloneEngine.class);

  private final ContextResolverPort resolver;
  private final DefaultCloneEngine delegate;

  public DefaultContextCloneEngine(ContextResolverPort resolver, DefaultCloneEngine delegate) {
    this.resolver = Objects.requireNonNull(resolver, "resolver");
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  /** Creates an engine backed by the built-in entity registry and default JDBC adapters. */
  public static DefaultContextCloneEngine createDefault() {
    return new DefaultContextCloneEngine(
        new DefaultContextResolver(BuiltinEntityRegistry.INSTANCE),
        DefaultCloneEngine.createDefault());
  }

  @Override
  public CloneReport cloneContext(ContextClonePlan plan, CloneProgressListener listener)
      throws CloneException {
    Objects.requireNonNull(plan, "plan");
    if (!plan.isLiveClone()) {
      throw new IllegalArgumentException(
          "Plan has no target profile — use exportContext() for export-only plans");
    }

    LOG.info(
        "Context clone: {} '{}' from '{}' → '{}'",
        plan.entity().displayName(),
        plan.entityId(),
        plan.source().name(),
        plan.target().name());

    var request = resolver.resolve(plan);
    var job = CloneJob.of(request);
    return delegate.clone(job, listener);
  }

  @Override
  public Path exportContext(ContextClonePlan plan) throws CloneException {
    Objects.requireNonNull(plan, "plan");
    Objects.requireNonNull(plan.outputDirectory(), "plan.outputDirectory must be set for export");

    LOG.info(
        "Context export: {} '{}' from '{}'",
        plan.entity().displayName(),
        plan.entityId(),
        plan.source().name());

    // For export, we need a temporary target that's the same as source (we only read, not write).
    var exportPlan =
        plan.isLiveClone()
            ? plan
            : new ContextClonePlan(
                plan.entity(),
                plan.entityId(),
                plan.source(),
                plan.source(), // dummy target — unused during export
                plan.depth(),
                plan.masking(),
                plan.outputDirectory(),
                plan.bugReport(),
                plan.fieldOverrides());

    var request = resolver.resolve(exportPlan);
    var job = CloneJob.of(request);

    // Build a v2.0 manifest with entity + bug report context.
    var bugReport = plan.bugReport();
    return delegate.exportPackageWithContext(
        job, plan.outputDirectory(), plan.entity().name(), bugReport);
  }
}
