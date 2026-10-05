package dev.solomaster.research;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Chat completions against OpenAI-compatible endpoints with ordered failover (ADR-0001, ADR-0005).
 * A busy provider (429 or 503) is retried after a short pause, then hands over to the next, as does
 * any other failure; unusable output is retried once on the same provider first. Logs name the
 * provider, status, and the provider's own error message, never keys or prompt text.
 */
class OpenAiCompatibleModelClient implements ModelClient {

  private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleModelClient.class);
  private static final int ATTEMPTS_PER_PROVIDER = 2;
  private static final List<Duration> BUSY_RETRY_PAUSES =
      List.of(Duration.ofSeconds(2), Duration.ofSeconds(5));
  private static final int MAX_ERROR_DETAIL_CHARS = 300;

  /** Waits between busy retries; tests pass a recorder instead of sleeping. */
  @FunctionalInterface
  interface Pause {
    void await(Duration duration) throws InterruptedException;
  }

  private final List<ResearchProperties.Provider> providers;
  private final RestClient http;
  private final Pause pause;
  private static final JsonMapper json = JsonMapper.builder().build();

  OpenAiCompatibleModelClient(
      List<ResearchProperties.Provider> providers, RestClient http, Pause pause) {
    for (ResearchProperties.Provider provider : providers) {
      requireFreeOpenRouterModel(provider);
    }
    this.providers = List.copyOf(providers);
    this.http = http;
    this.pause = pause;
  }

  @Override
  public ModelReply complete(ModelPrompt prompt, Predicate<String> usable) {
    for (ResearchProperties.Provider provider : providers) {
      if (provider.apiKey() == null || provider.apiKey().isBlank()) {
        log.warn("Model provider {} skipped: no API key configured", provider.name());
        continue;
      }
      for (int attempt = 1; attempt <= ATTEMPTS_PER_PROVIDER; attempt++) {
        Optional<String> text = call(provider, prompt);
        if (text.isEmpty()) {
          break;
        }
        if (usable.test(text.get())) {
          return new ModelReply(text.get(), provider.name(), provider.model());
        }
        log.warn("Model provider {} gave unusable output (attempt {})", provider.name(), attempt);
      }
    }
    throw new ModelUnavailableException();
  }

  /** Returns the reply text, or empty when this provider failed and the next should be tried. */
  private Optional<String> call(ResearchProperties.Provider provider, ModelPrompt prompt) {
    for (int busyRetry = 0; ; busyRetry++) {
      Optional<Response> response = send(provider, prompt);
      if (response.isEmpty()) {
        return Optional.empty();
      }
      int status = response.get().status();
      if (status == 200) {
        return Optional.of(replyText(response.get().body()));
      }
      log.warn(
          "Model provider {} ({}) returned HTTP {}: {}",
          provider.name(),
          provider.model(),
          status,
          errorDetail(response.get().body(), provider.apiKey()));
      boolean busy = status == 429 || status == 503;
      if (!busy || busyRetry == BUSY_RETRY_PAUSES.size()) {
        return Optional.empty();
      }
      try {
        pause.await(BUSY_RETRY_PAUSES.get(busyRetry));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return Optional.empty();
      }
    }
  }

  private record Response(int status, String body) {}

  /** Sends one request; empty when the provider could not be reached at all. */
  private Optional<Response> send(ResearchProperties.Provider provider, ModelPrompt prompt) {
    String body =
        json.writeValueAsString(
            Map.of(
                "model",
                provider.model(),
                "messages",
                List.of(
                    Map.of("role", "system", "content", prompt.system()),
                    Map.of("role", "user", "content", prompt.user()))));
    try {
      return http.post()
          .uri(URI.create(provider.baseUrl() + "/chat/completions"))
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.apiKey())
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .exchange(
              (request, response) ->
                  Optional.of(
                      new Response(
                          response.getStatusCode().value(),
                          new String(response.getBody().readAllBytes(), UTF_8))));
    } catch (RestClientException e) {
      log.warn("Model provider {} unreachable: {}", provider.name(), e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  /**
   * The provider's own explanation of a failed request, safe to log: OpenRouter's relayed upstream
   * message when present, else the error message (Gemini wraps it in an array). The API key is
   * redacted, control characters are flattened, and the result is capped; unreadable bodies give an
   * empty string.
   */
  static String errorDetail(String responseBody, String apiKey) {
    JsonNode root;
    try {
      root = json.readTree(responseBody);
    } catch (JacksonException e) {
      return "";
    }
    JsonNode error = (root.isArray() ? root.path(0) : root).path("error");
    JsonNode upstream = error.path("metadata").path("raw");
    JsonNode message = upstream.isString() ? upstream : error.path("message");
    if (!message.isString()) {
      return "";
    }
    String detail = message.asString();
    if (apiKey != null && !apiKey.isBlank()) {
      detail = detail.replace(apiKey, "[redacted]");
    }
    detail = detail.replaceAll("\\p{Cntrl}+", " ").strip();
    return detail.length() > MAX_ERROR_DETAIL_CHARS
        ? detail.substring(0, MAX_ERROR_DETAIL_CHARS)
        : detail;
  }

  /** Extracts the first choice's content; malformed JSON yields empty text, which is unusable. */
  private String replyText(String responseBody) {
    try {
      JsonNode content =
          json.readTree(responseBody).path("choices").path(0).path("message").path("content");
      return content.isString() ? content.asString() : "";
    } catch (JacksonException e) {
      return "";
    }
  }

  private static void requireFreeOpenRouterModel(ResearchProperties.Provider provider) {
    String host = URI.create(provider.baseUrl()).getHost();
    if ("openrouter.ai".equals(host) && !provider.model().endsWith(":free")) {
      throw new IllegalStateException(
          "OpenRouter model IDs must be :free models (ADR-0001): " + provider.model());
    }
  }
}
