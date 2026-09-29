package dev.solomaster.research;

/** The transcript tool could not provide captions; the caller falls back to manual paste. */
class TranscriptUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  enum Reason {
    NO_CAPTIONS,
    FAILED,
    TIMED_OUT,
    TOO_LONG
  }

  private final Reason reason;

  TranscriptUnavailableException(Reason reason) {
    super("Transcript unavailable: " + reason);
    this.reason = reason;
  }

  Reason reason() {
    return reason;
  }
}
