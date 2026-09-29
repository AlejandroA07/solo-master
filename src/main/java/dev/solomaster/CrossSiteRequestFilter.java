package dev.solomaster;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protects the loopback-only app from other sites (ADR-0004 item 10). Every request must carry a
 * loopback {@code Host} for the port it arrived on, which defeats DNS rebinding. State-changing
 * requests are also rejected when the browser marks them cross-site or sends a foreign {@code
 * Origin}.
 */
@Component
class CrossSiteRequestFilter extends OncePerRequestFilter {

  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
  private static final Set<String> ALLOWED_FETCH_SITES = Set.of("same-origin", "none");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String host = request.getHeader("Host");
    int port = request.getLocalPort();
    if (host == null
        || !Set.of("127.0.0.1:" + port, "localhost:" + port)
            .contains(host.toLowerCase(Locale.ROOT))) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    if (!SAFE_METHODS.contains(request.getMethod())) {
      String fetchSite = request.getHeader("Sec-Fetch-Site");
      String origin = request.getHeader("Origin");
      if ((fetchSite != null && !ALLOWED_FETCH_SITES.contains(fetchSite))
          || (origin != null && !origin.equalsIgnoreCase("http://" + host))) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
