package dev.solomaster;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CrossSiteRequestFilterTests {

  private final CrossSiteRequestFilter filter = new CrossSiteRequestFilter();

  @ParameterizedTest
  @ValueSource(strings = {"127.0.0.1:8080", "localhost:8080", "LOCALHOST:8080"})
  void letsLoopbackRequestsThrough(String host) throws Exception {
    assertThat(run(request("GET", host))).isEqualTo(200);
  }

  @ParameterizedTest
  @ValueSource(strings = {"evil.example:8080", "evil.example", "127.0.0.1:9999", "127.0.0.1"})
  void rejectsForeignHostsEvenForReads(String host) throws Exception {
    assertThat(run(request("GET", host))).isEqualTo(403);
  }

  @Test
  void rejectsAMissingHost() throws Exception {
    MockHttpServletRequest request = request("GET", null);
    request.removeHeader("Host");
    assertThat(run(request)).isEqualTo(403);
  }

  @Test
  void letsSameOriginAndNonBrowserPostsThrough() throws Exception {
    MockHttpServletRequest sameOrigin = request("POST", "localhost:8080");
    sameOrigin.addHeader("Origin", "http://localhost:8080");
    sameOrigin.addHeader("Sec-Fetch-Site", "same-origin");
    assertThat(run(sameOrigin)).isEqualTo(200);

    assertThat(run(request("POST", "localhost:8080"))).isEqualTo(200);
  }

  @ParameterizedTest
  @ValueSource(strings = {"cross-site", "same-site"})
  void rejectsPostsTheBrowserMarksAsFromAnotherSite(String fetchSite) throws Exception {
    MockHttpServletRequest request = request("POST", "localhost:8080");
    request.addHeader("Sec-Fetch-Site", fetchSite);
    assertThat(run(request)).isEqualTo(403);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"https://evil.example", "null", "http://127.0.0.1:8080", "http://localhost:9999"})
  void rejectsPostsFromAnotherOrigin(String origin) throws Exception {
    MockHttpServletRequest request = request("POST", "localhost:8080");
    request.addHeader("Origin", origin);
    assertThat(run(request)).isEqualTo(403);
  }

  @Test
  void checksEveryStateChangingMethod() throws Exception {
    for (String method : new String[] {"PUT", "PATCH", "DELETE"}) {
      MockHttpServletRequest request = request(method, "localhost:8080");
      request.addHeader("Sec-Fetch-Site", "cross-site");
      assertThat(run(request)).as(method).isEqualTo(403);
    }
  }

  private static MockHttpServletRequest request(String method, String host) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, "/research");
    request.setLocalPort(8080);
    if (host != null) {
      request.addHeader("Host", host);
    }
    return request;
  }

  private int run(MockHttpServletRequest request) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, response, chain);
    return chain.getRequest() == null ? response.getStatus() : 200;
  }
}
