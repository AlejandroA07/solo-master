package dev.solomaster.research;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ResearchVaultTests {

  private static final LocalDate DAY = LocalDate.of(2026, 9, 29);

  @TempDir Path vaultDir;

  @Test
  void isNotConfiguredWithoutAPathOrWithAMissingFolder() {
    assertThat(new ResearchVault("").isConfigured()).isFalse();
    assertThat(new ResearchVault(null).isConfigured()).isFalse();
    assertThat(new ResearchVault(vaultDir.resolve("missing").toString()).isConfigured()).isFalse();
    assertThat(vault().isConfigured()).isTrue();
  }

  @Test
  void createsTheSetupTemplateOnceAndKeepsEdits() throws IOException {
    ResearchVault vault = vault();
    assertThat(vault.setupContext()).contains("# My setup").contains("Never put passwords");

    Path setup = vaultDir.resolve("SoloMaster/Research/Context/my-setup.md");
    Files.writeString(setup, "I use IntelliJ.", UTF_8);
    assertThat(vault.setupContext()).isEqualTo("I use IntelliJ.");
  }

  @Test
  void writesBriefsUnderResearchBriefsWithoutOverwriting() throws IOException {
    ResearchVault vault = vault();

    String first = vault.writeBrief(DAY, "Loops & Boundaries!", "one");
    String second = vault.writeBrief(DAY, "Loops & Boundaries!", "two");

    assertThat(first).isEqualTo("2026-09-29-loops-boundaries.md");
    assertThat(second).isEqualTo("2026-09-29-loops-boundaries-2.md");
    assertThat(Files.readString(vaultDir.resolve("SoloMaster/Research/Briefs/" + first)))
        .isEqualTo("one");
    assertThat(vault.readBrief(second)).contains("two");
  }

  @Test
  void slugsNeverCarryPathSegments() {
    assertThat(ResearchVault.slug("../../etc/passwd")).isEqualTo("etc-passwd");
    assertThat(ResearchVault.slug("Café Größe")).isEqualTo("cafe-gro-e");
    assertThat(ResearchVault.slug("///")).isEqualTo("brief");
    assertThat(ResearchVault.slug("a".repeat(200))).hasSize(60);
    assertThat(ResearchVault.slug("a".repeat(59) + "-b")).isEqualTo("a".repeat(59));
    assertThat(ResearchVault.slug("-".repeat(50_000) + "x" + "-".repeat(50_000))).isEqualTo("x");
  }

  @Test
  void writesTheSourceNextToTheBriefName() throws IOException {
    ResearchVault vault = vault();
    vault.writeSource("2026-09-29-loops.md", "transcript");

    assertThat(
            Files.readString(vaultDir.resolve("SoloMaster/Research/Sources/2026-09-29-loops.md")))
        .isEqualTo("transcript");
    assertThatThrownBy(() -> vault.writeSource("../../outside.md", "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "../my-setup.md",
        "../../secret.md",
        "2026-09-29-x.md/../../../y.md",
        "/etc/passwd",
        "2026-09-29-UPPER.md",
        "notes.md",
        ""
      })
  void readsOnlyBriefNotesByName(String name) {
    assertThat(vault().readBrief(name)).isEmpty();
  }

  @Test
  void refusesToReadASymlinkedBrief() throws IOException {
    ResearchVault vault = vault();
    vault.writeBrief(DAY, "real", "inside");
    Path outside = Files.writeString(vaultDir.resolve("outside.txt"), "secret", UTF_8);
    Files.createSymbolicLink(
        vaultDir.resolve("SoloMaster/Research/Briefs/2026-09-29-link.md"), outside);

    assertThat(vault.readBrief("2026-09-29-link.md")).isEmpty();
  }

  @Test
  void refusesAResearchFolderThatLinksOutsideTheVault() throws IOException {
    Path elsewhere = Files.createTempDirectory("elsewhere");
    Files.createDirectories(vaultDir.resolve("SoloMaster"));
    Files.createSymbolicLink(vaultDir.resolve("SoloMaster/Research"), elsewhere);

    assertThatThrownBy(() -> vault().writeBrief(DAY, "x", "y"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesASoloMasterFolderThatLinksOutsideTheVault() throws IOException {
    Path elsewhere = Files.createTempDirectory("elsewhere");
    Files.createSymbolicLink(vaultDir.resolve("SoloMaster"), elsewhere);

    assertThatThrownBy(() -> vault().writeBrief(DAY, "x", "y"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void buildsAnObsidianLinkWithEncodedNames() throws IOException {
    Path named = Files.createDirectories(vaultDir.resolve("My Vault"));
    ResearchVault vault = new ResearchVault(named.toString());

    assertThat(vault.obsidianUri("2026-09-29-loops.md"))
        .isEqualTo(
            "obsidian://open?vault=My%20Vault&file=SoloMaster%2FResearch%2FBriefs%2F2026-09-29-loops.md");
  }

  private ResearchVault vault() {
    return new ResearchVault(vaultDir.toString());
  }
}
