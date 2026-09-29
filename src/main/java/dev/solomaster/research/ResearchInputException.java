package dev.solomaster.research;

/** The research request was invalid; the message is safe to show to the user. */
class ResearchInputException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  ResearchInputException(String message) {
    super(message);
  }
}
