package dev.solomaster.research;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BriefServiceTitleTests {

  @Test
  void keepsAShortFirstLineAsItIs() {
    assertThat(BriefService.firstWords("Loops and boundaries\nFor loops end early."))
        .isEqualTo("Loops and boundaries");
  }

  @Test
  void usesTheFirstSentenceOfALongFirstLine() {
    String text =
        "Java gets JSON built in. The new API parses and writes JSON without any external"
            + " library at all.";

    assertThat(BriefService.firstWords(text)).isEqualTo("Java gets JSON built in");
  }

  @Test
  void endsOnAWholeWordWithAnEllipsisWhenNoSentenceFits() {
    String text =
        "Today's digital-first companies rely on analysts to make sense of data, generate"
            + " insights, and help leaders make informed business decisions.";

    String title = BriefService.firstWords(text);

    assertThat(title)
        .isEqualTo(
            "Today's digital-first companies rely on analysts to make sense of data, generate…");
    assertThat(title.length()).isLessThanOrEqualTo(81);
  }
}
