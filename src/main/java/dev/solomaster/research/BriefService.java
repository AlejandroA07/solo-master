package dev.solomaster.research;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
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
  static final int MAX_TEXT_LENGTH = 400_000;

  private final ResearchVault vault;
  private final TranscriptFetcher transcripts;
  private final ModelClient model;
  private final Clock clock;

  BriefService(ResearchVault vault, TranscriptFetcher transcripts, ModelClient model, Clock clock) {
    this.vault = vault;
    this.transcripts = transcripts;
    this.model = model;
    this.clock = clock;
  }

  /** What the user submitted; blank fields mean "not given". */
  record Request(String link, String title, String text) {}

  /** Creates the brief and returns its note name. */
  String create(Request request) {
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
    if (title == null) {
      title = videoId.map(id -> "YouTube video " + id).orElseGet(() -> firstWords(source));
    }

    ModelClient.ModelReply reply =
        model.complete(
            BriefPrompt.build(vault.setupContext(), title, link, source), BriefPrompt::isComplete);

    Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
    String type = videoId.isPresent() ? "youtube" : "text";
    String frontMatter =
        "---\n"
            + "source_url: "
            + quoted(link == null ? "" : link)
            + "\ntype: "
            + type
            + "\ntitle: "
            + quoted(title)
            + "\nauthor: \"\"\npublished: \"\"\nfetched_at: "
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
            + "\nstatus: unreviewed\ntriage: []\n---\n\n";
    String heading = frontMatter + "# " + title + "\n\n";
    String body = BriefPrompt.clean(reply.text()) + "\n";
    String name =
        vault.writeBrief(
            LocalDate.ofInstant(now, clock.getZone()),
            title,
            noteName -> heading + transcriptLink(noteName) + body);

    String sourceNote =
        "---\nsource_url: "
            + quoted(link == null ? "" : link)
            + "\ntype: "
            + type
            + "\ntitle: "
            + quoted(title)
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
  private static String transcriptLink(String noteName) {
    String target = ResearchVault.FOLDER + "/Sources/" + noteName.replaceFirst("\\.md$", "");
    return "Transcript: [[" + target + "|full transcript]]\n\n";
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

  private static String firstWords(String text) {
    String firstLine = singleLine(text.lines().findFirst().orElse("Pasted text"));
    return firstLine.length() <= 80 ? firstLine : firstLine.substring(0, 80).strip();
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
