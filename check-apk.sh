#!/usr/bin/env bash
# Reads a finished APK with aapt2 and prints what Android will see: package, versionCode, versionName and the launcher icon entries.
# Fails the build when the versionCode is under 1000 (that is the "every build is version 1, so the phone keeps the old app" mistake)
# or when the APK has no launcher icon entry at all.
#   bash tools/ci-check-apk.sh path/to/app.apk
set -euo pipefail

apk="${1:-}"
[ -n "$apk" ] && [ -f "$apk" ] || { echo "ci-check-apk: APK not found: ${apk:-<none>}" >&2; exit 1; }

aapt2="${AAPT2:-}"
if [ -z "$aapt2" ]; then
  aapt2="$(ls -d "${ANDROID_HOME:-/nonexistent}"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -n 1 || true)"
fi
[ -n "$aapt2" ] && [ -x "$aapt2" ] || { echo "ci-check-apk: aapt2 not found (set AAPT2 or ANDROID_HOME)" >&2; exit 1; }

out="$("$aapt2" dump badging "$apk" 2>/dev/null)" || { echo "ci-check-apk: aapt2 could not read $apk" >&2; exit 1; }

pkg_line="$(printf '%s\n' "$out" | grep -m1 '^package:' || true)"
[ -n "$pkg_line" ] || { echo "ci-check-apk: no package line in the APK" >&2; exit 1; }
code="$(printf '%s\n' "$pkg_line" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p")"
name="$(printf '%s\n' "$pkg_line" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")"
pkg="$(printf '%s\n' "$pkg_line" | sed -n "s/.*name='\([^']*\)'.*/\1/p")"

echo "APK check: package $pkg, versionName $name, versionCode $code"
icons="$(printf '%s\n' "$out" | grep -E "^application-icon-|^application:.*icon=" || true)"
if [ -n "$icons" ]; then
  printf '%s\n' "$icons" | sed 's/^/  /'
else
  echo "ci-check-apk: the APK has no launcher icon entry" >&2
  exit 1
fi

[[ "$code" =~ ^[0-9]+$ ]] || { echo "ci-check-apk: could not read versionCode" >&2; exit 1; }
if [ "$code" -lt 1000 ]; then
  echo "ci-check-apk: versionCode $code is too low. Android will not replace an installed app with the same or a lower versionCode, so the phone would keep the old build (and its old icon)." >&2
  exit 1
fi
echo "APK check passed"
