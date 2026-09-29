package dev.solomaster.research;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;

/** The versioned brief prompt and the check that a reply follows the ADR-0004 template. */
final class BriefPrompt {

  static final String VERSION = "brief-v1";

  static final List<String> SECTIONS =
      List.of(
          "In short",
          "For my setup",
          "What's new to me",
          "Key claims",
          "Mentioned tools & sites",
          "People & positions",
          "Open questions",
          "Suggested triage");

  private static final String SYSTEM = load("/research/brief-prompt.md");

  private BriefPrompt() {}

  static ModelClient.ModelPrompt build(String setup, String title, String link, String source) {
    String user =
        "# My setup note\n\n"
            + setup.strip()
            + "\n\n# Source\n\nTitle: "
            + title
            + "\nLink: "
            + (link == null ? "none (pasted text)" : link)
            + "\n\n<source>\n"
            + source.replace("</source>", "</ source>")
            + "\n</source>\n";
    return new ModelClient.ModelPrompt(SYSTEM, user);
  }

  /** Removes a code fence the model may have wrapped around the whole answer. */
  static String clean(String reply) {
    String text = reply.strip();
    if (text.startsWith("```")) {
      int firstLineEnd = text.indexOf('\n');
      int closingFence = text.lastIndexOf("```");
      if (firstLineEnd > 0 && closingFence > firstLineEnd) {
        text = text.substring(firstLineEnd + 1, closingFence).strip();
      }
    }
    return text;
  }

  /** True when every template section appears as a level-2 heading, in order, first line first. */
  static boolean isComplete(String reply) {
    List<String> headings =
        clean(reply)
            .lines()
            .map(String::strip)
            .filter(line -> line.startsWith("## "))
            .map(line -> normalize(line.substring(3)))
            .toList();
    List<String> expected = SECTIONS.stream().map(BriefPrompt::normalize).toList();
    int next = 0;
    for (String heading : headings) {
      if (next < expected.size() && heading.equals(expected.get(next))) {
        next++;
      }
    }
    return next == expected.size() && clean(reply).startsWith("## ");
  }

  private static String normalize(String heading) {
    return heading.strip().replace('’', '\'').toLowerCase(Locale.ROOT);
  }

  private static String load(String resource) {
    try (InputStream in = BriefPrompt.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Missing prompt resource " + resource);
      }
      return new String(in.readAllBytes(), UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
