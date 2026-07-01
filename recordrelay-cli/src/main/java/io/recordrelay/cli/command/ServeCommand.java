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
package io.recordrelay.cli.command;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.recordrelay.cli.ExitCode;
import io.recordrelay.cli.RecordRelayCli;
import io.recordrelay.cli.config.PresetStore;
import io.recordrelay.cli.config.SyncStore;
import io.recordrelay.cli.engine.ConnProfileResolver;
import io.recordrelay.core.clone.domain.BusinessEntity;
import io.recordrelay.core.clone.domain.ContextClonePlan;
import io.recordrelay.core.clone.domain.MaskerType;
import io.recordrelay.core.clone.domain.MaskingConfig;
import io.recordrelay.core.clone.domain.MaskingRule;
import io.recordrelay.engine.clone.BuiltinEntityRegistry;
import io.recordrelay.engine.clone.DefaultContextCloneEngine;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * {@code rr serve} — starts RecordRelay as a headless HTTP API server.
 *
 * <p>Listens on the given port and exposes a REST-ish API suitable for CI/CD pipelines:
 *
 * <pre>
 * GET  /health                   — liveness probe
 * GET  /syncs                    — list scheduled syncs (JSON)
 * GET  /presets                  — list clone presets (JSON)
 * POST /clone                    — trigger a live clone
 * POST /sync/run/{name}          — run a scheduled sync by name
 * POST /preset/run/{name}        — run a clone preset by name
 * </pre>
 *
 * <p>Request / response bodies are JSON. No authentication is included; secure the port at the
 * network level (firewall, reverse proxy) if needed.
 */
@Command(
    name = "serve",
    description = "Start RecordRelay as a headless HTTP API server for CI/CD pipeline integration.")
public final class ServeCommand implements Callable<Integer> {

  @ParentCommand private RecordRelayCli parent;

  @Option(
      names = {"--port", "-p"},
      description = "Port to listen on (default: 8080)",
      defaultValue = "8080")
  int port;

  @Option(
      names = {"--threads"},
      description = "Thread pool size for concurrent requests (default: 4)",
      defaultValue = "4")
  int threads;

  @Override
  public Integer call() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
      server.setExecutor(Executors.newFixedThreadPool(threads));

      server.createContext("/health", this::handleHealth);
      server.createContext("/syncs", this::handleSyncs);
      server.createContext("/presets", this::handlePresets);
      server.createContext("/clone", this::handleClone);
      server.createContext("/sync/run/", this::handleSyncRun);
      server.createContext("/preset/run/", this::handlePresetRun);

      server.start();
      parent.printer().printLine("RecordRelay API server listening on port " + port);
      parent.printer().printLine("Endpoints:");
      parent.printer().printLine("  GET  http://localhost:" + port + "/health");
      parent.printer().printLine("  GET  http://localhost:" + port + "/syncs");
      parent.printer().printLine("  GET  http://localhost:" + port + "/presets");
      parent.printer().printLine("  POST http://localhost:" + port + "/clone");
      parent.printer().printLine("  POST http://localhost:" + port + "/sync/run/{name}");
      parent.printer().printLine("  POST http://localhost:" + port + "/preset/run/{name}");
      parent.printer().printLine("Press Ctrl+C to stop.");

      // Block until interrupted
      Thread.currentThread().join();
      return ExitCode.SUCCESS;
    } catch (Exception e) {
      return EnvCommand.handleError(parent, e, ExitCode.CONFIG_ERROR);
    }
  }

  // ── GET /health ───────────────────────────────────────────────────────────

  private void handleHealth(HttpExchange ex) throws IOException {
    if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
      sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
      return;
    }
    sendJson(ex, 200, "{\"status\":\"ok\",\"service\":\"recordrelay\"}");
  }

  // ── GET /syncs ────────────────────────────────────────────────────────────

  private void handleSyncs(HttpExchange ex) throws IOException {
    if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
      sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
      return;
    }
    try {
      var syncs = new SyncStore().loadAll();
      var sb = new StringBuilder("[");
      for (int i = 0; i < syncs.size(); i++) {
        var s = syncs.get(i);
        if (i > 0) sb.append(',');
        sb.append('{')
            .append(jf("name", s.getName()))
            .append(',')
            .append(jf("entity", s.getEntityName()))
            .append(',')
            .append(jf("entityId", s.getEntityId()))
            .append(',')
            .append(jf("source", s.getSourceConn()))
            .append(',')
            .append(jf("target", s.getTargetConn()))
            .append(',')
            .append("\"depth\":")
            .append(s.getDepth())
            .append(',')
            .append("\"maskPii\":")
            .append(s.isMaskPii())
            .append(',')
            .append(jf("schedule", s.scheduleLabel()))
            .append(',')
            .append(jf("lastRunAt", s.getLastRunAt()))
            .append(',')
            .append(jf("lastStatus", s.getLastStatus()))
            .append('}');
      }
      sb.append(']');
      sendJson(ex, 200, sb.toString());
    } catch (Exception e) {
      sendJson(ex, 500, "{\"error\":" + js(e.getMessage()) + "}");
    }
  }

  // ── GET /presets ──────────────────────────────────────────────────────────

  private void handlePresets(HttpExchange ex) throws IOException {
    if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
      sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
      return;
    }
    try {
      var presets = new PresetStore().loadAll();
      var sb = new StringBuilder("[");
      for (int i = 0; i < presets.size(); i++) {
        var p = presets.get(i);
        if (i > 0) sb.append(',');
        sb.append('{')
            .append(jf("name", p.getName()))
            .append(',')
            .append(jf("entity", p.getEntityName()))
            .append(',')
            .append(jf("entityId", p.getEntityId()))
            .append(',')
            .append(jf("source", p.getSourceConn()))
            .append(',')
            .append(jf("target", p.getTargetConn()))
            .append(',')
            .append("\"depth\":")
            .append(p.getDepth())
            .append(',')
            .append("\"maskPii\":")
            .append(p.isMaskPii())
            .append(',')
            .append(jf("description", p.getDescription()))
            .append('}');
      }
      sb.append(']');
      sendJson(ex, 200, sb.toString());
    } catch (Exception e) {
      sendJson(ex, 500, "{\"error\":" + js(e.getMessage()) + "}");
    }
  }

  // ── POST /clone ───────────────────────────────────────────────────────────

  /**
   * Expects JSON body:
   *
   * <pre>
   * {
   *   "entity": "customer",
   *   "id": "123",
   *   "source": "prod",
   *   "target": "local",
   *   "depth": 3,
   *   "maskPii": false
   * }
   * </pre>
   */
  private void handleClone(HttpExchange ex) throws IOException {
    if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
      sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
      return;
    }
    try {
      var body = readBody(ex.getRequestBody());
      var entity = extractField(body, "entity");
      var entityId = extractField(body, "id");
      var source = extractField(body, "source");
      var target = extractField(body, "target");
      int depth = extractIntField(body, "depth", 3);
      boolean maskPii = extractBoolField(body, "maskPii", false);

      if (entity == null || entityId == null || source == null || target == null) {
        sendJson(ex, 400, "{\"error\":\"Required fields: entity, id, source, target\"}");
        return;
      }

      var store = parent.configStore();
      var resolver = new ConnProfileResolver(store);
      var srcProfile = resolver.resolve(source);
      var tgtProfile = resolver.resolve(target);
      var masking = maskPii ? standardMasking() : MaskingConfig.none();

      var businessEntity =
          BuiltinEntityRegistry.INSTANCE
              .findByName(entity)
              .orElseGet(() -> BusinessEntity.of(entity, entity + "s"));
      var plan =
          ContextClonePlan.liveCloneWithOverrides(
              businessEntity,
              entityId,
              srcProfile,
              tgtProfile,
              depth,
              masking,
              io.recordrelay.core.clone.domain.FieldOverrideConfig.none(),
              io.recordrelay.core.clone.domain.ConflictResolution.REGENERATE_IDENTITIES,
              new io.recordrelay.cli.engine.SatelliteConfigResolver(store, resolver)
                  .configForEntity(businessEntity.name()));

      var report = DefaultContextCloneEngine.createDefault().cloneContext(plan, null);

      sendJson(
          ex,
          200,
          "{"
              + jf("status", "ok")
              + ","
              + jf("entity", entity)
              + ","
              + jf("entityId", entityId)
              + ","
              + "\"records\":"
              + report.totalRecords()
              + ","
              + jf("duration", report.formattedDuration())
              + "}");
    } catch (Exception e) {
      sendJson(ex, 500, "{\"error\":" + js(e.getMessage()) + "}");
    }
  }

  // ── POST /sync/run/{name} ─────────────────────────────────────────────────

  private void handleSyncRun(HttpExchange ex) throws IOException {
    if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
      sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
      return;
    }
    var path = ex.getRequestURI().getPath();
    var name = path.substring("/sync/run/".length());
    if (name.isBlank()) {
      sendJson(ex, 400, "{\"error\":\"Sync name missing in path\"}");
      return;
    }
    try {
      var store = new SyncStore();
      var entry = store.findByName(name);
      if (entry.isEmpty()) {
        sendJson(ex, 404, "{\"error\":\"Sync not found: " + escapeJson(name) + "\"}");
        return;
      }
      var sync = entry.get();
      var resolver = new ConnProfileResolver(parent.configStore());
      var srcProfile = resolver.resolve(sync.getSourceConn());
      var tgtProfile = resolver.resolve(sync.getTargetConn());
      var businessEntity =
          BuiltinEntityRegistry.INSTANCE
              .findByName(sync.getEntityName())
              .orElseGet(() -> BusinessEntity.of(sync.getEntityName(), sync.getEntityName() + "s"));
      var masking = sync.isMaskPii() ? standardMasking() : MaskingConfig.none();
      var plan =
          ContextClonePlan.liveCloneWithOverrides(
              businessEntity,
              sync.getEntityId(),
              srcProfile,
              tgtProfile,
              sync.getDepth(),
              masking,
              io.recordrelay.core.clone.domain.FieldOverrideConfig.none(),
              io.recordrelay.core.clone.domain.ConflictResolution.REGENERATE_IDENTITIES,
              new io.recordrelay.cli.engine.SatelliteConfigResolver(parent.configStore(), resolver)
                  .configForEntity(businessEntity.name()));

      var report = DefaultContextCloneEngine.createDefault().cloneContext(plan, null);
      store.updateRunResult(name, java.time.Instant.now().toString(), "OK");

      sendJson(
          ex,
          200,
          "{"
              + jf("status", "ok")
              + ","
              + jf("sync", name)
              + ","
              + "\"records\":"
              + report.totalRecords()
              + ","
              + jf("duration", report.formattedDuration())
              + "}");
    } catch (Exception e) {
      try {
        new SyncStore().updateRunResult(name, java.time.Instant.now().toString(), "FAILED");
      } catch (Exception ignored) {
      }
      sendJson(ex, 500, "{\"error\":" + js(e.getMessage()) + "}");
    }
  }

  // ── POST /preset/run/{name} ───────────────────────────────────────────────

  private void handlePresetRun(HttpExchange ex) throws IOException {
    if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
      sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
      return;
    }
    var path = ex.getRequestURI().getPath();
    var name = path.substring("/preset/run/".length());
    if (name.isBlank()) {
      sendJson(ex, 400, "{\"error\":\"Preset name missing in path\"}");
      return;
    }
    try {
      var store = new PresetStore();
      var entry = store.findByName(name);
      if (entry.isEmpty()) {
        sendJson(ex, 404, "{\"error\":\"Preset not found: " + escapeJson(name) + "\"}");
        return;
      }
      var preset = entry.get();
      var resolver = new ConnProfileResolver(parent.configStore());
      var srcProfile = resolver.resolve(preset.getSourceConn());
      var tgtProfile = resolver.resolve(preset.getTargetConn());
      var businessEntity =
          BuiltinEntityRegistry.INSTANCE
              .findByName(preset.getEntityName())
              .orElseGet(
                  () -> BusinessEntity.of(preset.getEntityName(), preset.getEntityName() + "s"));
      var masking = preset.isMaskPii() ? standardMasking() : MaskingConfig.none();
      var plan =
          ContextClonePlan.liveCloneWithOverrides(
              businessEntity,
              preset.getEntityId(),
              srcProfile,
              tgtProfile,
              preset.getDepth(),
              masking,
              io.recordrelay.core.clone.domain.FieldOverrideConfig.none(),
              io.recordrelay.core.clone.domain.ConflictResolution.REGENERATE_IDENTITIES,
              new io.recordrelay.cli.engine.SatelliteConfigResolver(parent.configStore(), resolver)
                  .configForEntity(businessEntity.name()));

      var report = DefaultContextCloneEngine.createDefault().cloneContext(plan, null);

      sendJson(
          ex,
          200,
          "{"
              + jf("status", "ok")
              + ","
              + jf("preset", name)
              + ","
              + "\"records\":"
              + report.totalRecords()
              + ","
              + jf("duration", report.formattedDuration())
              + "}");
    } catch (Exception e) {
      sendJson(ex, 500, "{\"error\":" + js(e.getMessage()) + "}");
    }
  }

  // ── Shared helpers ────────────────────────────────────────────────────────

  private static MaskingConfig standardMasking() {
    return new MaskingConfig(
        List.of(
            new MaskingRule("email", MaskerType.EMAIL),
            new MaskingRule("phone", MaskerType.PHONE),
            new MaskingRule("phone_number", MaskerType.PHONE),
            new MaskingRule("address", MaskerType.ADDRESS),
            new MaskingRule("national_id", MaskerType.NATIONAL_ID),
            new MaskingRule("iban", MaskerType.IBAN)));
  }

  private static void sendJson(HttpExchange ex, int status, String body) throws IOException {
    var bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    ex.sendResponseHeaders(status, bytes.length);
    try (var os = ex.getResponseBody()) {
      os.write(bytes);
    }
  }

  private static String readBody(InputStream is) throws IOException {
    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
  }

  /** Naive JSON field extractor — works for simple flat string/int/bool fields. */
  private static String extractField(String json, String key) {
    var pattern = "\"" + key + "\"";
    int idx = json.indexOf(pattern);
    if (idx < 0) return null;
    int colon = json.indexOf(':', idx + pattern.length());
    if (colon < 0) return null;
    int start = json.indexOf('"', colon + 1);
    if (start < 0) return null;
    int end = json.indexOf('"', start + 1);
    if (end < 0) return null;
    return json.substring(start + 1, end);
  }

  private static int extractIntField(String json, String key, int defaultValue) {
    var pattern = "\"" + key + "\"";
    int idx = json.indexOf(pattern);
    if (idx < 0) return defaultValue;
    int colon = json.indexOf(':', idx + pattern.length());
    if (colon < 0) return defaultValue;
    int numStart = colon + 1;
    while (numStart < json.length()
        && (json.charAt(numStart) == ' ' || json.charAt(numStart) == '\n')) numStart++;
    int numEnd = numStart;
    while (numEnd < json.length()
        && (Character.isDigit(json.charAt(numEnd)) || json.charAt(numEnd) == '-')) numEnd++;
    try {
      return Integer.parseInt(json, numStart, numEnd, 10);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static boolean extractBoolField(String json, String key, boolean defaultValue) {
    var pattern = "\"" + key + "\"";
    int idx = json.indexOf(pattern);
    if (idx < 0) return defaultValue;
    int colon = json.indexOf(':', idx + pattern.length());
    if (colon < 0) return defaultValue;
    var after = json.substring(colon + 1).stripLeading();
    if (after.startsWith("true")) return true;
    if (after.startsWith("false")) return false;
    return defaultValue;
  }

  /** JSON field entry: {@code "key":"value"} */
  private static String jf(String key, String value) {
    return "\"" + key + "\":" + js(value);
  }

  /** JSON string literal (null-safe). */
  private static String js(String value) {
    if (value == null) return "null";
    return "\"" + escapeJson(value) + "\"";
  }

  private static String escapeJson(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }

  // Suppress SuppressWarnings for com.sun.net.httpserver — it is a supported
  // public API since Java 18 and available in all JDK distributions before that.
  @SuppressWarnings("restriction")
  private static Map<String, String> unusedImportSuppressor() {
    return Map.of();
  }
}
