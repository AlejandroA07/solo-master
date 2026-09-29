#!/usr/bin/env bash
# Builds the research module's transcript tool environment (ADR-0004) from exact, hash-locked
# requirements. The environment is gitignored; rerun this script after requirements.txt changes.
# Needs Python 3.10 or newer: set PYTHON to choose one, otherwise the newest python3.x on PATH is used.
set -euo pipefail

cd "$(dirname "$0")/.."
venv=".venv-research"

python="${PYTHON:-}"
if [[ -z "$python" ]]; then
  for candidate in python3.14 python3.13 python3.12 python3.11 python3.10 python3; do
    if command -v "$candidate" >/dev/null 2>&1; then
      python="$candidate"
      break
    fi
  done
fi
if [[ -z "$python" ]] || ! "$python" -c 'import sys; sys.exit(sys.version_info < (3, 10))'; then
  echo "[setup] Python 3.10 or newer is required (found: ${python:-none})." >&2
  echo "[setup] Install one, e.g. 'brew install python@3.13', or set PYTHON=/path/to/python3." >&2
  exit 1
fi

rm -rf "$venv"
"$python" -m venv "$venv"
"$venv/bin/python" -m pip install --quiet --disable-pip-version-check --require-hashes --no-deps \
  --only-binary=:all: -r scripts/research/requirements.txt
"$venv/bin/python" -c "import youtube_transcript_api" && echo "[setup] $venv ready ($("$venv/bin/python" --version))"
