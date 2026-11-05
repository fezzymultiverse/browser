#!/usr/bin/env bash
# V1.27.14: proves an APK is a signed RELEASE build. Fails when it is Debug, unsigned, debug-signed, or when versionCode / icon checks fail.
#   bash ci/verify-release.sh path/to/app-release.apk
set -euo pipefail

apk="${1:-}"
[ -n "$apk" ] && [ -f "$apk" ] || { echo "verify-release: APK not found: ${apk:-<none>}" >&2; ls -la "$(dirname "${apk:-.}")" 2>/dev/null >&2 || true; exit 1; }
case "$apk" in *-unsigned.apk) echo "verify-release: $apk is unsigned" >&2; exit 1 ;; esac

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bt="$(ls -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/nonexistent}}"/build-tools/*/ 2>/dev/null | sort -V | tail -n 1 || true)"
bt="${bt%/}"
[ -n "$bt" ] && [ -x "$bt/apksigner" ] && [ -x "$bt/aapt2" ] || { echo "verify-release: apksigner/aapt2 not found (set ANDROID_HOME)" >&2; exit 1; }

certs="$("$bt/apksigner" verify --verbose --print-certs "$apk" 2>&1)" || { printf '%s\n' "$certs" >&2; echo "verify-release: signature does not verify" >&2; exit 1; }
printf '%s\n' "$certs" | grep -E "^Verifies|Signer #1 certificate (DN|SHA-256)" || true
if printf '%s\n' "$certs" | grep -qiE "Signer #1 certificate DN:.*(CN=Android Debug|O=Android)"; then
  echo "verify-release: signed with the Android DEBUG key, not your release key" >&2; exit 1
fi

badging="$("$bt/aapt2" dump badging "$apk" 2>/dev/null)" || { echo "verify-release: aapt2 could not read the APK" >&2; exit 1; }
if printf '%s\n' "$badging" | grep -q "^application-debuggable"; then
  echo "verify-release: the APK is DEBUGGABLE (a Debug build)" >&2; exit 1
fi

chk="$here/check-apk.sh"; [ -f "$chk" ] || chk="$here/ci-check-apk.sh"
[ -f "$chk" ] || { echo "verify-release: check-apk.sh / ci-check-apk.sh not found next to this script" >&2; exit 1; }
AAPT2="$bt/aapt2" bash "$chk" "$apk"
echo "verify-release passed: signed Release build, not debuggable"
