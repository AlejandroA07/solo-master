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
  ModelClient modelClient(ResearchProperties properties) {
    HttpClient httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.modelTimeout());
    return new OpenAiCompatibleModelClient(
        properties.providers(), RestClient.builder().requestFactory(requestFactory).build());
  }

  @Bean
  BriefService briefService(
      ResearchVault vault, TranscriptFetcher transcripts, ModelClient modelClient) {
    return new BriefService(vault, transcripts, modelClient, Clock.systemDefaultZone());
  }
}
