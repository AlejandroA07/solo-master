package dev.solomaster.research;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Recognizes YouTube video links and extracts a validated video ID. */
final class YouTubeLinks {

  private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
  private static final Set<String> WATCH_HOSTS =
      Set.of("youtube.com", "www.youtube.com", "m.youtube.com");
  private static final Set<String> PATH_PREFIXES = Set.of("/shorts/", "/live/", "/embed/");

  private YouTubeLinks() {}

  static boolean isVideoId(String candidate) {
    return candidate != null && VIDEO_ID.matcher(candidate).matches();
  }

  static Optional<String> videoId(String link) {
    URI uri;
    try {
      uri = new URI(link.strip());
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
    String scheme = uri.getScheme();
    String host = uri.getHost();
    String path = uri.getRawPath();
    if (scheme == null || host == null || path == null) {
      return Optional.empty();
    }
    if (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http")) {
      return Optional.empty();
    }
    host = host.toLowerCase(Locale.ROOT);
    String candidate = null;
    if (host.equals("youtu.be")) {
      candidate = path.substring(1);
    } else if (WATCH_HOSTS.contains(host)) {
      if (path.equals("/watch")) {
        candidate = queryParameter(uri.getRawQuery(), "v");
      } else {
        for (String prefix : PATH_PREFIXES) {
          if (path.startsWith(prefix)) {
            candidate = path.substring(prefix.length());
          }
        }
      }
    }
    return isVideoId(candidate) ? Optional.of(candidate) : Optional.empty();
  }

  private static String queryParameter(String rawQuery, String name) {
    if (rawQuery == null) {
      return null;
    }
    for (String pair : rawQuery.split("&")) {
      if (pair.startsWith(name + "=")) {
        return pair.substring(name.length() + 1);
      }
    }
    return null;
  }
}
