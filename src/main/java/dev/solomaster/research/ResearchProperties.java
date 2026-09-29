package dev.solomaster.research;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Research module settings; secrets arrive through environment variables or local config. */
@ConfigurationProperties("solomaster.research")
record ResearchProperties(
    String vaultPath, Transcript transcript, Duration modelTimeout, List<Provider> providers) {

  /** The transcript tool: the command prefix the video ID is appended to, and its limits. */
  record Transcript(List<String> command, Duration timeout, int maxChars) {}

  /** One OpenAI-compatible model provider, tried in list order (ADR-0001). */
  record Provider(String name, String baseUrl, String model, String apiKey) {}
}
