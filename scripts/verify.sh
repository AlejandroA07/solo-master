#!/bin/bash
# Deterministic verification gate. Green here is required before work is complete.
set -euo pipefail

cd "$(dirname "$0")/.."

# Gradle resolves against the committed gradle.lockfile in strict mode, so any
# unlocked or drifted dependency fails the build.
echo "[verify] 1/4 compile with warnings as errors"
./gradlew --no-daemon compileJava compileTestJava

echo "[verify] 2/4 formatting"
./gradlew --no-daemon spotlessCheck

echo "[verify] 3/4 tests"
./gradlew --no-daemon test

echo "[verify] 4/4 AI attribution checker"
./scripts/check-ai-attribution.test.sh

echo "[verify] ALL GREEN"
