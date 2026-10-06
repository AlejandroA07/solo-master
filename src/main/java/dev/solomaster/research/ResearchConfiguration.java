package dev.solomaster.research;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ResearchProperties.class)
class ResearchConfiguration {

  @Bean
  ResearchVault researchVault(ResearchProperties properties) {
    return new ResearchVault(properties.vaultPath());
  }

  @Bean
  TranscriptFetcher transcriptFetcher(ResearchProperties properties) {
    ResearchProperties.Transcript transcript = properties.transcript();
    return new TranscriptFetcher(transcript.command(), transcript.timeout(), transcript.maxChars());
  }

  @Bean
  YouTubeVideoDetails youTubeVideoDetails(ResearchProperties properties) {
    ResearchProperties.YouTube youtube = properties.youtube();
    return new YouTubeVideoDetails(
        youtube.baseUrl(), youtube.apiKey(), restClient(youtube.timeout()));
  }

  @Bean
  ModelClient modelClient(ResearchProperties properties) {
    return new OpenAiCompatibleModelClient(
        properties.providers(), restClient(properties.modelTimeout()), Thread::sleep);
  }

  @Bean
  BriefService briefService(
      ResearchVault vault,
      TranscriptFetcher transcripts,
      YouTubeVideoDetails videoDetails,
      ModelClient modelClient) {
    return new BriefService(
        vault, transcripts, videoDetails, modelClient, Clock.systemDefaultZone());
  }

  /** An outbound client that never follows redirects and gives up after the read timeout. */
  private static RestClient restClient(Duration readTimeout) {
    HttpClient httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(readTimeout);
    return RestClient.builder().requestFactory(requestFactory).build();
  }
}
