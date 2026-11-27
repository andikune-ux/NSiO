#!/usr/bin/env bash
set -euo pipefail

# Reproducible TDLib Java/JNI build for NSiO's Android API 24 floor.
TDLIB_COMMIT="c8828d50f5eac2fc7089ce1089d7ce9095c61b5e"
TDLIB_VERSION="1.8.70"
ANDROID_NDK_VERSION="28.2.13676358"
ANDROID_API_LEVEL="24"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
BUILD_ROOT="${RUNNER_TEMP:-$PWD}/nsio-tdlib-build"
SOURCE_DIR="$BUILD_ROOT/td"

: "${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT or ANDROID_HOME must be set}"

if [[ ! -d "$SOURCE_DIR/.git" ]]; then
  mkdir -p "$BUILD_ROOT"
  git clone --filter=blob:none https://github.com/tdlib/td.git "$SOURCE_DIR"
fi
git -C "$SOURCE_DIR" fetch --depth=1 origin "$TDLIB_COMMIT"
git -C "$SOURCE_DIR" checkout --detach "$TDLIB_COMMIT"
ACTUAL_COMMIT="$(git -C "$SOURCE_DIR" rev-parse HEAD)"
[[ "$ACTUAL_COMMIT" == "$TDLIB_COMMIT" ]] || {
  echo "TDLib source pin mismatch: $ACTUAL_COMMIT" >&2
  exit 1
}

ANDROID_DIR="$SOURCE_DIR/example/android"
cd "$ANDROID_DIR"
mkdir -p third-party
if [[ ! -d third-party/openssl ]]; then
  ./build-openssl.sh "$ANDROID_SDK_ROOT" "$ANDROID_NDK_VERSION" third-party/openssl
fi

if [[ ! -f tdlib/java/org/drinkless/tdlib/Client.java ]] ||
   [[ ! -f tdlib/java/org/drinkless/tdlib/TdApi.java ]] ||
   [[ ! -f tdlib/libs/arm64-v8a/libtdjni.so ]] ||
   [[ ! -f tdlib/libs/armeabi-v7a/libtdjni.so ]] ||
   [[ ! -f tdlib/libs/x86_64/libtdjni.so ]] ||
   [[ ! -f tdlib/libs/x86/libtdjni.so ]]; then
  ./build-tdlib.sh "$ANDROID_SDK_ROOT" "$ANDROID_NDK_VERSION" third-party/openssl c++_static Java "$ANDROID_API_LEVEL" "arm64-v8a armeabi-v7a x86_64 x86"
fi

for abi in arm64-v8a armeabi-v7a x86_64 x86; do
  lib="tdlib/libs/$abi/libtdjni.so"
  test -s "$lib"
  "$ANDROID_SDK_ROOT/ndk/$ANDROID_NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" -h "$lib" >/dev/null
done

OUT_DIR="${1:-$PWD/nsio-tdlib-api24}"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/jniLibs" "$OUT_DIR/java"
cp -R tdlib/libs/* "$OUT_DIR/jniLibs/"
cp -R tdlib/java/. "$OUT_DIR/java/"
cat > "$OUT_DIR/BUILD-INFO.txt" <<INFO
TDLib version: $TDLIB_VERSION
TDLib commit: $TDLIB_COMMIT
Android minimum API: $ANDROID_API_LEVEL
Android NDK: $ANDROID_NDK_VERSION
Interface: Java/JNI
ABIs: arm64-v8a, armeabi-v7a, x86_64, x86
INFO
find "$OUT_DIR" -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > "$OUT_DIR/SHA256SUMS"
echo "TDLib $TDLIB_VERSION built for Android API $ANDROID_API_LEVEL."
