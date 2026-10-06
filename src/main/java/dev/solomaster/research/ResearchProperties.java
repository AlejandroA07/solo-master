package dev.solomaster.research;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Research module settings; secrets arrive through environment variables or local config. */
@ConfigurationProperties("solomaster.research")
record ResearchProperties(
    String vaultPath,
    Transcript transcript,
    YouTube youtube,
    Duration modelTimeout,
    List<Provider> providers) {

  /** The transcript tool: the command prefix the video ID is appended to, and its limits. */
  record Transcript(List<String> command, Duration timeout, int maxChars) {}

  /** The YouTube Data API, used only for a video's title, channel and publish date. */
  record YouTube(String baseUrl, String apiKey, Duration timeout) {}

  /** One OpenAI-compatible model provider, tried in list order (ADR-0001). */
  record Provider(String name, String baseUrl, String model, String apiKey) {}
}
