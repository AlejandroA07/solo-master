#!/usr/bin/env bash
# Builds the research module's transcript tool environment (ADR-0004) from exact, hash-locked
# requirements. The environment is gitignored; rerun this script after requirements.txt changes.
set -euo pipefail

cd "$(dirname "$0")/.."
venv=".venv-research"

rm -rf "$venv"
python3 -m venv "$venv"
"$venv/bin/python" -m pip install --quiet --disable-pip-version-check --require-hashes --no-deps --only-binary=:all: \
  -r scripts/research/requirements.txt
"$venv/bin/python" -c "import youtube_transcript_api" && echo "[setup] $venv ready"
