#!/bin/bash
# Deterministic verification gate. Green here is required before work is complete.
set -euo pipefail

cd "$(dirname "$0")/.."

echo "[verify] 1/4 restore locked dependencies"
dotnet restore SoloMaster.slnx --locked-mode

echo "[verify] 2/4 Release build"
dotnet build SoloMaster.slnx --configuration Release --no-restore

echo "[verify] 3/4 formatting"
dotnet format whitespace SoloMaster.slnx --no-restore --verify-no-changes
dotnet format style SoloMaster.slnx --no-restore --verify-no-changes --severity info

echo "[verify] 4/4 tests"
dotnet test SoloMaster.slnx --configuration Release --no-build --no-restore

echo "[verify] ALL GREEN"
