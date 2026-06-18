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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.recordrelay.core.clone.domain.ImportedPackage;
import io.recordrelay.core.clone.domain.PackageManifest;
import io.recordrelay.core.clone.exception.CloneException;
import io.recordrelay.core.clone.port.out.PackageImporterPort;
import io.recordrelay.core.domain.DataRecord;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a {@code .rrpkg} archive (ZIP) produced by {@link RrPkgExporter} and returns an {@link
 * ImportedPackage}.
 */
public final class RrPkgImporter implements PackageImporterPort {

  private static final Logger LOG = LoggerFactory.getLogger(RrPkgImporter.class);

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final TypeReference<List<String>> LIST_STRING_TYPE = new TypeReference<>() {};

  private final ObjectMapper mapper;

  public RrPkgImporter() {
    this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  @Override
  public ImportedPackage importFrom(Path packagePath) throws CloneException {
    try (var zip = new ZipFile(packagePath.toFile())) {
      var manifest = readManifest(zip);
      var records = readRecords(zip, manifest.tableNames());
      LOG.info(
          "Imported .rrpkg from {}; {} tables, {} total records",
          packagePath.getFileName(),
          records.size(),
          records.values().stream().mapToLong(List::size).sum());
      return new ImportedPackage(manifest, records);
    } catch (IOException e) {
      throw new CloneException("Failed to read .rrpkg archive: " + packagePath, e);
    }
  }

  private PackageManifest readManifest(ZipFile zip) throws IOException, CloneException {
    var entry = zip.getEntry("metadata.json");
    if (entry == null) {
      throw new CloneException("Invalid .rrpkg: missing metadata.json");
    }
    try (var stream = zip.getInputStream(entry)) {
      var map = mapper.readValue(stream, MAP_TYPE);
      var version = (String) map.get("formatVersion");
      if (!PackageManifest.CURRENT_VERSION.equals(version)) {
        throw new CloneException(
            "Unsupported .rrpkg format version: "
                + version
                + "; expected "
                + PackageManifest.CURRENT_VERSION);
      }
      @SuppressWarnings("unchecked")
      var tableNames = (List<String>) map.getOrDefault("tableNames", List.of());
      return new PackageManifest(
          version,
          Instant.parse((String) map.get("createdAt")),
          (String) map.get("sourceConnectorId"),
          (String) map.get("rootTable"),
          (String) map.get("rootId"),
          tableNames,
          null);
    }
  }

  private Map<String, List<DataRecord>> readRecords(ZipFile zip, List<String> tableNames)
      throws IOException {
    var result = new LinkedHashMap<String, List<DataRecord>>();
    for (String table : tableNames) {
      var entry = zip.getEntry("records/" + table + ".jsonl");
      if (entry == null) {
        LOG.warn("No records file found for table '{}'", table);
        result.put(table, List.of());
        continue;
      }
      var records = new ArrayList<DataRecord>();
      try (var reader =
          new BufferedReader(
              new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (!line.isBlank()) {
            var fields = mapper.readValue(line, new TypeReference<Map<String, Object>>() {});
            records.add(new DataRecord(new HashMap<>(fields)));
          }
        }
      }
      result.put(table, List.copyOf(records));
      LOG.debug("Read {} records for table '{}'", records.size(), table);
    }
    return result;
  }
}
