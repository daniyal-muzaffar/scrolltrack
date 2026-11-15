#!/bin/bash
# Builds ScrollTrack.apk (com.dudeyy.scrolltrack) with the manual aapt2/javac/d8 pipeline.
# Java + Android framework APIs only. There is no Gradle build: the Gradle daemon
# cannot run in the sandbox this project was built in, so the build is a plain
# shell pipeline. Run from the repo root: ./build.sh
#
# Signing comes from keystore.properties in the repo root (git-ignored) or from
# the KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD environment
# variables. Copy keystore.properties.example to keystore.properties to set it up.
# Version comes from VERSION_CODE / VERSION_NAME (defaults below).
set -e

PJ="$(cd "$(dirname "$0")" && pwd)"
OUT="$PJ/out"
ANDROID_SDK="${ANDROID_HOME:-$HOME/android-sdk}"
JAVA_HOME="${JAVA_HOME:-$ANDROID_SDK/jdk/jdk-17.0.20.1+1}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
BT="$ANDROID_SDK/build-tools/34.0.0"
VERSION_CODE="${VERSION_CODE:-21}"
VERSION_NAME="${VERSION_NAME:-1.5.1}"

# --- signing config: keystore.properties first, environment variables second ---
STORE_FILE="${KEYSTORE_FILE:-}"
STORE_PASS="${KEYSTORE_PASSWORD:-}"
KEY_ALIAS="${KEY_ALIAS:-}"
KEY_PASS="${KEY_PASSWORD:-}"
if [ -f "$PJ/keystore.properties" ]; then
  while IFS='=' read -r key value; do
    value="$(printf '%s' "$value" | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    case "$key" in
      storeFile) STORE_FILE="$value" ;;
      storePassword) STORE_PASS="$value" ;;
      keyAlias) KEY_ALIAS="$value" ;;
      keyPassword) KEY_PASS="$value" ;;
    esac
  done < <(grep -v '^[[:space:]]*#' "$PJ/keystore.properties" | grep -v '^[[:space:]]*$')
fi
if [ -z "$STORE_FILE" ] || [ -z "$STORE_PASS" ] || [ -z "$KEY_ALIAS" ] || [ -z "$KEY_PASS" ]; then
  echo "No signing config found. Copy keystore.properties.example to keystore.properties and fill it in," >&2
  echo "or set KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD." >&2
  exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT/compiled-res" "$OUT/gen" "$OUT/classes"

"$BT/aapt2" compile --dir "$PJ/app/src/main/res" -o "$OUT/compiled-res/res.zip"
"$BT/aapt2" link -o "$OUT/app-unsigned.apk" \
  -I "$ANDROID_SDK/platforms/android-34/android.jar" \
  --manifest "$PJ/app/src/main/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  "$OUT/compiled-res/res.zip"

javac -encoding UTF-8 -nowarn -source 17 -target 17 \
  -cp "$ANDROID_SDK/platforms/android-34/android.jar" \
  -d "$OUT/classes" \
  $(find "$OUT/gen" -name "*.java") \
  $(find "$PJ/app/src/main/java" -name "*.java")

"$BT/d8" --lib "$ANDROID_SDK/platforms/android-34/android.jar" --min-api 26 \
  --output "$OUT" $(find "$OUT/classes" -name "*.class")

cd "$OUT" && jar uf app-unsigned.apk classes.dex && cd - >/dev/null
"$BT/zipalign" -f -p 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"

"$BT/apksigner" sign --ks "$STORE_FILE" --ks-pass "pass:$STORE_PASS" \
  --ks-key-alias "$KEY_ALIAS" --key-pass "pass:$KEY_PASS" \
  --out "$OUT/ScrollTrack.apk" "$OUT/app-aligned.apk"
"$BT/apksigner" verify "$OUT/ScrollTrack.apk" && echo "SIGNATURE_OK"
echo "APK: $OUT/ScrollTrack.apk"
echo "BUILD_OK"
