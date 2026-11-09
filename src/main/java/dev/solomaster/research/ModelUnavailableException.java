package dev.solomaster.research;

import java.util.List;

/**
 * Every configured model provider failed, was rate limited, or gave unusable output. Carries one
 * plain line per provider (model ID and reason, never keys or provider text) for the user.
 */
class ModelUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<String> failures;

  ModelUnavailableException(List<String> failures) {
    super("No model provider produced a usable reply");
    this.failures = List.copyOf(failures);
  }

  List<String> failures() {
    return failures;
  }
}
