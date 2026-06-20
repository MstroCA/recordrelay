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

import io.recordrelay.core.clone.domain.BugReport;
import io.recordrelay.core.clone.domain.CloneReport;
import io.recordrelay.core.clone.domain.PackageManifest;
import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.clone.port.in.ReplayUseCase;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.core.clone.port.out.PackageImporterPort;
import io.recordrelay.core.domain.ConnectionProfile;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replays a {@code .rrpkg} reproduction package into a target environment.
 *
 * <p>Replay is distinct from plain import: before writing records, the engine reads the package
 * manifest and logs any embedded {@link BugReport} so that engineers know exactly what issue the
 * data set was captured to reproduce.
 *
 * <p>Usage:
 *
 * <pre>
 * DefaultReplayEngine engine = DefaultReplayEngine.createDefault();
 * CloneReport report = engine.replay(Path.of("bug-1234.rrpkg"), localProfile, listener);
 * </pre>
 */
public final class DefaultReplayEngine implements ReplayUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultReplayEngine.class);

  private final PackageImporterPort importer;
  private final DefaultCloneEngine delegate;

  public DefaultReplayEngine(PackageImporterPort importer, DefaultCloneEngine delegate) {
    this.importer = Objects.requireNonNull(importer, "importer");
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  /** Creates a replay engine with default JDBC-backed implementations. */
  public static DefaultReplayEngine createDefault() {
    return new DefaultReplayEngine(new RrPkgImporter(), DefaultCloneEngine.createDefault());
  }

  @Override
  public PackageManifest inspect(Path packagePath) throws CloneException {
    Objects.requireNonNull(packagePath, "packagePath");
    var pkg = importer.importFrom(packagePath);
    return pkg.manifest();
  }

  @Override
  public CloneReport replay(
      Path packagePath, ConnectionProfile target, CloneProgressListener listener)
      throws CloneException {
    Objects.requireNonNull(packagePath, "packagePath");
    Objects.requireNonNull(target, "target");

    var pkg = importer.importFrom(packagePath);
    var manifest = pkg.manifest();

    logReplayContext(packagePath, manifest);

    if (manifest.hasEntityContext()) {
      listener.onTableExtractionStarted(manifest.businessEntityName() + " #" + manifest.rootId());
    }

    return delegate.importPackage(packagePath, target, listener);
  }

  private void logReplayContext(Path packagePath, PackageManifest manifest) {
    LOG.info(
        "Replaying {} — root: {}/{}, tables: {}, captured: {}",
        packagePath.getFileName(),
        manifest.rootTable(),
        manifest.rootId(),
        manifest.tableNames().size(),
        manifest.createdAt());

    if (manifest.hasBugReport()) {
      var bug = manifest.bugReport();
      LOG.info(
          "Bug context: [{}] {} — service: {}, env: {}",
          bug.id(),
          bug.title(),
          bug.service() != null ? bug.service() : "unknown",
          bug.environment() != null ? bug.environment() : "unknown");
      if (bug.stepsToReproduce() != null) {
        LOG.info("Steps to reproduce: {}", bug.stepsToReproduce());
      }
    }
  }
}
