#!/usr/bin/env bash
# Installs the Android SDK pieces Capacitor 7 needs into $ANDROID_HOME (cached between GitLab pipelines).
set -euo pipefail
: "${ANDROID_HOME:?ANDROID_HOME must be set}"
SM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SM" ]; then
  echo "Downloading Android command line tools"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  ZIP="${TMPDIR:-/tmp}/android-cmdline-tools.zip"
  curl -fsSL -o "$ZIP" "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  unzip -q -o "$ZIP" -d "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -f "$ZIP"
else
  echo "Android command line tools already cached"
fi
# yes ends with a broken pipe when sdkmanager stops reading, so do not let pipefail fail the script here
yes | "$SM" --licenses >/dev/null 2>&1 || true
"$SM" "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null
[ -d "$ANDROID_HOME/platforms/android-35" ] && echo "Android platform 35 present" || { echo "ERROR: Android platform 35 is missing"; exit 1; }
[ -d "$ANDROID_HOME/build-tools/35.0.0" ] && echo "Build tools 35.0.0 present" || { echo "ERROR: build tools 35.0.0 are missing"; exit 1; }
