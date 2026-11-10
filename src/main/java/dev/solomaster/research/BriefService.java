package dev.solomaster.research;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Turns a YouTube link or pasted text into a brief in the vault (research slice 1, ADR-0004). The
 * brief enters the vault at once with {@code status: unreviewed}; triage comes later.
 */
class BriefService {

  static final int MAX_LINK_LENGTH = 2_048;
  static final int MAX_TITLE_LENGTH = 200;
  private static final int FALLBACK_TITLE_LENGTH = 80;
  static final int MAX_TEXT_LENGTH = 400_000;

  private final ResearchVault vault;
  private final TranscriptFetcher transcripts;
  private final YouTubeVideoDetails videoDetails;
  private final ModelClient model;
  private final Clock clock;

  BriefService(
      ResearchVault vault,
      TranscriptFetcher transcripts,
      YouTubeVideoDetails videoDetails,
      ModelClient model,
      Clock clock) {
    this.vault = vault;
    this.transcripts = transcripts;
    this.videoDetails = videoDetails;
    this.model = model;
    this.clock = clock;
  }

  /** What the user submitted; blank fields mean "not given". */
  record Request(String link, String title, String text) {}

  /** Creates the brief and returns its note name. */
  String create(Request request) {
    Instant started = clock.instant();
    String link = blankToNull(request.link());
    String title = singleLine(blankToNull(request.title()));
    String text = blankToNull(request.text());
    validateLengths(link, title, text);
    if (!vault.isConfigured()) {
      throw new IllegalStateException("Vault path is not configured");
    }

    Optional<String> videoId = link == null ? Optional.empty() : YouTubeLinks.videoId(link);
    String source;
    String transcriptSource;
    if (text != null) {
      source = text.strip();
      transcriptSource = "pasted";
    } else if (videoId.isPresent()) {
      source = transcripts.fetch(videoId.get());
      transcriptSource = "youtube-transcript-api";
    } else if (link != null) {
      throw new ResearchInputException(
          "Only YouTube links are fetched. For an article or any other page, paste its text.");
    } else {
      throw new ResearchInputException("Give a YouTube link or paste the source text.");
    }
    Optional<YouTubeVideoDetails.VideoDetails> details = videoId.flatMap(videoDetails::lookup);
    if (title == null) {
      title = details.map(YouTubeVideoDetails.VideoDetails::title).orElse(null);
    }
    if (title == null) {
      title = videoId.map(id -> "YouTube video " + id).orElseGet(() -> firstWords(source));
    }
    String author = details.map(YouTubeVideoDetails.VideoDetails::channel).orElse("");
    String published =
        details
            .map(YouTubeVideoDetails.VideoDetails::published)
            .map(LocalDate::toString)
            .orElse("");

    ModelClient.ModelReply reply =
        model.complete(
            BriefPrompt.build(vault.setupContext(), title, link, source), BriefPrompt::isComplete);

    Instant finished = clock.instant();
    // From pressing the button to a written brief: transcript, video details and the model.
    long generationSeconds = Duration.between(started, finished).toSeconds();
    Instant now = finished.truncatedTo(ChronoUnit.SECONDS);
    String type = videoId.isPresent() ? "youtube" : "text";
    String frontMatter =
        "---\n"
            + "source_url: "
            + quoted(link == null ? "" : link)
            + "\ntype: "
            + type
            + "\ntitle: "
            + quoted(title)
            + "\nauthor: "
            + quoted(author)
            + "\npublished: "
            + quoted(published)
            + "\nfetched_at: "
            + quoted(now.toString())
            + "\ntranscript_source: "
            + transcriptSource
            + "\ncontent_hash: "
            + quoted("sha256:" + sha256(source))
            + "\nprovider: "
            + quoted(reply.provider())
            + "\nmodel: "
            + quoted(reply.model())
            + "\nprompt_version: "
            + BriefPrompt.VERSION
            + "\ngeneration_seconds: "
            + generationSeconds
            + "\nstatus: unreviewed\ntriage: []\n---\n\n";
    String heading = frontMatter + "# " + title + "\n\n";
    String body = BriefPrompt.clean(reply.text()) + "\n";
    String name =
        vault.writeBrief(
            LocalDate.ofInstant(now, clock.getZone()),
            title,
            noteName -> heading + sourceLink(noteName, videoId.isPresent()) + body);

    String sourceNote =
        "---\nsource_url: "
            + quoted(link == null ? "" : link)
            + "\ntype: "
            + type
            + "\ntitle: "
            + quoted(title)
            + "\nauthor: "
            + quoted(author)
            + "\npublished: "
            + quoted(published)
            + "\nfetched_at: "
            + quoted(now.toString())
            + "\ntranscript_source: "
            + transcriptSource
            + "\ncontent_hash: "
            + quoted("sha256:" + sha256(source))
            + "\nbrief: "
            + quoted(ResearchVault.FOLDER + "/Briefs/" + name)
            + "\n---\n\n"
            + source
            + "\n";
    vault.writeSource(name, sourceNote);
    return name;
  }

  /** An Obsidian link to the source note, which shares the brief's note name. */
  private static String sourceLink(String noteName, boolean video) {
    String target = ResearchVault.FOLDER + "/Sources/" + noteName.replaceFirst("\\.md$", "");
    return video
        ? "Transcript: [[" + target + "|full transcript]]\n\n"
        : "Full text: [[" + target + "|full text]]\n\n";
  }

  private static void validateLengths(String link, String title, String text) {
    if (link != null && link.length() > MAX_LINK_LENGTH) {
      throw new ResearchInputException("The link is too long.");
    }
    if (title != null && title.length() > MAX_TITLE_LENGTH) {
      throw new ResearchInputException("The title is too long (at most 200 characters).");
    }
    if (text != null && text.length() > MAX_TEXT_LENGTH) {
      throw new ResearchInputException("The pasted text is too long (at most 400,000 characters).");
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static String singleLine(String value) {
    return value == null ? null : value.replaceAll("\\p{Cntrl}+", " ").strip();
  }

  /**
   * A title for pasted text without one: its first line, or the first sentence of a long line, or
   * else the words that fit in 80 characters followed by an ellipsis. Never cuts a word in half.
   */
  static String firstWords(String text) {
    String firstLine = singleLine(text.lines().findFirst().orElse("Pasted text"));
    if (firstLine.length() <= FALLBACK_TITLE_LENGTH) {
      return firstLine;
    }
    int sentenceEnd = firstLine.indexOf(". ");
    if (sentenceEnd > 0 && sentenceEnd < FALLBACK_TITLE_LENGTH) {
      return firstLine.substring(0, sentenceEnd);
    }
    int lastSpace = firstLine.lastIndexOf(' ', FALLBACK_TITLE_LENGTH);
    String words =
        lastSpace > 0
            ? firstLine.substring(0, lastSpace)
            : firstLine.substring(0, FALLBACK_TITLE_LENGTH);
    int end = words.length();
    while (end > 0 && " \t,;:".indexOf(words.charAt(end - 1)) >= 0) {
      end--;
    }
    return words.substring(0, end) + "…";
  }

  /** A YAML double-quoted scalar; control characters are dropped. */
  private static String quoted(String value) {
    String clean = value.replaceAll("\\p{Cntrl}", " ");
    return "\"" + clean.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
