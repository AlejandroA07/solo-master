package dev.solomaster.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.solomaster.research.TranscriptUnavailableException.Reason;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TranscriptFetcherTests {

  private static final String SCRIPT = "src/test/resources/research/fake-transcript.sh";

  private final TranscriptFetcher fetcher =
      new TranscriptFetcher(List.of("/bin/sh", SCRIPT), Duration.ofSeconds(2), 1_000);

  @Test
  void returnsTheCaptions() {
    assertThat(fetcher.fetch("okVideo0001")).isEqualTo("[00:01] first line\n[00:05] second line");
  }

  @Test
  void rejectsAnInvalidVideoIdBeforeStartingAProcess() {
    var neverRuns = new TranscriptFetcher(List.of("/nonexistent"), Duration.ofSeconds(1), 10);
    assertThatThrownBy(() -> neverRuns.fetch("../../etc/x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> neverRuns.fetch("abc; rm -rf"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reportsMissingCaptions() {
    assertReason("noCaptions1", Reason.NO_CAPTIONS);
    assertReason("emptyVideo1", Reason.NO_CAPTIONS);
  }

  @Test
  void reportsAFailedOrBlockedFetch() {
    assertReason("brokenVideo", Reason.FAILED);
  }

  @Test
  void stopsAProcessThatRunsTooLong() {
    long start = System.nanoTime();
    assertReason("slowVideo01", Reason.TIMED_OUT);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(8));
  }

  @Test
  void stopsAProcessWhoseOutputExceedsTheCap() {
    assertReason("hugeOutput1", Reason.TOO_LONG);
  }

  @Test
  void reportsAFailureWhenTheToolIsMissing() {
    var missing =
        new TranscriptFetcher(List.of("/nonexistent/python"), Duration.ofSeconds(1), 1_000);
    assertThatThrownBy(() -> missing.fetch("okVideo0001"))
        .isInstanceOfSatisfying(
            TranscriptUnavailableException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.FAILED));
  }

  @Test
  void passesOnlyAnAllowListOfEnvironmentVariablesToTheTool() {
    Map<String, String> child =
        TranscriptFetcher.childEnvironment(
            Map.of(
                "PATH", "/usr/bin",
                "HOME", "/home/me",
                "GEMINI_API_KEY", "not-for-the-child",
                "OPENROUTER_API_KEY", "not-for-the-child",
                "SOLOMASTER_VAULT_PATH", "/vault"));
    assertThat(child)
        .containsEntry("PATH", "/usr/bin")
        .containsEntry("HOME", "/home/me")
        .containsKeys("PYTHONWARNINGS", "PYTHONIOENCODING")
        .doesNotContainKeys("GEMINI_API_KEY", "OPENROUTER_API_KEY", "SOLOMASTER_VAULT_PATH");
  }

  private void assertReason(String videoId, Reason reason) {
    assertThatThrownBy(() -> fetcher.fetch(videoId))
        .isInstanceOfSatisfying(
            TranscriptUnavailableException.class, e -> assertThat(e.reason()).isEqualTo(reason));
  }
}
