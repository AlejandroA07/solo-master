package dev.solomaster;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HomePageTests {

  @LocalServerPort private int port;

  @Test
  void homePageReturnsSoloMasterShell() {
    var response =
        RestClient.create("http://localhost:" + port)
            .get()
            .uri("/")
            .retrieve()
            .toEntity(String.class);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).contains("data-testid=\"home-heading\"").contains("Solo Master");
  }
}
