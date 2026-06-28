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
package io.recordrelay.connector.bigquery;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.exception.ConnectorException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds a {@link BigQuery} client from a {@link ConnectionProfile}.
 *
 * <p>Credential resolution order:
 *
 * <ol>
 *   <li>If {@code credentials.password()} starts with {@code /} or {@code ~}, treat as a path to a
 *       service account JSON key file.
 *   <li>If {@code credentials.password()} starts with {@code {}, treat as inline JSON content.
 *   <li>Otherwise, fall back to Application Default Credentials.
 * </ol>
 */
final class BigQueryClientFactory {

  private BigQueryClientFactory() {}

  static BigQuery create(ConnectionProfile profile) throws ConnectorException {
    var projectId = profile.host();
    try {
      var creds = resolveCredentials(profile);
      var builder = BigQueryOptions.newBuilder().setProjectId(projectId);
      if (creds != null) {
        builder.setCredentials(creds);
      }
      return builder.build().getService();
    } catch (IOException e) {
      throw new ConnectorException(
          "Failed to build BigQuery client for project '" + projectId + "': " + e.getMessage(), e);
    }
  }

  private static GoogleCredentials resolveCredentials(ConnectionProfile profile)
      throws IOException {
    String credValue = profile.credentials().password();
    if (credValue == null || credValue.isBlank()) {
      // Use Application Default Credentials
      return null;
    }
    if (credValue.trim().startsWith("{")) {
      // Inline JSON
      var bytes = credValue.getBytes(StandardCharsets.UTF_8);
      return ServiceAccountCredentials.fromStream(new ByteArrayInputStream(bytes))
          .createScoped("https://www.googleapis.com/auth/cloud-platform");
    }
    // File path
    String path =
        credValue.startsWith("~")
            ? System.getProperty("user.home") + credValue.substring(1)
            : credValue;
    var bytes = Files.readAllBytes(Path.of(path));
    return ServiceAccountCredentials.fromStream(new ByteArrayInputStream(bytes))
        .createScoped("https://www.googleapis.com/auth/cloud-platform");
  }
}
