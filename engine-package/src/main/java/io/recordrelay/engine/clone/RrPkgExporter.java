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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.recordrelay.core.clone.domain.PackageManifest;
import io.recordrelay.core.clone.domain.RelationshipEdge;
import io.recordrelay.core.clone.domain.RelationshipGraph;
import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.clone.port.out.PackageExporterPort;
import io.recordrelay.core.domain.DataRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes a {@code .rrpkg} archive (ZIP) in v2.1 format:
 *
 * <ul>
 *   <li>{@code metadata.json} — {@link PackageManifest} (v2.1)
 *   <li>{@code schema.json} — inferred column schema per table
 *   <li>{@code relationships.json} — edge list from the {@link RelationshipGraph}
 *   <li>{@code identity-mapping.json} — source → target ID mapping per table
 *   <li>{@code masking-rules.json} — list of masking rules applied (if any)
 *   <li>{@code data/<table>.jsonl} — one JSON-Lines file per table
 * </ul>
 */
public final class RrPkgExporter implements PackageExporterPort {

  private static final Logger LOG = LoggerFactory.getLogger(RrPkgExporter.class);

  private final ObjectMapper mapper;

  public RrPkgExporter() {
    this.mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  @Override
  public Path export(
      PackageManifest manifest,
      RelationshipGraph graph,
      Map<String, List<DataRecord>> records,
      Path outputDirectory)
      throws CloneException {
    try {
      Files.createDirectories(outputDirectory);
      var fileName =
          manifest.rootTable()
              + "-"
              + manifest.rootId()
              + "-"
              + manifest.createdAt().getEpochSecond()
              + ".rrpkg";
      var pkgPath = outputDirectory.resolve(fileName);

      try (var zos = new ZipOutputStream(Files.newOutputStream(pkgPath))) {
        zos.setComment("RecordRelay package v" + PackageManifest.CURRENT_VERSION);

        writeMetadata(zos, manifest);
        writeSchema(zos, records);
        writeRelationships(zos, graph);
        writeIdentityMapping(zos, manifest);
        writeMaskingRules(zos, manifest);
        writeRecords(zos, records);
      }

      LOG.info("Exported .rrpkg to {}", pkgPath);
      return pkgPath;
    } catch (IOException e) {
      throw new CloneException("Failed to write .rrpkg archive", e);
    }
  }

  private void writeMetadata(ZipOutputStream zos, PackageManifest manifest) throws IOException {
    zos.putNextEntry(new ZipEntry("metadata.json"));
    zos.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(toManifestMap(manifest)));
    zos.closeEntry();
  }

  private void writeRelationships(ZipOutputStream zos, RelationshipGraph graph) throws IOException {
    zos.putNextEntry(new ZipEntry("relationships.json"));
    zos.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(toEdgeList(graph)));
    zos.closeEntry();
  }

  private void writeSchema(ZipOutputStream zos, Map<String, List<DataRecord>> records)
      throws IOException {
    var schema = new LinkedHashMap<String, Object>();
    schema.put("formatVersion", PackageManifest.CURRENT_VERSION);
    var tables = new LinkedHashMap<String, Object>();
    for (var entry : records.entrySet()) {
      var tableSchema = new LinkedHashMap<String, Object>();
      var columns =
          entry.getValue().isEmpty()
              ? List.of()
              : new ArrayList<>(entry.getValue().get(0).fields().keySet());
      tableSchema.put("columns", columns);
      tables.put(entry.getKey(), tableSchema);
    }
    schema.put("tables", tables);
    zos.putNextEntry(new ZipEntry("schema.json"));
    zos.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(schema));
    zos.closeEntry();
  }

  private void writeIdentityMapping(ZipOutputStream zos, PackageManifest manifest)
      throws IOException {
    zos.putNextEntry(new ZipEntry("identity-mapping.json"));
    var mapping = manifest.identityMapping();
    var content = mapping != null ? mapping.snapshot() : Map.of();
    zos.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(content));
    zos.closeEntry();
  }

  private void writeMaskingRules(ZipOutputStream zos, PackageManifest manifest) throws IOException {
    zos.putNextEntry(new ZipEntry("masking-rules.json"));
    // Masking rules are embedded in the manifest report; emit empty list when not available.
    zos.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(List.of()));
    zos.closeEntry();
  }

  private void writeRecords(ZipOutputStream zos, Map<String, List<DataRecord>> records)
      throws IOException {
    for (var entry : records.entrySet()) {
      var table = entry.getKey();
      // v2.1: data/ folder (was records/ in v2.0)
      zos.putNextEntry(new ZipEntry("data/" + table + ".jsonl"));
      for (var record : entry.getValue()) {
        var line = mapper.writeValueAsString(record.fields()) + "\n";
        zos.write(line.getBytes(StandardCharsets.UTF_8));
      }
      zos.closeEntry();
      LOG.debug("Wrote {} records for table '{}'", entry.getValue().size(), table);
    }
  }

  private Map<String, Object> toManifestMap(PackageManifest manifest) {
    var map = new LinkedHashMap<String, Object>();
    map.put("formatVersion", manifest.formatVersion());
    map.put("createdAt", manifest.createdAt().toString());
    map.put("sourceConnectorId", manifest.sourceConnectorId());
    map.put("rootTable", manifest.rootTable());
    map.put("rootId", manifest.rootId());
    if (manifest.businessEntityName() != null) {
      map.put("businessEntityName", manifest.businessEntityName());
    }
    map.put("tableNames", manifest.tableNames());
    if (manifest.hasBugReport()) {
      map.put("bugReport", toBugReportMap(manifest.bugReport()));
    }
    if (manifest.hasIdentityMapping()) {
      var stats = new LinkedHashMap<String, Object>();
      stats.put("totalMappings", manifest.identityMapping().totalMappings());
      map.put("identityMappingStats", stats);
    }
    return map;
  }

  private Map<String, Object> toBugReportMap(io.recordrelay.core.clone.domain.BugReport bugReport) {
    var map = new LinkedHashMap<String, Object>();
    map.put("id", bugReport.id());
    map.put("title", bugReport.title());
    if (bugReport.service() != null) {
      map.put("service", bugReport.service());
    }
    if (bugReport.environment() != null) {
      map.put("environment", bugReport.environment());
    }
    map.put("capturedAt", bugReport.capturedAt().toString());
    if (bugReport.stepsToReproduce() != null) {
      map.put("stepsToReproduce", bugReport.stepsToReproduce());
    }
    return map;
  }

  private List<Map<String, Object>> toEdgeList(RelationshipGraph graph) {
    var list = new ArrayList<Map<String, Object>>(graph.edgeCount());
    for (RelationshipEdge edge : graph.edges()) {
      var m = new LinkedHashMap<String, Object>();
      m.put("fromTable", edge.fromNode().tableName());
      m.put("fromColumn", edge.fromColumn());
      m.put("toTable", edge.toNode().tableName());
      m.put("toColumn", edge.toColumn());
      m.put("source", edge.source().name());
      m.put("confidence", edge.confidence());
      list.add(m);
    }
    return list;
  }
}
