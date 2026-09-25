#!/bin/bash
# Deterministic verification gate. Green here is required before work is complete.
set -euo pipefail

cd "$(dirname "$0")/.."

# Gradle resolves against the committed gradle.lockfile in strict mode, so any
# unlocked or drifted dependency fails the build.
echo "[verify] 1/3 compile with warnings as errors"
./gradlew --no-daemon compileJava compileTestJava

echo "[verify] 2/3 formatting"
./gradlew --no-daemon spotlessCheck

echo "[verify] 3/3 tests"
./gradlew --no-daemon test

echo "[verify] ALL GREEN"
