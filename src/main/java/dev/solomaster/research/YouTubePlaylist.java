package dev.solomaster.research;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the videos in Manuel's Unlisted "Research" playlist through the official YouTube Data API
 * with an API key and no OAuth (ADR-0002 Amendment 1, confirmed 2026-11-10). The key travels in a
 * header, never the URL, and is never logged. Everything in the response is untrusted.
 */
class YouTubePlaylist {

  /** A video in the playlist: a validated video ID and its capped, one-line title. */
  record Video(String videoId, String title) {}

  /** The playlist could not be read; the message is safe to show on the page. */
  static class UnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    UnavailableException(String message) {
      super(message);
    }
  }

  private static final Logger log = LoggerFactory.getLogger(YouTubePlaylist.class);
  private static final Pattern PLAYLIST_ID = Pattern.compile("[A-Za-z0-9_-]{10,64}");
  private static final Pattern PAGE_TOKEN = Pattern.compile("[A-Za-z0-9_-]{1,128}");
  private static final int PAGE_SIZE = 50;
  // 50 videos a page, so at most 200 videos and 4 quota units per Run now.
  static final int MAX_PAGES = 4;
  private static final JsonMapper json = JsonMapper.builder().build();

  private final String baseUrl;
  private final String apiKey;
  private final String playlistId;
  private final RestClient http;

  YouTubePlaylist(String baseUrl, String apiKey, String playlistId, RestClient http) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.playlistId = playlistId == null ? "" : playlistId.strip();
    this.http = http;
  }

  /** Null when the playlist can be read, otherwise what to set up, safe to show on the page. */
  String setupProblem() {
    if (playlistId.isEmpty()) {
      return "No playlist is configured. Set YOUTUBE_PLAYLIST_ID to your Research playlist's ID.";
    }
    if (!PLAYLIST_ID.matcher(playlistId).matches()) {
      return "YOUTUBE_PLAYLIST_ID is not a playlist ID. Use the part after list= in its URL.";
    }
    if (apiKey == null || apiKey.isBlank()) {
      return "No YouTube API key is configured. Set YOUTUBE_API_KEY to read the playlist.";
    }
    return null;
  }

  /** The playlist's videos in playlist order, up to {@link #MAX_PAGES} pages. */
  List<Video> videos() {
    if (setupProblem() != null) {
      throw new IllegalStateException("Playlist is not configured");
    }
    List<Video> videos = new ArrayList<>();
    String pageToken = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      JsonNode body = fetchPage(pageToken);
      for (JsonNode item : body.path("items")) {
        JsonNode snippet = item.path("snippet");
        JsonNode videoId = snippet.path("resourceId").path("videoId");
        if (videoId.isString() && YouTubeLinks.isVideoId(videoId.asString())) {
          String title = YouTubeVideoDetails.text(snippet.path("title"), 200);
          videos.add(
              new Video(
                  YouTubeLinks.requireVideoId(videoId.asString()),
                  title == null ? "YouTube video " + videoId.asString() : title));
        }
      }
      JsonNode next = body.path("nextPageToken");
      if (!next.isString() || !PAGE_TOKEN.matcher(next.asString()).matches()) {
        break;
      }
      pageToken = next.asString();
    }
    return videos;
  }

  private JsonNode fetchPage(String pageToken) {
    URI uri =
        URI.create(
            baseUrl
                + "/playlistItems?part=snippet&maxResults="
                + PAGE_SIZE
                + "&playlistId="
                + playlistId
                + "&fields=nextPageToken,items(snippet(title,resourceId(videoId)))"
                + (pageToken == null ? "" : "&pageToken=" + pageToken));
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
                      log.warn("YouTube playlist returned HTTP {}", status);
                      throw new UnavailableException(describe(status));
                    }
                    return new String(response.getBody().readAllBytes(), UTF_8);
                  });
    } catch (RestClientException e) {
      log.warn("YouTube playlist unreachable: {}", e.getClass().getSimpleName());
      throw new UnavailableException("Couldn't reach YouTube to read the playlist. Try again.");
    }
    try {
      return json.readTree(body);
    } catch (JacksonException e) {
      throw new UnavailableException("YouTube sent an unreadable playlist reply. Try again later.");
    }
  }

  private static String describe(int status) {
    return switch (status) {
      case 403 ->
          "YouTube refused to read the playlist (HTTP 403). Check that it is Unlisted, not"
              + " Private, and that the API key allows the YouTube Data API.";
      case 404 -> "YouTube can't find the playlist (HTTP 404). Check YOUTUBE_PLAYLIST_ID.";
      case 429 -> "YouTube's daily quota is used up (HTTP 429). Try again tomorrow.";
      default -> "YouTube couldn't read the playlist (HTTP " + status + "). Try again later.";
    };
  }
}
