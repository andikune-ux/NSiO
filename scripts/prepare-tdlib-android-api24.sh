#!/usr/bin/env bash
set -euo pipefail

ARTIFACT_DIR="${1:-$RUNNER_TEMP/tdlib-api24}"
APP_DIR="${2:-app/src/main}"
test -s "$ARTIFACT_DIR/java/org/drinkless/tdlib/Client.java"
test -s "$ARTIFACT_DIR/java/org/drinkless/tdlib/TdApi.java"
for abi in arm64-v8a armeabi-v7a x86_64 x86; do
  test -s "$ARTIFACT_DIR/jniLibs/$abi/libtdjni.so"
done
(
  cd "$ARTIFACT_DIR"
  # The first build recorded absolute runner paths; normalize them after artifact download.
  awk '{ path = $2; prefix = "tdlib-api24-output/"; pos = index(path, prefix); if (pos > 0) path = substr(path, pos + length(prefix)); print $1 "  " path }' SHA256SUMS | sha256sum -c -
)
mkdir -p "$APP_DIR/java/org/drinkless/tdlib" "$APP_DIR/jniLibs"
cp "$ARTIFACT_DIR/java/org/drinkless/tdlib/"*.java "$APP_DIR/java/org/drinkless/tdlib/"
for abi in arm64-v8a armeabi-v7a x86_64 x86; do
  mkdir -p "$APP_DIR/jniLibs/$abi"
  install -m 0644 "$ARTIFACT_DIR/jniLibs/$abi/libtdjni.so" "$APP_DIR/jniLibs/$abi/libtdjni.so"
done
printf 'TDLib 1.8.70 API 24 installed into %s\n' "$APP_DIR"
