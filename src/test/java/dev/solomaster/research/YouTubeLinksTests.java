package dev.solomaster.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class YouTubeLinksTests {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        "https://youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=42",
        "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
        "https://youtu.be/dQw4w9WgXcQ?si=abc",
        "https://www.youtube.com/shorts/dQw4w9WgXcQ",
        "https://www.youtube.com/live/dQw4w9WgXcQ",
        "  http://www.youtube.com/embed/dQw4w9WgXcQ  "
      })
  void extractsTheVideoIdFromYouTubeLinks(String link) {
    assertThat(YouTubeLinks.videoId(link)).contains("dQw4w9WgXcQ");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://example.com/watch?v=dQw4w9WgXcQ",
        "https://youtube.com.evil.example/watch?v=dQw4w9WgXcQ",
        "https://www.youtube.com/watch?v=short",
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ;rm",
        "https://youtu.be/../../etc/passwd",
        "javascript:alert(1)",
        "file:///etc/passwd",
        "ftp://youtu.be/dQw4w9WgXcQ",
        "not a url",
        ""
      })
  void rejectsEverythingElse(String link) {
    assertThat(YouTubeLinks.videoId(link)).isEmpty();
  }

  @Test
  void requireVideoIdReturnsAnEqualIdAndRejectsAnythingElse() {
    assertThat(YouTubeLinks.requireVideoId("dQw4w9WgXcQ")).isEqualTo("dQw4w9WgXcQ");
    assertThat(YouTubeLinks.requireVideoId("a-b_c0123XY")).isEqualTo("a-b_c0123XY");
    assertThatThrownBy(() -> YouTubeLinks.requireVideoId("dQw4w9WgXc;"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> YouTubeLinks.requireVideoId(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
