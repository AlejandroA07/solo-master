package dev.solomaster.research;

/** Every configured model provider failed, was rate limited, or gave unusable output. */
class ModelUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  ModelUnavailableException() {
    super("No model provider produced a usable reply");
  }
}
