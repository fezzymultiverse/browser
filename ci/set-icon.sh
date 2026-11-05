#!/usr/bin/env bash
# Installs the Bojack launcher icon into the generated android/ project. Run by CI right after `npx cap add android`.
#   bash ci/set-icon.sh android
# Safe to run twice. Fails the build when the icon files or the manifest entry are missing, so a build can no longer ship the default icon without anyone noticing.
set -euo pipefail

root="${1:-android}"
res="$root/app/src/main/res"
manifest="$root/app/src/main/AndroidManifest.xml"
src="assets/android-res"

[ -d "$res" ] || { echo "Missing $res. Did 'npx cap add android' run?" >&2; exit 1; }
[ -f "$manifest" ] || { echo "Missing $manifest" >&2; exit 1; }
for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  for f in ic_launcher.png ic_launcher_round.png ic_launcher_foreground.png; do
    [ -f "$src/mipmap-$d/$f" ] || { echo "Missing $src/mipmap-$d/$f" >&2; exit 1; }
  done
done

cp -R "$src/." "$res/"

# adaptive icon (Android 8 and newer): Bojack foreground on white
mkdir -p "$res/mipmap-anydpi-v26"
for n in ic_launcher ic_launcher_round; do
  cat > "$res/mipmap-anydpi-v26/$n.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@android:color/white"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
XML
done

grep -q 'android:icon="@mipmap/ic_launcher"' "$manifest" || { echo "Manifest does not use @mipmap/ic_launcher" >&2; exit 1; }
echo "Launcher icon installed: Bojack (adaptive, round, legacy)"
