package dev.solomaster.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

class YouTubePlaylistTests {

  private static final String PLAYLIST = "PLresearch_0123456789";
  private static final String URL =
      "https://youtube.example/v3/playlistItems?part=snippet&maxResults=50&playlistId="
          + PLAYLIST
          + "&fields=nextPageToken,items(snippet(title,resourceId(videoId)))";

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

  @Test
  void readsEveryPageWithTheKeyInAHeader() {
    server
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("X-Goog-Api-Key", "yt-key"))
        .andRespond(
            json(
                """
                {"nextPageToken":"PAGE_2","items":[
                {"snippet":{"title":"Loops in Java","resourceId":{"videoId":"aaaaaaaaaaa"}}}]}
                """));
    server
        .expect(requestTo(URL + "&pageToken=PAGE_2"))
        .andExpect(header("X-Goog-Api-Key", "yt-key"))
        .andRespond(
            json(
                """
                {"items":[{"snippet":{"title":"Records\\nexplained","resourceId":{"videoId":"bbbbbbbbbbb"}}}]}
                """));

    assertThat(playlist("yt-key", PLAYLIST).videos())
        .containsExactly(
            new YouTubePlaylist.Video("aaaaaaaaaaa", "Loops in Java"),
            new YouTubePlaylist.Video("bbbbbbbbbbb", "Records explained"));
    server.verify();
  }

  @Test
  void skipsItemsWithoutAValidVideoIdAndNamesUntitledOnes() {
    server
        .expect(requestTo(URL))
        .andRespond(
            json(
                """
                {"items":[
                {"snippet":{"title":"Injected","resourceId":{"videoId":"../../etc/passwd"}}},
                {"snippet":{"title":"No id"}},
                {"snippet":{"resourceId":{"videoId":"ccccccccccc"}}}]}
                """));

    assertThat(playlist("yt-key", PLAYLIST).videos())
        .containsExactly(new YouTubePlaylist.Video("ccccccccccc", "YouTube video ccccccccccc"));
    server.verify();
  }

  @Test
  void stopsAtAMalformedPageToken() {
    server
        .expect(requestTo(URL))
        .andRespond(json("{\"nextPageToken\":\"a&key=leak\",\"items\":[]}"));

    assertThat(playlist("yt-key", PLAYLIST).videos()).isEmpty();
    server.verify();
  }

  @Test
  void readsAtMostFourPages() {
    server
        .expect(times(YouTubePlaylist.MAX_PAGES), requestTo(org.hamcrest.Matchers.startsWith(URL)))
        .andRespond(json("{\"nextPageToken\":\"MORE\",\"items\":[]}"));

    assertThat(playlist("yt-key", PLAYLIST).videos()).isEmpty();
    server.verify();
  }

  @Test
  void explainsARefusedPlaylistWithoutTheProviderText() {
    server
        .expect(requestTo(URL))
        .andRespond(
            withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"secret detail\"}}"));

    assertThatThrownBy(() -> playlist("yt-key", PLAYLIST).videos())
        .isInstanceOf(YouTubePlaylist.UnavailableException.class)
        .hasMessageContaining("HTTP 403")
        .hasMessageContaining("Unlisted")
        .hasMessageNotContaining("secret detail");
  }

  @Test
  void explainsAnUnreachableOrUnreadableReply() {
    server.expect(requestTo(URL)).andRespond(withException(new IOException("down")));
    assertThatThrownBy(() -> playlist("yt-key", PLAYLIST).videos())
        .isInstanceOf(YouTubePlaylist.UnavailableException.class)
        .hasMessageContaining("Couldn't reach YouTube");

    server.reset();
    server.expect(requestTo(URL)).andRespond(json("not json"));
    assertThatThrownBy(() -> playlist("yt-key", PLAYLIST).videos())
        .isInstanceOf(YouTubePlaylist.UnavailableException.class)
        .hasMessageContaining("unreadable");
  }

  @Test
  void namesWhatIsMissingAndReadsNothingUntilConfigured() {
    assertThat(playlist("yt-key", "").setupProblem()).contains("YOUTUBE_PLAYLIST_ID");
    assertThat(playlist("yt-key", "PL&key=x").setupProblem()).contains("not a playlist ID");
    assertThat(playlist("", PLAYLIST).setupProblem()).contains("YOUTUBE_API_KEY");
    assertThat(playlist("yt-key", PLAYLIST).setupProblem()).isNull();

    assertThatThrownBy(() -> playlist("", PLAYLIST).videos())
        .isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  private YouTubePlaylist playlist(String key, String playlistId) {
    return new YouTubePlaylist("https://youtube.example/v3", key, playlistId, builder.build());
  }

  private static ResponseCreator json(String body) {
    return withSuccess(body, MediaType.APPLICATION_JSON);
  }
}
