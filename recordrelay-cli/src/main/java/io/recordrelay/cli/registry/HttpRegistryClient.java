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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP-backed registry client that speaks to a {@link PackageRegistryServer} (or compatible API).
 *
 * <p>Endpoints used:
 *
 * <ul>
 *   <li>{@code GET /api/packages} — list (JSON array of {@code "name:tag"} strings)
 *   <li>{@code GET /api/packages/{name}/{tag}} — download binary
 *   <li>{@code PUT /api/packages/{name}/{tag}} — upload binary
 * </ul>
 */
public final class HttpRegistryClient implements RegistryClient {

  private final String baseUrl;
  private final HttpClient http = HttpClient.newHttpClient();

  public HttpRegistryClient(String baseUrl) {
    this.baseUrl = baseUrl.replaceAll("/$", "");
  }

  @Override
  public void push(String name, String tag, Path pkgFile) throws Exception {
    var req =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/packages/" + name + "/" + tag))
            .PUT(HttpRequest.BodyPublishers.ofFile(pkgFile))
            .header("Content-Type", "application/octet-stream")
            .build();
    var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() != 200 && resp.statusCode() != 201)
      throw new IOException("Push failed: HTTP " + resp.statusCode());
  }

  @Override
  public Path pull(String name, String tag, Path outputDir) throws Exception {
    Files.createDirectories(outputDir);
    var dest = outputDir.resolve(name + "-" + tag + ".rrpkg");
    var req =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/packages/" + name + "/" + tag))
            .GET()
            .build();
    var resp = http.send(req, HttpResponse.BodyHandlers.ofFile(dest));
    if (resp.statusCode() != 200) throw new IOException("Pull failed: HTTP " + resp.statusCode());
    return dest;
  }

  @Override
  public List<String> list() throws Exception {
    var req =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/packages"))
            .GET()
            .header("Accept", "application/json")
            .build();
    var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() != 200) throw new IOException("List failed: HTTP " + resp.statusCode());
    var body = resp.body().trim();
    if (body.equals("[]")) return List.of();
    var result = new ArrayList<String>();
    body = body.replace("[", "").replace("]", "");
    for (var item : body.split(",")) {
      var s = item.trim().replace("\"", "");
      if (!s.isEmpty()) result.add(s);
    }
    return result;
  }
}
