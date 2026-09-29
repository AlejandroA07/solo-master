package dev.solomaster.research;

import java.util.function.Predicate;

/** Application-owned model access (ADR-0005). Provider details stay behind this interface. */
interface ModelClient {

  /**
   * Returns the first reply that {@code usable} accepts, failing over across providers.
   *
   * @throws ModelUnavailableException when every provider failed or gave unusable output
   */
  ModelReply complete(ModelPrompt prompt, Predicate<String> usable);

  record ModelPrompt(String system, String user) {}

  /** The reply with its provenance: which provider and pinned model produced it. */
  record ModelReply(String text, String provider, String model) {}
}
