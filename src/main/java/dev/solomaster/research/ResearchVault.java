package dev.solomaster.research;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The Obsidian vault as seen by the research module (ADR-0004): it writes only under {@code
 * SoloMaster/Research/}, never overwrites a note, and checks every resolved path stays inside its
 * folder.
 */
class ResearchVault {

  /** The research folder, relative to the vault root. */
  static final String FOLDER = "SoloMaster/Research";

  static final Pattern NOTE_NAME =
      Pattern.compile("\\d{4}-\\d{2}-\\d{2}-[a-z0-9]+(?:-[a-z0-9]+)*\\.md");

  private static final int MAX_SLUG_LENGTH = 60;
  private static final String SETUP_TEMPLATE =
      """
      # My setup

      Sent with every brief request so the **For my setup** section can judge a source
      against what I actually use. Never put passwords, API keys, or other credentials here.

      ## Tools

      ## Stack

      ## Projects

      ## Learning

      ## Goals
      """;

  private final Path vault;
  private final Path research;

  ResearchVault(String vaultPath) {
    if (vaultPath == null || vaultPath.isBlank()) {
      this.vault = null;
      this.research = null;
    } else {
      this.vault = Path.of(vaultPath).toAbsolutePath().normalize();
      this.research = vault.resolve(FOLDER);
    }
  }

  boolean isConfigured() {
    return vault != null && Files.isDirectory(vault);
  }

  /** Returns the setup note, creating an empty template first if it is missing. */
  String setupContext() {
    Path context = folder("Context");
    Path setup = contained(context, "my-setup.md");
    try {
      if (!Files.exists(setup, LinkOption.NOFOLLOW_LINKS)) {
        Files.writeString(setup, SETUP_TEMPLATE, UTF_8, StandardOpenOption.CREATE_NEW);
      }
      requireRegularFile(setup);
      return Files.readString(setup, UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Writes a new brief under {@code SoloMaster/Research/Briefs/} and returns its note name. */
  String writeBrief(LocalDate date, String title, String markdown) {
    Path briefs = folder("Briefs");
    String base = date + "-" + slug(title);
    for (int suffix = 1; ; suffix++) {
      String name = (suffix == 1 ? base : base + "-" + suffix) + ".md";
      try {
        Files.writeString(contained(briefs, name), markdown, UTF_8, StandardOpenOption.CREATE_NEW);
        return name;
      } catch (FileAlreadyExistsException e) {
        // Keep the existing note; try the next suffix.
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  /**
   * Writes the source text under {@code SoloMaster/Research/Sources/} with the brief's note name.
   */
  void writeSource(String name, String markdown) {
    requireNoteName(name);
    try {
      Files.writeString(
          contained(folder("Sources"), name), markdown, UTF_8, StandardOpenOption.CREATE_NEW);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  Optional<String> readBrief(String name) {
    if (!isConfigured() || name == null || !NOTE_NAME.matcher(name).matches()) {
      return Optional.empty();
    }
    Path brief = contained(research.resolve("Briefs"), name);
    if (!Files.isRegularFile(brief, LinkOption.NOFOLLOW_LINKS)) {
      return Optional.empty();
    }
    try {
      return Optional.of(Files.readString(brief, UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** An {@code obsidian://} link that opens the brief in the Obsidian app. */
  String obsidianUri(String name) {
    requireNoteName(name);
    return "obsidian://open?vault="
        + encode(vault.getFileName().toString())
        + "&file="
        + encode(FOLDER + "/Briefs/" + name);
  }

  static String slug(String title) {
    String ascii =
        Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    String slug = trimDashes(ascii.replaceAll("[^a-z0-9]+", "-"));
    if (slug.length() > MAX_SLUG_LENGTH) {
      slug = trimDashes(slug.substring(0, MAX_SLUG_LENGTH));
    }
    return slug.isEmpty() ? "brief" : slug;
  }

  private static String trimDashes(String value) {
    int start = 0;
    int end = value.length();
    while (start < end && value.charAt(start) == '-') {
      start++;
    }
    while (end > start && value.charAt(end - 1) == '-') {
      end--;
    }
    return value.substring(start, end);
  }

  private Path folder(String name) {
    if (!isConfigured()) {
      throw new IllegalStateException("Vault path is not configured");
    }
    Path folder = contained(research, name);
    try {
      Files.createDirectories(folder);
      if (!folder.toRealPath().startsWith(vault.toRealPath())) {
        throw new IllegalStateException("Research folder resolves outside the vault");
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return folder;
  }

  private Path contained(Path folder, String name) {
    Path resolved = folder.resolve(name).normalize();
    if (!resolved.startsWith(research) || !folder.equals(resolved.getParent())) {
      throw new IllegalArgumentException("Path escapes the research folder");
    }
    return resolved;
  }

  private static void requireNoteName(String name) {
    if (name == null || !NOTE_NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("Not a research note name");
    }
  }

  private static void requireRegularFile(Path path) throws IOException {
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Not a regular file: " + path.getFileName());
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, UTF_8).replace("+", "%20");
  }
}
