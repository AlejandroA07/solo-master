package dev.solomaster.research;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
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
 * A failed or rate-limited provider hands over to the next; unusable output is retried once on the
 * same provider first. Logs name the provider and status only, never keys or prompt text.
 */
class OpenAiCompatibleModelClient implements ModelClient {

  private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleModelClient.class);
  private static final int ATTEMPTS_PER_PROVIDER = 2;

  private final List<ResearchProperties.Provider> providers;
  private final RestClient http;
  private final JsonMapper json = JsonMapper.builder().build();

  OpenAiCompatibleModelClient(List<ResearchProperties.Provider> providers, RestClient http) {
    for (ResearchProperties.Provider provider : providers) {
      requireFreeOpenRouterModel(provider);
    }
    this.providers = List.copyOf(providers);
    this.http = http;
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
              (request, response) -> {
                int status = response.getStatusCode().value();
                String responseBody = new String(response.getBody().readAllBytes(), UTF_8);
                if (status != 200) {
                  log.warn("Model provider {} returned HTTP {}", provider.name(), status);
                  return Optional.empty();
                }
                return Optional.of(replyText(responseBody));
              });
    } catch (RestClientException e) {
      log.warn("Model provider {} unreachable: {}", provider.name(), e.getClass().getSimpleName());
      return Optional.empty();
    }
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
