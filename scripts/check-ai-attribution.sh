#!/bin/bash
# Reads a commit message or PR description on stdin and fails if it credits an
# AI tool as author or co-author. Mentioning AI tools as a topic is fine.
# Used by the commit-msg hook and the PR description check in CI.
set -uo pipefail

tools='claude|codex|copilot|chatgpt|gpt(-?[0-9][a-z0-9.]*)?|openai|anthropic|gemini|cursor|an? ai( assistant| model| tool)?'

# Drop git's comment lines so the commit template itself never matches.
message=$(grep -v '^#' || true)

patterns=(
  "co-authored-by:.*($tools|noreply@anthropic\.com)"
  "(generated|written|authored|created|produced) (with|by|using) (\[)?(github )?($tools)\b"
)

status=0
for pattern in "${patterns[@]}"; do
  if matches=$(printf '%s\n' "$message" | grep -inE "$pattern"); then
    printf '%s\n' "$matches" | sed 's/^/  AI attribution: line /' >&2
    status=1
  fi
done

if [ "$status" -ne 0 ]; then
  echo "Remove AI authorship markers from the commit message or PR description." >&2
fi
exit "$status"
