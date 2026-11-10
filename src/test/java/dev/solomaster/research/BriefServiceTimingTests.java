package dev.solomaster.research;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

class BriefServiceTimingTests {

  @TempDir Path vaultDir;

  @Test
  void recordsHowManySecondsTheBriefTookToWrite() throws IOException {
    Instant pressed = Instant.parse("2026-11-10T09:00:00.400Z");
    Instant written = pressed.plusSeconds(42).plusMillis(300);
    BriefService service = service(new SteppingClock(pressed, written));

    String name =
        service.create(new BriefService.Request("", "Timing check", "Some pasted source text."));

    String brief = Files.readString(vaultDir.resolve("SoloMaster/Research/Briefs/" + name), UTF_8);
    assertThat(brief)
        .contains("prompt_version: brief-v2\ngeneration_seconds: 42\nstatus: unreviewed")
        .contains("fetched_at: \"2026-11-10T09:00:42Z\"");
  }

  private BriefService service(Clock clock) {
    ModelClient model =
        (prompt, usable) ->
            new ModelClient.ModelReply(BriefPromptTests.COMPLETE_BRIEF, "fake", "fake-model");
    return new BriefService(
        new ResearchVault(vaultDir.toString()),
        new TranscriptFetcher(List.of("unused"), Duration.ZERO, 0),
        new YouTubeVideoDetails("https://unused.example", "unused", RestClient.create()),
        model,
        clock);
  }

  /** Returns the given instants in order, one per call, then repeats the last one. */
  private static final class SteppingClock extends Clock {
    private final Deque<Instant> instants;

    SteppingClock(Instant... instants) {
      this.instants = new ArrayDeque<>(List.of(instants));
    }

    @Override
    public Instant instant() {
      return instants.size() > 1 ? instants.removeFirst() : instants.getFirst();
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }
}
