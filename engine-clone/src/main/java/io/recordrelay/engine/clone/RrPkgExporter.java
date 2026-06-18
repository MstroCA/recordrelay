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
 * Writes a {@code .rrpkg} archive (ZIP) containing:
 *
 * <ul>
 *   <li>{@code metadata.json} — {@link PackageManifest}
 *   <li>{@code relationships.json} — edge list from the {@link RelationshipGraph}
 *   <li>{@code records/<table>.jsonl} — one JSON-Lines file per table
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
        writeRelationships(zos, graph);
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

  private void writeRecords(ZipOutputStream zos, Map<String, List<DataRecord>> records)
      throws IOException {
    for (var entry : records.entrySet()) {
      var table = entry.getKey();
      zos.putNextEntry(new ZipEntry("records/" + table + ".jsonl"));
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
    map.put("tableNames", manifest.tableNames());
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
