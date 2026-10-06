package dev.solomaster.research;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Looks up a video's public title, channel and publish date through the official YouTube Data API
 * (ADR-0002, ADR-0004 Amendment 2). Optional: without a key, or on any failure, the brief is still
 * written without them. The key travels in a header, never the URL, and is never logged.
 */
class YouTubeVideoDetails {

  /** Untrusted text from YouTube, flattened to one line and capped; null when missing. */
  record VideoDetails(String title, String channel, LocalDate published) {}

  private static final Logger log = LoggerFactory.getLogger(YouTubeVideoDetails.class);
  private static final int MAX_CHANNEL_LENGTH = 100;
  private static final JsonMapper json = JsonMapper.builder().build();

  private final String baseUrl;
  private final String apiKey;
  private final RestClient http;

  YouTubeVideoDetails(String baseUrl, String apiKey, RestClient http) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.http = http;
  }

  Optional<VideoDetails> lookup(String videoId) {
    if (apiKey == null || apiKey.isBlank()) {
      return Optional.empty();
    }
    String id = YouTubeLinks.requireVideoId(videoId);
    URI uri =
        URI.create(
            baseUrl
                + "/videos?part=snippet&id="
                + id
                + "&fields=items(snippet(title,channelTitle,publishedAt))");
    String body;
    try {
      body =
          http.get()
              .uri(uri)
              .header("X-Goog-Api-Key", apiKey)
              .exchange(
                  (request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status != 200) {
                      log.warn("YouTube video details for {} returned HTTP {}", id, status);
                      return null;
                    }
                    return new String(response.getBody().readAllBytes(), UTF_8);
                  });
    } catch (RestClientException e) {
      log.warn("YouTube video details unreachable: {}", e.getClass().getSimpleName());
      return Optional.empty();
    }
    return body == null ? Optional.empty() : parse(body);
  }

  private static Optional<VideoDetails> parse(String body) {
    JsonNode snippet;
    try {
      snippet = json.readTree(body).path("items").path(0).path("snippet");
    } catch (JacksonException e) {
      return Optional.empty();
    }
    if (!snippet.isObject()) {
      return Optional.empty();
    }
    return Optional.of(
        new VideoDetails(
            text(snippet.path("title"), BriefService.MAX_TITLE_LENGTH),
            text(snippet.path("channelTitle"), MAX_CHANNEL_LENGTH),
            date(snippet.path("publishedAt"))));
  }

  private static String text(JsonNode node, int maxLength) {
    if (!node.isString()) {
      return null;
    }
    String value = node.asString().replaceAll("\\p{Cntrl}+", " ").strip();
    if (value.length() > maxLength) {
      value = value.substring(0, maxLength).strip();
    }
    return value.isEmpty() ? null : value;
  }

  private static LocalDate date(JsonNode node) {
    if (!node.isString()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(node.asString())
          .withOffsetSameInstant(ZoneOffset.UTC)
          .toLocalDate();
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
