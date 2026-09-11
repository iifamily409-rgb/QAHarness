#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if ! command -v java >/dev/null 2>&1; then
  echo "Java is required but not found in PATH. Install JDK 17+ and retry."
  exit 1
fi

JAVA_VERSION="$(java -version 2>&1 | head -n 1 | sed -E 's/.*version "?([0-9]+).*$/\1/')"
if [ "$JAVA_VERSION" -lt 17 ]; then
  echo "JDK 17+ is required. Current version: $(java -version 2>&1 | head -n 1)"
  exit 1
fi

if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
  echo "ANDROID_HOME or ANDROID_SDK_ROOT is not set. Configure Android SDK before building."
  exit 1
fi

export QAHARNESS_STORE_PASS="${QAHARNESS_STORE_PASS:-qaharness123}"
export QAHARNESS_KEY_PASS="${QAHARNESS_KEY_PASS:-qaharness123}"

if [ ! -f "app/qaharness.jks" ]; then
  echo "Generating release keystore at app/qaharness.jks"
  keytool -genkeypair \
    -alias qaharness \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -keystore app/qaharness.jks \
    -storepass "$QAHARNESS_STORE_PASS" \
    -keypass "$QAHARNESS_KEY_PASS" \
    -dname "CN=QAHarness, OU=QA, O=Example, L=Local, ST=Local, C=US"
fi

./gradlew clean assembleRelease

APK_PATH="app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK_PATH" ]; then
  echo "APK built successfully: $APK_PATH"
  ls -lh "$APK_PATH"
else
  echo "APK not found at $APK_PATH"
  exit 1
fi

if command -v apksigner >/dev/null 2>&1; then
  echo "Verifying APK signature with apksigner..."
  apksigner verify --verbose "$APK_PATH"
else
  echo "apksigner not found in PATH. APK was built but not verified automatically."
fi

echo "Install command: adb install -r $APK_PATH"
