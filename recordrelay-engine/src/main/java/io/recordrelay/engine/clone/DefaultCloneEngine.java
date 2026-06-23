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
import io.recordrelay.core.clone.domain.CloneJob;
import io.recordrelay.core.clone.domain.CloneReport;
import io.recordrelay.core.clone.domain.CloneRequest;
import io.recordrelay.core.clone.domain.ClonedTableSummary;
import io.recordrelay.core.clone.domain.FieldOverrideConfig;
import io.recordrelay.core.clone.domain.IdentityMapping;
import io.recordrelay.core.clone.domain.ImportedPackage;
import io.recordrelay.core.clone.domain.PackageManifest;
import io.recordrelay.core.clone.domain.RelationshipGraph;
import io.recordrelay.core.clone.domain.RelationshipSource;
import io.recordrelay.core.clone.engine.TraversalNode;
import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.clone.port.in.CloneUseCase;
import io.recordrelay.core.clone.port.in.ExportPackageUseCase;
import io.recordrelay.core.clone.port.in.ImportPackageUseCase;
import io.recordrelay.core.clone.port.out.CloneProgressListener;
import io.recordrelay.core.clone.port.out.IdentityMapperPort;
import io.recordrelay.core.clone.port.out.MaskingServicePort;
import io.recordrelay.core.clone.port.out.PackageExporterPort;
import io.recordrelay.core.clone.port.out.PackageImporterPort;
import io.recordrelay.core.clone.port.out.RecordFetcherPort;
import io.recordrelay.core.clone.port.out.RelationshipResolverPort;
import io.recordrelay.core.clone.port.out.SequenceSyncPort;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DataRecord;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.exception.ConnectorException;
import io.recordrelay.core.spi.ConnectorRegistry;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the business-context reproduction workflow.
 *
 * <p>Implements all three context-reproduction use cases:
 *
 * <ul>
 *   <li>{@link CloneUseCase} — {@code rr clone}
 *   <li>{@link ExportPackageUseCase} — {@code rr export}
 *   <li>{@link ImportPackageUseCase} — {@code rr import}
 * </ul>
 *
 * <p>Context clone execution sequence:
 *
 * <ol>
 *   <li>Resolve relationship graph for the root entity
 *   <li>BFS extraction of root record + all transitive context records
 *   <li>Allocate new identities (REGENERATE_IDENTITIES by default)
 *   <li>Remap all foreign keys using the identity mapping
 *   <li>Apply optional deterministic masking (preserves referential integrity)
 *   <li>Write context records into the reproduction environment
 *   <li>Emit {@link CloneReport}
 * </ol>
 */
public final class DefaultCloneEngine
    implements CloneUseCase, ExportPackageUseCase, ImportPackageUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultCloneEngine.class);

  private final RelationshipResolverPort relationshipResolver;
  private final RecordFetcherPort recordFetcher;
  private final IdentityMapperPort identityMapper;
  private final MaskingServicePort maskingService;
  private final PackageExporterPort packageExporter;
  private final PackageImporterPort packageImporter;
  private final SequenceSyncPort sequenceSyncer;

  public DefaultCloneEngine(
      RelationshipResolverPort relationshipResolver,
      RecordFetcherPort recordFetcher,
      IdentityMapperPort identityMapper,
      MaskingServicePort maskingService,
      PackageExporterPort packageExporter,
      PackageImporterPort packageImporter,
      SequenceSyncPort sequenceSyncer) {
    this.relationshipResolver =
        Objects.requireNonNull(relationshipResolver, "relationshipResolver");
    this.recordFetcher = Objects.requireNonNull(recordFetcher, "recordFetcher");
    this.identityMapper = Objects.requireNonNull(identityMapper, "identityMapper");
    this.maskingService = Objects.requireNonNull(maskingService, "maskingService");
    this.packageExporter = Objects.requireNonNull(packageExporter, "packageExporter");
    this.packageImporter = Objects.requireNonNull(packageImporter, "packageImporter");
    this.sequenceSyncer = Objects.requireNonNull(sequenceSyncer, "sequenceSyncer");
  }

  /** Creates an engine with the default JDBC-backed implementations. */
  public static DefaultCloneEngine createDefault() {
    return new DefaultCloneEngine(
        new JdbcRelationshipResolver(),
        new JdbcRecordFetcher(),
        new DefaultIdentityMapper(),
        new DefaultMaskingService(),
        new RrPkgExporter(),
        new RrPkgImporter(),
        new JdbcSequenceSynchronizer());
  }

  @Override
  public CloneReport clone(CloneJob job, CloneProgressListener listener) throws CloneException {
    var request = job.request();
    long startMs = System.currentTimeMillis();
    var warnings = new ArrayList<String>();
    LOG.info("Clone job {} starting: {}.{}", job.id(), request.rootTable(), request.rootId());
    try {
      var report = executeClone(job, request, startMs, warnings, listener);
      CloneHistoryStore.getInstance()
          .record(
              CloneHistorySummary.ofSuccess(
                  report, request.source().name(), request.target().name()));
      return report;
    } catch (CloneException e) {
      CloneHistoryStore.getInstance()
          .record(
              CloneHistorySummary.ofFailure(
                  request.rootTable(),
                  request.rootId(),
                  request.source().name(),
                  request.target().name(),
                  System.currentTimeMillis() - startMs,
                  e.getMessage()));
      throw e;
    }
  }

  private CloneReport executeClone(
      CloneJob job,
      CloneRequest request,
      long startMs,
      List<String> warnings,
      CloneProgressListener listener)
      throws CloneException {
    var graph = relationshipResolver.resolve(request.source(), request.rootTable());
    listener.onRelationshipsDiscovered(graph.edgeCount());
    var rawRecords = bfsExtractRaw(request, graph, warnings, listener);
    var identityMapping =
        identityMapper.allocate(
            request.target(), request.rootTable(), rawRecords, request.conflictResolution());
    listener.onIdentitiesAllocated(identityMapping.totalMappings());
    var remappedRecords = FkRemapper.remap(rawRecords, identityMapping, graph, request.rootTable());
    var finalRecords = applyMaskingAll(remappedRecords, request);
    writeToTarget(request, finalRecords, listener, warnings);
    sequenceSyncer.synchronize(request.target(), identityMapping);
    long maskedFieldCount = countMaskedFieldsAll(rawRecords, request);
    var report =
        new CloneReport(
            request.rootTable(),
            request.rootId(),
            buildSummaries(finalRecords),
            System.currentTimeMillis() - startMs,
            List.copyOf(warnings),
            maskedFieldCount);
    LOG.info(
        "Clone job {} done: {} tables, {} records, {} identity mappings",
        job.id(),
        report.tableCount(),
        report.totalRecords(),
        identityMapping.totalMappings());
    return report;
  }

  @Override
  public Path exportPackage(CloneJob job, Path outputDirectory) throws CloneException {
    return exportPackageWithContext(job, outputDirectory, null, null);
  }

  /**
   * Exports a package with optional entity and bug-report context embedded in the manifest.
   *
   * <p>Called by {@link DefaultContextCloneEngine} when exporting via the semantic API.
   */
  public Path exportPackageWithContext(
      CloneJob job, Path outputDirectory, String businessEntityName, BugReport bugReport)
      throws CloneException {
    var request = job.request();
    var graph = relationshipResolver.resolve(request.source(), request.rootTable());
    var rawRecords =
        bfsExtractRaw(request, graph, new ArrayList<>(), new CloneProgressListener() {});

    // Allocate identities and remap FKs for the export package.
    // For export we allocate from 0 (no live target), so IDs are relative offsets.
    var identityMapping = IdentityMapping.empty();
    var finalRecords = applyMaskingAll(rawRecords, request);

    var manifest =
        PackageManifest.createWithIdentityMapping(
            request.source().type().name().toLowerCase(),
            request.rootTable(),
            request.rootId(),
            new ArrayList<>(finalRecords.keySet()),
            businessEntityName,
            bugReport,
            identityMapping);

    return packageExporter.export(manifest, graph, finalRecords, outputDirectory);
  }

  @Override
  public CloneReport importPackage(
      Path packagePath, ConnectionProfile target, CloneProgressListener listener)
      throws CloneException {
    long startMs = System.currentTimeMillis();
    var warnings = new ArrayList<String>();
    var pkg = packageImporter.importFrom(packagePath);
    writePackageToTarget(pkg, target, listener, warnings);
    var summaries =
        pkg.records().entrySet().stream()
            .map(e -> new ClonedTableSummary(e.getKey(), e.getValue().size()))
            .toList();
    return new CloneReport(
        pkg.manifest().rootTable(),
        pkg.manifest().rootId(),
        summaries,
        System.currentTimeMillis() - startMs,
        List.copyOf(warnings),
        0L);
  }

  // ── BFS extraction ───────────────────────────────────────────────────────────

  private Map<String, List<DataRecord>> bfsExtractRaw(
      CloneRequest request,
      RelationshipGraph graph,
      List<String> warnings,
      CloneProgressListener listener)
      throws CloneException {
    var allRecords = new LinkedHashMap<String, List<DataRecord>>();
    var visited = new HashSet<String>();
    var queue = new ArrayDeque<TraversalNode>();
    queue.add(new TraversalNode(request.rootTable(), "id", request.rootId(), 0));

    while (!queue.isEmpty()) {
      var node = queue.poll();
      if (visited.contains(node.visitKey()) || node.depth() > request.depth()) {
        continue;
      }
      visited.add(node.visitKey());
      processNode(request, graph, node, allRecords, queue, warnings, listener);
    }
    return allRecords;
  }

  private void processNode(
      CloneRequest request,
      RelationshipGraph graph,
      TraversalNode node,
      Map<String, List<DataRecord>> allRecords,
      ArrayDeque<TraversalNode> queue,
      List<String> warnings,
      CloneProgressListener listener)
      throws CloneException {
    listener.onTableExtractionStarted(node.tableName());
    var records = fetchNode(request, node, warnings);
    allRecords.computeIfAbsent(node.tableName(), k -> new ArrayList<>()).addAll(records);
    listener.onTableExtractionCompleted(node.tableName(), records.size());
    if (node.depth() == 0) {
      listener.onRootRecordLoaded(node.tableName(), node.idValue());
    }
    enqueueOutgoing(graph, node, records, queue, warnings, listener);
    enqueueIncoming(graph, node, records, queue);
  }

  private void enqueueOutgoing(
      RelationshipGraph graph,
      TraversalNode node,
      List<DataRecord> records,
      ArrayDeque<TraversalNode> queue,
      List<String> warnings,
      CloneProgressListener listener) {
    for (var edge : graph.edgesFrom(node.tableName())) {
      for (var record : records) {
        var fkValue = record.get(edge.fromColumn());
        if (fkValue == null) {
          if (edge.source() == RelationshipSource.FOREIGN_KEY) {
            var msg = "Null FK value for " + node.tableName() + "." + edge.fromColumn();
            warnings.add(msg);
            listener.onWarning(msg);
          } else {
            LOG.debug(
                "Null heuristic FK for {}.{} — skipping", node.tableName(), edge.fromColumn());
          }
          continue;
        }
        queue.add(
            new TraversalNode(
                edge.toNode().tableName(), edge.toColumn(), fkValue.toString(), node.depth() + 1));
      }
    }
  }

  private void enqueueIncoming(
      RelationshipGraph graph,
      TraversalNode node,
      List<DataRecord> records,
      ArrayDeque<TraversalNode> queue) {
    for (var edge : graph.edgesTo(node.tableName())) {
      for (var record : records) {
        var pkValue = record.get(edge.toColumn());
        if (pkValue != null) {
          queue.add(
              new TraversalNode(
                  edge.fromNode().tableName(),
                  edge.fromColumn(),
                  pkValue.toString(),
                  node.depth() + 1));
        }
      }
    }
  }

  // ── private helpers ──────────────────────────────────────────────────────────

  private List<DataRecord> fetchNode(
      CloneRequest request, TraversalNode node, List<String> warnings) throws CloneException {
    if (node.depth() == 0) {
      return recordFetcher
          .fetchById(request.source(), node.tableName(), node.idColumn(), node.idValue())
          .map(List::of)
          .orElseGet(
              () -> {
                warnings.add("Root record not found: " + node.tableName() + ":" + node.idValue());
                return List.of();
              });
    }
    return recordFetcher.fetchByForeignKey(
        request.source(), node.tableName(), node.idColumn(), node.idValue());
  }

  private Map<String, List<DataRecord>> applyMaskingAll(
      Map<String, List<DataRecord>> records, CloneRequest request) {
    if (request.masking().isEmpty()) {
      return records;
    }
    var result = new LinkedHashMap<String, List<DataRecord>>();
    records.forEach(
        (table, tableRecords) ->
            result.put(
                table,
                tableRecords.stream()
                    .map(r -> maskingService.mask(r, request.masking()))
                    .toList()));
    return result;
  }

  private void writeToTarget(
      CloneRequest request,
      Map<String, List<DataRecord>> allRecords,
      CloneProgressListener listener,
      List<String> warnings)
      throws CloneException {
    var targetConnector = ConnectorRegistry.findConnector(request.target());
    for (var entry : allRecords.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        writeTable(
            targetConnector,
            request.target(),
            entry.getKey(),
            entry.getValue(),
            request.fieldOverrides(),
            listener,
            warnings);
      }
    }
  }

  private void writePackageToTarget(
      ImportedPackage pkg,
      ConnectionProfile target,
      CloneProgressListener listener,
      List<String> warnings)
      throws CloneException {
    var targetConnector = ConnectorRegistry.findConnector(target);
    for (var entry : pkg.records().entrySet()) {
      if (!entry.getValue().isEmpty()) {
        writeTable(
            targetConnector,
            target,
            entry.getKey(),
            entry.getValue(),
            FieldOverrideConfig.none(),
            listener,
            warnings);
      }
    }
  }

  private void writeTable(
      io.recordrelay.core.port.out.ContextProviderPort connector,
      ConnectionProfile target,
      String tableName,
      List<DataRecord> records,
      FieldOverrideConfig overrides,
      CloneProgressListener listener,
      List<String> warnings)
      throws CloneException {
    listener.onImportStarted(tableName);
    var tableRef = new TableRef(new DatabaseRef(target.database(), target.type()), "", tableName);
    try (var writer = connector.createWriter()) {
      writer.open(target, tableRef);
      for (var record : records) {
        writer.write(applyFieldOverrides(record, tableName, overrides));
      }
      writer.flush();
    } catch (ConnectorException e) {
      var msg = "Failed to write table " + tableName + ": " + e.getMessage();
      warnings.add(msg);
      LOG.warn(msg, e);
    } catch (Exception e) {
      throw new CloneException("Unexpected error writing " + tableName, e);
    }
    listener.onImportCompleted(tableName, records.size());
  }

  private DataRecord applyFieldOverrides(
      DataRecord record, String tableName, FieldOverrideConfig overrides) {
    if (overrides.isEmpty()) {
      return record;
    }
    var applicable = overrides.getOverridesFor(tableName);
    if (applicable.isEmpty()) {
      return record;
    }
    var fields = new LinkedHashMap<>(record.fields());
    for (var override : applicable) {
      if (fields.containsKey(override.column())) {
        fields.put(override.column(), override.value());
      }
    }
    return new DataRecord(fields);
  }

  private long countMaskedFieldsAll(
      Map<String, List<DataRecord>> rawRecords, CloneRequest request) {
    if (request.masking().isEmpty()) {
      return 0L;
    }
    return rawRecords.values().stream()
        .flatMap(List::stream)
        .mapToLong(r -> maskingService.countMaskedFields(r, request.masking()))
        .sum();
  }

  private List<ClonedTableSummary> buildSummaries(Map<String, List<DataRecord>> allRecords) {
    return allRecords.entrySet().stream()
        .map(e -> new ClonedTableSummary(e.getKey(), e.getValue().size()))
        .toList();
  }
}
