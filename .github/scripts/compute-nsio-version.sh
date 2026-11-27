#!/usr/bin/env bash
set -euo pipefail

# Gradle owns the single version implementation. This wrapper only exports
# its result for the workflow's artifact and release naming.
SOURCE_SHA="${GITHUB_SHA:-$(git rev-parse HEAD)}"
export NSIO_SOURCE_SHA="$SOURCE_SHA"

VERSION_OUTPUT="$(gradle -q printNsioVersion --no-daemon)"
printf '%s\n' "$VERSION_OUTPUT"

: > nsio-version.env
while IFS= read -r line; do
  if [[ "$line" == NSIO_* ]]; then
    printf '%s\n' "$line" >> nsio-version.env
    if [[ -n "${GITHUB_ENV:-}" ]]; then
      printf '%s\n' "$line" >> "$GITHUB_ENV"
    fi
  fi
done <<< "$VERSION_OUTPUT"

for required in NSIO_VERSION_NAME NSIO_VERSION_TAG NSIO_VERSION_CODE NSIO_SOURCE_SHA; do
  if ! grep -q "^$required=" nsio-version.env; then
    echo "::error::Shared Gradle version calculator did not output $required."
    exit 1
  fi
done

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  source nsio-version.env
  printf 'version_tag=%s\n' "$NSIO_VERSION_TAG" >> "$GITHUB_OUTPUT"
fi
