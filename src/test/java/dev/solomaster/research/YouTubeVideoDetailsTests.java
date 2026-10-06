package dev.solomaster.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class YouTubeVideoDetailsTests {

  private static final String VIDEO = "dQw4w9WgXcQ";
  private static final String URL =
      "https://youtube.example/v3/videos?part=snippet&id="
          + VIDEO
          + "&fields=items(snippet(title,channelTitle,publishedAt))";

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

  @Test
  void readsTitleChannelAndPublishDateWithTheKeyInAHeader() {
    server
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("X-Goog-Api-Key", "yt-key"))
        .andRespond(
            json(
                """
                {"items":[{"snippet":{"title":"Loops in Java","channelTitle":"Java Channel",
                "publishedAt":"2026-09-30T18:05:00Z"}}]}
                """));

    assertThat(details("yt-key").lookup(VIDEO))
        .contains(
            new YouTubeVideoDetails.VideoDetails(
                "Loops in Java", "Java Channel", LocalDate.of(2026, 9, 30)));
    server.verify();
  }

  @Test
  void looksNothingUpWithoutAKey() {
    assertThat(details("").lookup(VIDEO)).isEmpty();
    assertThat(details(null).lookup(VIDEO)).isEmpty();
    server.verify();
  }

  @Test
  void givesNothingForAnUnknownOrPrivateVideo() {
    server.expect(requestTo(URL)).andRespond(json("{\"items\":[]}"));

    assertThat(details("yt-key").lookup(VIDEO)).isEmpty();
  }

  @Test
  void givesNothingWhenTheApiRefuses() {
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

    assertThat(details("yt-key").lookup(VIDEO)).isEmpty();
  }

  @Test
  void givesNothingWhenTheApiIsUnreachable() {
    server.expect(requestTo(URL)).andRespond(withException(new IOException("offline")));

    assertThat(details("yt-key").lookup(VIDEO)).isEmpty();
  }

  @Test
  void givesNothingForMalformedJson() {
    server.expect(requestTo(URL)).andRespond(json("not json"));

    assertThat(details("yt-key").lookup(VIDEO)).isEmpty();
  }

  @Test
  void flattensAndCapsUntrustedText() {
    String longTitle = "T".repeat(BriefService.MAX_TITLE_LENGTH + 50);
    server
        .expect(requestTo(URL))
        .andRespond(
            json(
                "{\"items\":[{\"snippet\":{\"title\":\""
                    + longTitle
                    + "\",\"channelTitle\":\"Line\\none\",\"publishedAt\":\"yesterday\"}}]}"));

    YouTubeVideoDetails.VideoDetails found = details("yt-key").lookup(VIDEO).orElseThrow();

    assertThat(found.title()).hasSize(BriefService.MAX_TITLE_LENGTH);
    assertThat(found.channel()).isEqualTo("Line one");
    assertThat(found.published()).isNull();
  }

  @Test
  void treatsBlankFieldsAsMissing() {
    server
        .expect(requestTo(URL))
        .andRespond(json("{\"items\":[{\"snippet\":{\"title\":\"  \"}}]}"));

    YouTubeVideoDetails.VideoDetails found = details("yt-key").lookup(VIDEO).orElseThrow();

    assertThat(found.title()).isNull();
    assertThat(found.channel()).isNull();
    assertThat(found.published()).isNull();
  }

  private YouTubeVideoDetails details(String apiKey) {
    return new YouTubeVideoDetails("https://youtube.example/v3", apiKey, builder.build());
  }

  private static org.springframework.test.web.client.ResponseCreator json(String body) {
    return withSuccess(body, MediaType.APPLICATION_JSON);
  }
}
