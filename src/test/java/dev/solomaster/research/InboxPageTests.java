package dev.solomaster.research;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/** Run now through the real running app, with the playlist, captions and model faked. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InboxPageTests {

  @TempDir static Path vaultDir;
  @TempDir static Path dbDir;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("solomaster.research.vault-path", vaultDir::toString);
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbDir.resolve("inbox-test.db"));
  }

  static final String NO_CAPTIONS = "nocaptions0";

  @TestConfiguration
  static class Fakes {
    @Bean
    @Primary
    FakePlaylist fakePlaylist() {
      return new FakePlaylist();
    }

    @Bean
    @Primary
    FakeModel fakeModel() {
      return new FakeModel();
    }

    @Bean
    @Primary
    TranscriptFetcher fakeTranscriptFetcher() {
      return new TranscriptFetcher(List.of("unused"), Duration.ZERO, 0) {
        @Override
        String fetch(String videoId) {
          if (videoId.equals(NO_CAPTIONS)) {
            throw new TranscriptUnavailableException(
                TranscriptUnavailableException.Reason.NO_CAPTIONS);
          }
          return "[00:01] Captions for " + videoId;
        }
      };
    }

    @Bean
    @Primary
    YouTubeVideoDetails fakeVideoDetails() {
      return new YouTubeVideoDetails("https://unused.example", "", RestClient.create());
    }

    /** Runs synchronously, so a test sees the finished run as soon as Run now returns. */
    @Bean
    @Primary
    InboxRun synchronousInboxRun(
        FakePlaylist playlist, RunItems items, BriefService briefs, ResearchVault vault) {
      return new InboxRun(playlist, items, briefs, vault, Runnable::run, Clock.systemUTC());
    }
  }

  static class FakePlaylist extends YouTubePlaylist {
    final List<Video> videos = new ArrayList<>();
    final AtomicInteger reads = new AtomicInteger();
    volatile String setupProblem;

    FakePlaylist() {
      super("https://unused.example", "unused", "PLunused_0000", RestClient.create());
    }

    @Override
    String setupProblem() {
      return setupProblem;
    }

    @Override
    List<Video> videos() {
      reads.incrementAndGet();
      return List.copyOf(videos);
    }
  }

  static class FakeModel implements ModelClient {
    final AtomicBoolean down = new AtomicBoolean();
    final AtomicInteger calls = new AtomicInteger();

    @Override
    public ModelReply complete(ModelPrompt prompt, Predicate<String> usable) {
      calls.incrementAndGet();
      if (down.get()) {
        throw new ModelUnavailableException(
            List.of(
                "model-a: out of free quota or rate limited (HTTP 429)",
                "model-b: unreachable (network error or timeout)"));
      }
      return new ModelReply(BriefPromptTests.COMPLETE_BRIEF, "fake", "fake-model");
    }
  }

  @LocalServerPort private int port;
  @Autowired private FakePlaylist playlist;
  @Autowired private FakeModel model;
  @Autowired private JdbcClient db;

  @BeforeEach
  void reset() throws IOException {
    db.sql("DELETE FROM run_item").update();
    playlist.videos.clear();
    playlist.reads.set(0);
    playlist.setupProblem = null;
    model.down.set(false);
    model.calls.set(0);
    for (String folder : List.of("Briefs", "Sources")) {
      Path notes = vaultDir.resolve("SoloMaster/Research/" + folder);
      if (Files.isDirectory(notes)) {
        try (Stream<Path> files = Files.list(notes)) {
          for (Path note : files.toList()) {
            Files.delete(note);
          }
        }
      }
    }
  }

  @Test
  void showsAnEmptyInbox() {
    String page = page();

    assertThat(page)
        .contains("data-testid=\"inbox-heading\"")
        .contains("data-testid=\"inbox-empty\"")
        .contains("data-testid=\"run-now\"")
        .contains("href=\"/research/inbox\"");
  }

  @Test
  void writesABriefForEveryNewPlaylistVideo() throws IOException {
    video("aaaaaaaaaaa", "Loops in Java");
    video("bbbbbbbbbbb", "Records explained");

    Result result = runNow(null);

    assertThat(result.status()).isEqualTo(303);
    assertThat(result.location()).endsWith("/research/inbox");
    assertThat(briefNames()).hasSize(2);
    assertThat(statuses()).containsOnly(RunItems.DONE);
    String brief = Files.readString(briefFor("aaaaaaaaaaa"), UTF_8);
    assertThat(brief)
        .contains("source_url: \"https://www.youtube.com/watch?v=aaaaaaaaaaa\"")
        .contains("status: unreviewed");
    assertThat(page())
        .contains("Loops in Java")
        .contains("Records explained")
        .contains("Briefed")
        .contains("href=\"/research/briefs/" + briefFor("aaaaaaaaaaa").getFileName() + "\"");
  }

  @Test
  void neverBriefsAVideoTwice() throws IOException {
    Path briefs = Files.createDirectories(vaultDir.resolve("SoloMaster/Research/Briefs"));
    Files.writeString(
        briefs.resolve("2026-11-01-already.md"),
        "---\nsource_url: \"https://youtu.be/aaaaaaaaaaa\"\n---\n",
        UTF_8);
    video("aaaaaaaaaaa", "Briefed by hand");
    video("bbbbbbbbbbb", "New one");

    runNow(null);
    runNow(null);

    assertThat(model.calls.get()).isEqualTo(1);
    assertThat(briefNames()).hasSize(2);
    assertThat(statuses()).containsOnly(RunItems.DONE);
    assertThat(page()).contains("href=\"/research/briefs/2026-11-01-already.md\"");
  }

  @Test
  void keepsTheQueueWhenNoModelCanAnswerAndRetriesOnTheNextRun() {
    video("aaaaaaaaaaa", "First");
    video("bbbbbbbbbbb", "Second");
    model.down.set(true);

    runNow(null);

    assertThat(model.calls.get()).isEqualTo(1);
    assertThat(briefNames()).isEmpty();
    assertThat(statuses()).containsExactly(RunItems.FAILED, RunItems.QUEUED);
    assertThat(page())
        .contains("data-testid=\"inbox-last-problem\"")
        .contains("midnight Pacific")
        .contains("model-a: out of free quota or rate limited (HTTP 429)")
        .contains("model-b: unreachable (network error or timeout)")
        .contains("Tried 1 time(s)");

    model.down.set(false);
    runNow(null);

    assertThat(briefNames()).hasSize(2);
    assertThat(statuses()).containsOnly(RunItems.DONE);
    assertThat(page()).doesNotContain("data-testid=\"inbox-last-problem\"");
  }

  @Test
  void recordsAVideoWithoutCaptionsAndCarriesOn() {
    video(NO_CAPTIONS, "Silent video");
    video("bbbbbbbbbbb", "Has captions");

    runNow(null);

    assertThat(statuses()).containsExactly(RunItems.FAILED, RunItems.DONE);
    assertThat(page()).contains("Couldn&#39;t get captions for this video");
  }

  @Test
  void writesAtMostTenBriefsPerRun() {
    for (int i = 0; i < InboxRun.MAX_ITEMS_PER_RUN + 2; i++) {
      video(String.format("video%06d", i), "Video " + i);
    }

    runNow(null);

    assertThat(briefNames()).hasSize(InboxRun.MAX_ITEMS_PER_RUN);
    assertThat(statuses()).filteredOn(RunItems.QUEUED::equals).hasSize(2);
  }

  @Test
  void saysWhatToSetUpAndDoesNotRunWithoutAPlaylist() {
    playlist.setupProblem = "No playlist is configured. Set YOUTUBE_PLAYLIST_ID.";
    video("aaaaaaaaaaa", "Never read");

    Result result = runNow(null);

    assertThat(result.status()).isEqualTo(303);
    assertThat(playlist.reads.get()).isZero();
    assertThat(page())
        .contains("data-testid=\"inbox-setup-problem\"")
        .contains("YOUTUBE_PLAYLIST_ID")
        .contains("disabled");
  }

  @Test
  void showsAPlaylistErrorWithoutWritingAnything() {
    InboxRun run =
        new InboxRun(
            new FakePlaylist() {
              @Override
              List<Video> videos() {
                throw new UnavailableException("YouTube can't find the playlist (HTTP 404).");
              }
            },
            new RunItems(db),
            null,
            new ResearchVault(vaultDir.toString()),
            Runnable::run,
            Clock.systemUTC());

    assertThat(run.start()).isTrue();

    assertThat(run.lastProblem()).contains("HTTP 404");
    assertThat(run.isRunning()).isFalse();
    assertThat(statuses()).isEmpty();
  }

  @Test
  void rejectsACrossSiteRunNow() {
    video("aaaaaaaaaaa", "Not from another site");

    Result result = runNow("https://evil.example");

    assertThat(result.status()).isEqualTo(403);
    assertThat(playlist.reads.get()).isZero();
    assertThat(statuses()).isEmpty();
  }

  private void video(String id, String title) {
    playlist.videos.add(new YouTubePlaylist.Video(id, title));
  }

  private List<String> statuses() {
    return db.sql("SELECT status FROM run_item ORDER BY id").query(String.class).list();
  }

  private List<String> briefNames() {
    Path briefs = vaultDir.resolve("SoloMaster/Research/Briefs");
    if (!Files.isDirectory(briefs)) {
      return List.of();
    }
    try (Stream<Path> notes = Files.list(briefs)) {
      return notes.map(note -> note.getFileName().toString()).toList();
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  private Path briefFor(String videoId) {
    Optional<String> name =
        db.sql("SELECT brief_name FROM run_item WHERE item_key = ?")
            .params(videoId)
            .query(String.class)
            .optional();
    return vaultDir.resolve("SoloMaster/Research/Briefs/" + name.orElseThrow());
  }

  private record Result(int status, String location) {}

  private Result runNow(String origin) {
    return http()
        .post()
        .uri("/research/inbox/run")
        .headers(
            headers -> {
              if (origin != null) {
                headers.add("Origin", origin);
              }
            })
        .exchange(
            (request, response) ->
                new Result(
                    response.getStatusCode().value(), response.getHeaders().getFirst("Location")));
  }

  private String page() {
    return http().get().uri("/research/inbox").retrieve().body(String.class);
  }

  private RestClient http() {
    return RestClient.create("http://localhost:" + port);
  }
}
