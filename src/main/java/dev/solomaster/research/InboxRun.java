package dev.solomaster.research;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Run now (research slice 2, ADR-0004 item 8): reads the intake playlist, queues the videos that
 * have no brief yet, and writes their briefs one at a time, at most {@link #MAX_ITEMS_PER_RUN} per
 * run (ADR-0002 item 2). One run at a time, in the background, so the page stays responsive. A
 * failed item stays in the queue with its reason and is retried on the next Run now; nothing is
 * retried automatically.
 */
class InboxRun {

  static final String PLAYLIST = "playlist";
  static final int MAX_ITEMS_PER_RUN = 10;

  private static final Logger log = LoggerFactory.getLogger(InboxRun.class);

  private final YouTubePlaylist playlist;
  private final RunItems items;
  private final BriefService briefs;
  private final ResearchVault vault;
  private final Executor executor;
  private final Clock clock;
  private final AtomicBoolean running = new AtomicBoolean();
  private volatile String lastProblem;

  InboxRun(
      YouTubePlaylist playlist,
      RunItems items,
      BriefService briefs,
      ResearchVault vault,
      Executor executor,
      Clock clock) {
    this.playlist = playlist;
    this.items = items;
    this.briefs = briefs;
    this.vault = vault;
    this.executor = executor;
    this.clock = clock;
  }

  /** Null when Run now can start, otherwise what to set up, safe to show on the page. */
  String setupProblem() {
    if (!vault.isConfigured()) {
      return "The vault path is not configured. Set SOLOMASTER_VAULT_PATH to your Obsidian vault.";
    }
    return playlist.setupProblem();
  }

  boolean isRunning() {
    return running.get();
  }

  /** Why the last run stopped early, if it did; safe to show on the page. */
  String lastProblem() {
    return lastProblem;
  }

  /** Starts a run unless one is already going or setup is missing; returns whether it started. */
  boolean start() {
    if (setupProblem() != null || !running.compareAndSet(false, true)) {
      return false;
    }
    lastProblem = null;
    try {
      executor.execute(this::runAndRelease);
    } catch (RuntimeException e) {
      running.set(false);
      throw e;
    }
    return true;
  }

  private void runAndRelease() {
    try {
      run();
    } catch (RuntimeException e) {
      log.error("Run now stopped unexpectedly", e);
      lastProblem = "The run stopped unexpectedly. The app log has the details.";
    } finally {
      running.set(false);
    }
  }

  private void run() {
    try {
      queueNewVideos();
    } catch (YouTubePlaylist.UnavailableException e) {
      lastProblem = e.getMessage();
      return;
    }
    for (RunItems.RunItem item : items.pending(MAX_ITEMS_PER_RUN)) {
      if (!process(item)) {
        return;
      }
    }
  }

  private void queueNewVideos() {
    List<YouTubePlaylist.Video> videos = playlist.videos();
    Map<String, String> briefed = vault.briefedVideos();
    for (YouTubePlaylist.Video video : videos) {
      String briefName = briefed.get(video.videoId());
      if (briefName != null) {
        items.addDone(PLAYLIST, video.videoId(), video.title(), briefName, clock.instant());
      } else {
        items.addQueued(PLAYLIST, video.videoId(), video.title(), clock.instant());
      }
    }
  }

  /** Writes one brief; returns false when the run should stop because no model can answer. */
  private boolean process(RunItems.RunItem item) {
    String videoId = YouTubeLinks.requireVideoId(item.itemKey());
    BriefService.Request request =
        new BriefService.Request("https://www.youtube.com/watch?v=" + videoId, "", "");
    try {
      items.markDone(item.id(), briefs.create(request), clock.instant());
      return true;
    } catch (TranscriptUnavailableException e) {
      log.info("Run now: transcript unavailable for {}: {}", videoId, e.reason());
      items.markFailed(
          item.id(),
          "Couldn't get captions for this video. Paste its transcript in New research instead.",
          clock.instant());
      return true;
    } catch (ModelUnavailableException e) {
      items.markFailed(
          item.id(),
          "No model could write the brief:\n" + String.join("\n", e.failures()),
          clock.instant());
      lastProblem =
          "No model could write a brief, so the run stopped. The rest stay queued for the next"
              + " Run now. Gemini's free quota resets at midnight Pacific time.";
      return false;
    } catch (ResearchInputException e) {
      items.markFailed(item.id(), e.getMessage(), clock.instant());
      return true;
    } catch (RuntimeException e) {
      log.error("Run now: brief for {} failed", videoId, e);
      items.markFailed(
          item.id(), "Unexpected error. The app log has the details.", clock.instant());
      lastProblem = "The run stopped on an unexpected error. The app log has the details.";
      return false;
    }
  }
}
