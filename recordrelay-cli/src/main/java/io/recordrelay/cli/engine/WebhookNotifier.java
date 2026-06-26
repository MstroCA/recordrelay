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
package io.recordrelay.cli.engine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Sends a JSON notification to a webhook URL after a clone or sync completes.
 *
 * <p>Uses the built-in {@link HttpClient} (Java 11+) — no external dependencies. Failures are
 * logged to stderr and never propagated, so a broken endpoint never aborts a clone.
 */
public final class WebhookNotifier {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private WebhookNotifier() {}

  /** Immutable payload for a webhook notification. */
  public record WebhookPayload(
      String event,
      String entity,
      String entityId,
      String source,
      String target,
      long records,
      long durationMs,
      String error) {

    /** Returns a success payload for a completed clone or sync. */
    public static WebhookPayload success(
        String event,
        String entity,
        String entityId,
        String source,
        String target,
        long records,
        long durationMs) {
      return new WebhookPayload(event, entity, entityId, source, target, records, durationMs, null);
    }

    /** Returns a failure payload. */
    public static WebhookPayload failure(
        String event, String entity, String entityId, String source, String target, String error) {
      return new WebhookPayload(event, entity, entityId, source, target, 0, 0, error);
    }
  }

  /**
   * Fire-and-forget POST to {@code url} with a JSON payload describing a completed operation.
   *
   * @param url the webhook URL (Slack incoming webhook, Teams connector, or custom endpoint)
   * @param payload the notification payload
   */
  public static void notify(String url, WebhookPayload payload) {
    if (url == null || url.isBlank()) {
      return;
    }
    var body = buildJson(payload);
    try {
      var client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
      var request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(TIMEOUT)
              .header("Content-Type", "application/json")
              .header("User-Agent", "RecordRelay-Webhook/1.0")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() >= 400) {
        System.err.println("[webhook] POST " + url + " returned HTTP " + response.statusCode());
      }
    } catch (Exception e) {
      System.err.println("[webhook] Failed to notify " + url + ": " + e.getMessage());
    }
  }

  /**
   * Convenience overload for Slack incoming webhooks — sends a human-readable {@code text} field
   * that Slack renders as a message alongside the raw JSON attachment.
   *
   * @param url Slack incoming webhook URL
   * @param payload the notification payload
   */
  public static void notifySlack(String url, WebhookPayload payload) {
    if (url == null || url.isBlank()) {
      return;
    }
    String statusEmoji = payload.error() == null ? ":white_check_mark:" : ":x:";
    String text =
        statusEmoji
            + " *RecordRelay* | "
            + payload.event()
            + " | `"
            + payload.entity()
            + " #"
            + payload.entityId()
            + "` from `"
            + payload.source()
            + "`"
            + (payload.target() != null ? " → `" + payload.target() + "`" : "")
            + " | "
            + payload.records()
            + " records | "
            + (payload.durationMs() / 1000.0)
            + "s"
            + (payload.error() != null ? "\nError: " + payload.error() : "");

    var slackBody =
        "{"
            + "\"text\":"
            + jsonString(text)
            + ","
            + "\"attachments\":[{\"color\":"
            + (payload.error() == null ? "\"good\"" : "\"danger\"")
            + ","
            + "\"text\":"
            + jsonString(buildJson(payload))
            + "}]}";
    try {
      var client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
      var request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(TIMEOUT)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(slackBody))
              .build();
      client.send(request, HttpResponse.BodyHandlers.discarding());
    } catch (Exception e) {
      System.err.println("[webhook] Slack notify failed: " + e.getMessage());
    }
  }

  private static String buildJson(WebhookPayload p) {
    return "{"
        + "\"event\":"
        + jsonString(p.event())
        + ","
        + "\"entity\":"
        + jsonString(p.entity())
        + ","
        + "\"entityId\":"
        + jsonString(p.entityId())
        + ","
        + "\"source\":"
        + jsonString(p.source())
        + ","
        + "\"target\":"
        + jsonString(p.target())
        + ","
        + "\"records\":"
        + p.records()
        + ","
        + "\"durationMs\":"
        + p.durationMs()
        + ","
        + "\"timestamp\":"
        + jsonString(Instant.now().toString())
        + ","
        + "\"error\":"
        + jsonString(p.error())
        + "}";
  }

  private static String jsonString(String value) {
    if (value == null) {
      return "null";
    }
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
  }
}
