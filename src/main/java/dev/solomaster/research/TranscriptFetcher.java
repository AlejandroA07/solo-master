package dev.solomaster.research;

import static java.nio.charset.StandardCharsets.UTF_8;

import dev.solomaster.research.TranscriptUnavailableException.Reason;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the transcript tool for one video (ADR-0004): an argument list and never a shell, a
 * validated video ID, a scrubbed environment, a timeout, and an output cap.
 */
class TranscriptFetcher {

  private static final Set<String> INHERITED_ENVIRONMENT =
      Set.of("PATH", "HOME", "LANG", "LC_ALL", "LC_CTYPE", "TMPDIR");

  private final List<String> command;
  private final Duration timeout;
  private final int maxChars;

  TranscriptFetcher(List<String> command, Duration timeout, int maxChars) {
    this.command = List.copyOf(command);
    this.timeout = timeout;
    this.maxChars = maxChars;
  }

  String fetch(String videoId) {
    if (!YouTubeLinks.isVideoId(videoId)) {
      throw new IllegalArgumentException("Not a YouTube video ID");
    }
    List<String> arguments = new ArrayList<>(command);
    arguments.add(videoId);
    ProcessBuilder builder =
        new ProcessBuilder(arguments).redirectError(ProcessBuilder.Redirect.DISCARD);
    Map<String, String> environment = builder.environment();
    Map<String, String> child = childEnvironment(environment);
    environment.clear();
    environment.putAll(child);

    Process process;
    try {
      process = builder.start();
    } catch (IOException e) {
      throw new TranscriptUnavailableException(Reason.FAILED);
    }
    try {
      return awaitOutput(process);
    } finally {
      process.descendants().forEach(ProcessHandle::destroyForcibly);
      process.destroyForcibly();
    }
  }

  /** The child sees only an allow-list of the parent's variables, so no API key reaches it. */
  static Map<String, String> childEnvironment(Map<String, String> parent) {
    Map<String, String> child = new HashMap<>();
    parent.forEach(
        (name, value) -> {
          if (INHERITED_ENVIRONMENT.contains(name)) {
            child.put(name, value);
          }
        });
    child.put("PYTHONWARNINGS", "ignore");
    child.put("PYTHONIOENCODING", "utf-8");
    return child;
  }

  private String awaitOutput(Process process) {
    StringBuilder output = new StringBuilder();
    AtomicBoolean overflow = new AtomicBoolean();
    Thread reader =
        Thread.ofVirtual()
            .start(
                () -> {
                  try (Reader in = new InputStreamReader(process.getInputStream(), UTF_8)) {
                    char[] buffer = new char[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                      if (output.length() + read > maxChars) {
                        overflow.set(true);
                        process.destroyForcibly();
                        return;
                      }
                      output.append(buffer, 0, read);
                    }
                  } catch (IOException e) {
                    // The process was killed or closed its output; the exit status decides.
                  }
                });
    try {
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        throw new TranscriptUnavailableException(Reason.TIMED_OUT);
      }
      reader.join(Duration.ofSeconds(5));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TranscriptUnavailableException(Reason.FAILED);
    }
    if (overflow.get()) {
      throw new TranscriptUnavailableException(Reason.TOO_LONG);
    }
    return switch (process.exitValue()) {
      case 0 -> {
        String transcript = output.toString().strip();
        if (transcript.isEmpty()) {
          throw new TranscriptUnavailableException(Reason.NO_CAPTIONS);
        }
        yield transcript;
      }
      case 2 -> throw new TranscriptUnavailableException(Reason.NO_CAPTIONS);
      default -> throw new TranscriptUnavailableException(Reason.FAILED);
    };
  }
}
