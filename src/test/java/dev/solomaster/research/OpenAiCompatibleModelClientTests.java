package dev.solomaster.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleModelClientTests {

  private static final ModelClient.ModelPrompt PROMPT =
      new ModelClient.ModelPrompt("system", "user");
  private static final ResearchProperties.Provider FIRST =
      new ResearchProperties.Provider("first", "https://first.example/v1", "model-a", "key-1");
  private static final ResearchProperties.Provider SECOND =
      new ResearchProperties.Provider("second", "https://second.example/v1", "model-b", "key-2");

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

  @Test
  void returnsTheFirstProvidersReplyWithProvenance() {
    server
        .expect(requestTo("https://first.example/v1/chat/completions"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer key-1"))
        .andExpect(jsonPath("$.model").value("model-a"))
        .andExpect(jsonPath("$.messages[0].role").value("system"))
        .andExpect(jsonPath("$.messages[1].content").value("user"))
        .andRespond(reply("good"));

    var reply = client(FIRST, SECOND).complete(PROMPT, text -> true);

    assertThat(reply).isEqualTo(new ModelClient.ModelReply("good", "first", "model-a"));
    server.verify();
  }

  @Test
  void failsOverToTheNextProviderOnRateLimit() {
    server
        .expect(requestTo("https://first.example/v1/chat/completions"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    server
        .expect(requestTo("https://second.example/v1/chat/completions"))
        .andExpect(header("Authorization", "Bearer key-2"))
        .andRespond(reply("good"));

    assertThat(client(FIRST, SECOND).complete(PROMPT, text -> true).provider()).isEqualTo("second");
    server.verify();
  }

  @Test
  void retriesUnusableOutputOnceOnTheSameProviderThenFailsOver() {
    server.expect(requestTo("https://first.example/v1/chat/completions")).andRespond(reply("bad"));
    server.expect(requestTo("https://first.example/v1/chat/completions")).andRespond(reply("bad"));
    server
        .expect(requestTo("https://second.example/v1/chat/completions"))
        .andRespond(reply("good"));

    var reply = client(FIRST, SECOND).complete(PROMPT, "good"::equals);

    assertThat(reply.provider()).isEqualTo("second");
    server.verify();
  }

  @Test
  void acceptsAUsableRetryOnTheSameProvider() {
    server.expect(requestTo("https://first.example/v1/chat/completions")).andRespond(reply("bad"));
    server.expect(requestTo("https://first.example/v1/chat/completions")).andRespond(reply("good"));

    assertThat(client(FIRST, SECOND).complete(PROMPT, "good"::equals).provider())
        .isEqualTo("first");
    server.verify();
  }

  @Test
  void treatsMalformedResponsesAsUnusable() {
    server
        .expect(requestTo("https://first.example/v1/chat/completions"))
        .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://first.example/v1/chat/completions"))
        .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://second.example/v1/chat/completions"))
        .andRespond(reply("good"));

    assertThat(client(FIRST, SECOND).complete(PROMPT, text -> !text.isEmpty()).provider())
        .isEqualTo("second");
    server.verify();
  }

  @Test
  void failsLoudlyWhenEveryProviderIsExhausted() {
    server
        .expect(requestTo("https://first.example/v1/chat/completions"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    server
        .expect(requestTo("https://second.example/v1/chat/completions"))
        .andRespond(withServerError());

    assertThatThrownBy(() -> client(FIRST, SECOND).complete(PROMPT, text -> true))
        .isInstanceOf(ModelUnavailableException.class);
    server.verify();
  }

  @Test
  void skipsAProviderWithoutAnApiKey() {
    var keyless =
        new ResearchProperties.Provider("keyless", "https://first.example/v1", "model-a", " ");
    server
        .expect(requestTo("https://second.example/v1/chat/completions"))
        .andRespond(reply("good"));

    assertThat(client(keyless, SECOND).complete(PROMPT, text -> true).provider())
        .isEqualTo("second");
    server.verify();
  }

  @Test
  void refusesAPaidOpenRouterModel() {
    var paid =
        new ResearchProperties.Provider(
            "openrouter", "https://openrouter.ai/api/v1", "openai/gpt-5", "key");
    var free =
        new ResearchProperties.Provider(
            "openrouter", "https://openrouter.ai/api/v1", "google/gemma-4-31b-it:free", "key");

    assertThatThrownBy(() -> client(paid)).isInstanceOf(IllegalStateException.class);
    assertThat(client(free)).isNotNull();
  }

  private OpenAiCompatibleModelClient client(ResearchProperties.Provider... providers) {
    return new OpenAiCompatibleModelClient(List.of(providers), builder.build());
  }

  private static org.springframework.test.web.client.ResponseCreator reply(String content) {
    return withSuccess(
        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + content + "\"}}]}",
        MediaType.APPLICATION_JSON);
  }
}
