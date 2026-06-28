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
package io.recordrelay.cli.registry;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedded HTTP package registry server for {@code rr pkg serve}.
 *
 * <p>API:
 *
 * <ul>
 *   <li>{@code GET /api/packages} — JSON list of {@code "name:tag"} strings
 *   <li>{@code GET /api/packages/{name}/{tag}} — stream .rrpkg file
 *   <li>{@code PUT /api/packages/{name}/{tag}} — save .rrpkg file
 * </ul>
 */
public final class PackageRegistryServer {

  private static final Logger LOG = LoggerFactory.getLogger(PackageRegistryServer.class);

  private final int port;
  private final LocalRegistryClient registry;
  private HttpServer server;

  public PackageRegistryServer(int port, Path storeDir) {
    this.port = port;
    this.registry = new LocalRegistryClient(storeDir);
  }

  /** Starts the server; returns immediately (non-blocking). */
  public void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(port), 0);
    server.setExecutor(Executors.newCachedThreadPool());
    server.createContext("/api/packages", this::handlePackages);
    server.start();
    LOG.info("Package registry listening on port {}", port);
  }

  /** Stops the server gracefully. */
  public void stop() {
    if (server != null) server.stop(1);
  }

  private void handlePackages(HttpExchange ex) throws IOException {
    var path = ex.getRequestURI().getPath();
    var parts = path.split("/"); // ["", "api", "packages"] or ["", "api", "packages", name, tag]
    try {
      if ("GET".equals(ex.getRequestMethod()) && parts.length == 3) {
        handleList(ex);
      } else if ("GET".equals(ex.getRequestMethod()) && parts.length == 5) {
        handleGet(ex, parts[3], parts[4]);
      } else if ("PUT".equals(ex.getRequestMethod()) && parts.length == 5) {
        handlePut(ex, parts[3], parts[4]);
      } else {
        respond(ex, 404, "Not found");
      }
    } catch (Exception e) {
      LOG.warn("Registry request failed", e);
      respond(ex, 500, e.getMessage());
    }
  }

  private void handleList(HttpExchange ex) throws Exception {
    var packages = registry.list();
    var sb = new StringBuilder("[");
    for (int i = 0; i < packages.size(); i++) {
      if (i > 0) sb.append(",");
      sb.append("\"").append(packages.get(i)).append("\"");
    }
    sb.append("]");
    var body = sb.toString().getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().set("Content-Type", "application/json");
    ex.sendResponseHeaders(200, body.length);
    try (var out = ex.getResponseBody()) {
      out.write(body);
    }
  }

  private void handleGet(HttpExchange ex, String name, String tag) throws Exception {
    var tmpDir = Files.createTempDirectory("rr-pkg-serve");
    try {
      var file = registry.pull(name, tag, tmpDir);
      var bytes = Files.readAllBytes(file);
      ex.getResponseHeaders().set("Content-Type", "application/octet-stream");
      ex.getResponseHeaders()
          .set("Content-Disposition", "attachment; filename=\"" + name + "-" + tag + ".rrpkg\"");
      ex.sendResponseHeaders(200, bytes.length);
      try (var out = ex.getResponseBody()) {
        out.write(bytes);
      }
    } finally {
      try (var s = Files.list(tmpDir)) {
        s.forEach(
            p -> {
              try {
                Files.deleteIfExists(p);
              } catch (IOException ignored) {
              }
            });
      }
      Files.deleteIfExists(tmpDir);
    }
  }

  private void handlePut(HttpExchange ex, String name, String tag) throws Exception {
    var tmpFile = Files.createTempFile("rr-pkg-upload", ".rrpkg");
    try {
      try (var in = ex.getRequestBody()) {
        Files.write(tmpFile, in.readAllBytes());
      }
      registry.push(name, tag, tmpFile);
    } finally {
      Files.deleteIfExists(tmpFile);
    }
    respond(ex, 201, "Created");
  }

  private static void respond(HttpExchange ex, int status, String msg) throws IOException {
    var body = (msg == null ? "" : msg).getBytes(StandardCharsets.UTF_8);
    ex.sendResponseHeaders(status, body.length);
    try (var out = ex.getResponseBody()) {
      out.write(body);
    }
  }
}
