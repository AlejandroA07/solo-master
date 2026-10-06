package dev.solomaster.research;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResearchPageTests {

  @TempDir static Path vaultDir;

  @DynamicPropertySource
  static void vault(DynamicPropertyRegistry registry) {
    registry.add("solomaster.research.vault-path", vaultDir::toString);
  }

  @TestConfiguration
  static class FakeModel {
    @Bean
    @Primary
    RecordingModelClient recordingModelClient() {
      return new RecordingModelClient();
    }
  }

  /** Returns a complete brief without touching the network, and records the last prompt. */
  static class RecordingModelClient implements ModelClient {
    final AtomicReference<ModelPrompt> lastPrompt = new AtomicReference<>();

    @Override
    public ModelReply complete(ModelPrompt prompt, java.util.function.Predicate<String> usable) {
      if (prompt.user().contains("Title: model-down")) {
        throw new ModelUnavailableException();
      }
      lastPrompt.set(prompt);
      assertThat(usable.test(BriefPromptTests.COMPLETE_BRIEF)).isTrue();
      return new ModelReply(BriefPromptTests.COMPLETE_BRIEF, "fake", "fake-model");
    }
  }

  @LocalServerPort private int port;
  @Autowired private RecordingModelClient model;

  @Test
  void showsTheNewResearchForm() {
    var response = http().get().uri("/research/new").retrieve().toEntity(String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody())
        .contains("data-testid=\"research-heading\"")
        .contains("name=\"link\"")
        .contains("name=\"text\"");
  }

  @Test
  void turnsPastedTextIntoAnUnreviewedBriefInTheVault() throws IOException {
    Result created =
        post(
            form("https://example.com/article", "Loops and boundaries", "For loops end early."),
            null);

    assertThat(created.status()).isEqualTo(302);
    String name = LocalDate.now() + "-loops-and-boundaries.md";
    assertThat(created.location()).endsWith("/research/briefs/" + name);

    String brief = Files.readString(vaultDir.resolve("SoloMaster/Research/Briefs/" + name), UTF_8);
    assertThat(brief)
        .startsWith("---\n")
        .contains("source_url: \"https://example.com/article\"")
        .contains("type: text")
        .contains("transcript_source: pasted")
        .contains("content_hash: \"sha256:")
        .contains("provider: \"fake\"")
        .contains("model: \"fake-model\"")
        .contains("prompt_version: brief-v1")
        .contains("status: unreviewed")
        .contains(
            "# Loops and boundaries\n\nTranscript: [[SoloMaster/Research/Sources/"
                + name.replace(".md", "")
                + "|full transcript]]\n\n## In short");
    assertThat(Files.readString(vaultDir.resolve("SoloMaster/Research/Sources/" + name), UTF_8))
        .contains("For loops end early.")
        .contains("brief: \"SoloMaster/Research/Briefs/" + name + "\"");
    assertThat(model.lastPrompt.get().user())
        .contains("# My setup")
        .contains("<source>\nFor loops end early.\n</source>");

    var page = http().get().uri("/research/briefs/" + name).retrieve().toEntity(String.class);
    assertThat(page.getBody())
        .contains("status: unreviewed")
        .contains("href=\"obsidian://open?vault=");
  }

  @Test
  void escapesSourceControlledTextOnTheBriefPage() {
    Result created = post(form("", "<script>alert(1)</script>", "text"), null);

    var page = http().get().uri(created.location()).retrieve().toEntity(String.class);
    assertThat(page.getBody())
        .doesNotContain("<script>alert(1)</script>")
        .contains("&lt;script&gt;");
  }

  @Test
  void rejectsANonYouTubeLinkWithoutPastedText() {
    Result result = post(form("https://example.com/article", "", ""), null);

    assertThat(result.status()).isEqualTo(400);
    assertThat(result.body()).contains("Only YouTube links are fetched");
  }

  @Test
  void rejectsAnEmptySubmission() {
    Result result = post(form("", "", "   "), null);

    assertThat(result.status()).isEqualTo(400);
    assertThat(result.body()).contains("Give a YouTube link or paste the source text.");
  }

  @Test
  void rejectsOversizedText() {
    Result result = post(form("", "", "x".repeat(BriefService.MAX_TEXT_LENGTH + 1)), null);

    assertThat(result.status()).isEqualTo(400);
    assertThat(result.body()).contains("too long");
  }

  @Test
  void saysSoWhenNoModelIsAvailable() {
    Result result = post(form("", "model-down", "text"), null);

    assertThat(result.status()).isEqualTo(503);
    assertThat(result.body()).contains("No model is available right now");
  }

  @Test
  void rejectsACrossSitePostAndWritesNothing() throws IOException {
    Result result = post(form("", "cross-site-attempt", "text"), "https://evil.example");

    assertThat(result.status()).isEqualTo(403);
    try (Stream<Path> files = Files.walk(vaultDir)) {
      assertThat(files.map(Path::toString)).noneMatch(path -> path.contains("cross-site-attempt"));
    }
  }

  @Test
  void answersUnknownOrMalformedBriefNamesWithAnError() {
    assertThat(status("/research/briefs/2020-01-01-missing.md")).isEqualTo(404);
    assertThat(status("/research/briefs/..%2F..%2Fsecret.md")).isBetween(400, 404);
    assertThat(status("/research/briefs/notes.md")).isEqualTo(404);
  }

  private record Result(int status, String location, String body) {}

  private static MultiValueMap<String, String> form(String link, String title, String text) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("link", link);
    form.add("title", title);
    form.add("text", text);
    return form;
  }

  private Result post(MultiValueMap<String, String> form, String origin) {
    return http()
        .post()
        .uri("/research")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .headers(
            headers -> {
              if (origin != null) {
                headers.add("Origin", origin);
              }
            })
        .body(form)
        .exchange(
            (request, response) ->
                new Result(
                    response.getStatusCode().value(),
                    response.getHeaders().getFirst("Location"),
                    new String(response.getBody().readAllBytes(), UTF_8)));
  }

  private int status(String path) {
    return http()
        .get()
        .uri(URI.create("http://localhost:" + port + path))
        .exchange((request, response) -> response.getStatusCode().value());
  }

  private RestClient http() {
    return RestClient.create("http://localhost:" + port);
  }
}
