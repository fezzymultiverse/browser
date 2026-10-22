#!/usr/bin/env bash
# Same manifest, gradle and plugin edits as .github/workflows/android.yml, for GitLab CI.
# Run from the repo root after `npx cap add android`.
set -euo pipefail

M=android/app/src/main/AndroidManifest.xml
G=android/app/build.gradle
[ -f "$M" ] || { echo "ERROR: $M not found; run npx cap add android first"; exit 1; }
[ -f "$G" ] || { echo "ERROR: $G not found; run npx cap add android first"; exit 1; }

# add a <uses-permission> line right after the INTERNET permission, once
add_after_internet() {
  grep -q "$1" "$M" || sed -i "s#<uses-permission android:name=\"android.permission.INTERNET\" */>#&\n    <uses-permission android:name=\"$1\" />#" "$M"
}
# add a <uses-permission> line just before <application>, once
add_before_app() {
  grep -q "$1\"" "$M" || sed -i "s#<application#    <uses-permission android:name=\"$1\" />\n    <application#" "$M"
}

echo "== network state =="
add_after_internet android.permission.ACCESS_NETWORK_STATE
grep -q ACCESS_NETWORK_STATE "$M" && echo "network state permission present" || echo "WARNING: could not add ACCESS_NETWORK_STATE"

echo "== biometric =="
add_after_internet android.permission.USE_BIOMETRIC
grep -q "androidx.biometric" "$G" || sed -i '0,/^dependencies {/s//dependencies {\n    implementation "androidx.biometric:biometric:1.1.0"/' "$G"
grep -q "androidx.biometric" "$G" && echo "biometric library present" || { echo "ERROR: could not add androidx.biometric to $G"; exit 1; }

echo "== webkit (web passkeys, autofill) =="
grep -q "androidx.webkit:webkit" "$G" || sed -i '0,/^dependencies {/s//dependencies {\n    implementation "androidx.webkit:webkit:1.14.0"/' "$G"
grep -q "androidx.webkit:webkit" "$G" && echo "webkit library present" || { echo "ERROR: could not add androidx.webkit to $G"; exit 1; }

echo "== credentials (Credential Manager for WebView passkeys, V1.19.3) =="
# Android's WebView passkey guide lists these two next to androidx.webkit. Override the version with FB_CRED_VER if needed.
CRED_VER="${FB_CRED_VER:-1.5.0}"
grep -q "androidx.credentials:credentials:" "$G" || sed -i "0,/^dependencies {/s//dependencies {\n    implementation \"androidx.credentials:credentials:$CRED_VER\"/" "$G"
grep -q "androidx.credentials:credentials-play-services-auth" "$G" || sed -i "0,/^dependencies {/s//dependencies {\n    implementation \"androidx.credentials:credentials-play-services-auth:$CRED_VER\"/" "$G"
grep -q "androidx.credentials:credentials:" "$G" && grep -q "androidx.credentials:credentials-play-services-auth" "$G" && echo "credentials libraries present ($CRED_VER)" || { echo "ERROR: could not add androidx.credentials to $G"; exit 1; }

echo "== browser-origin permission for passkeys (V1.19.4) =="
# A browser must hold this before Android lets it name a web origin in a Credential Manager call. Android may still refuse to grant it to a normal app; Settings > Autofill & Passkeys shows the answer.
add_before_app android.permission.CREDENTIAL_MANAGER_SET_ORIGIN
grep -q 'android.permission.CREDENTIAL_MANAGER_SET_ORIGIN"' "$M" && echo "CREDENTIAL_MANAGER_SET_ORIGIN declared" || { echo "ERROR: could not declare CREDENTIAL_MANAGER_SET_ORIGIN"; exit 1; }

echo "== install apps and file provider =="
add_after_internet android.permission.REQUEST_INSTALL_PACKAGES
grep -q REQUEST_INSTALL_PACKAGES "$M" && echo "install packages permission present" || { echo "ERROR: could not add REQUEST_INSTALL_PACKAGES"; exit 1; }
grep -q 'fileprovider' "$M" && echo "file provider present" || { echo "ERROR: Capacitor FileProvider missing from manifest"; exit 1; }

echo "== notification and media permissions =="
for P in android.permission.POST_NOTIFICATIONS android.permission.WAKE_LOCK android.permission.ACCESS_NETWORK_STATE android.permission.ACCESS_WIFI_STATE; do
  add_before_app "$P"
done

echo "== keep-alive service, battery exemption, legacy storage =="
for P in android.permission.FOREGROUND_SERVICE android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS; do
  add_before_app "$P"
done
grep -q WRITE_EXTERNAL_STORAGE "$M" || sed -i "s#<application#    <uses-permission android:name=\"android.permission.WRITE_EXTERNAL_STORAGE\" android:maxSdkVersion=\"28\" />\n    <application#" "$M"
grep -q FBKeepAliveService "$M" || sed -i "s#</application>#        <service android:name=\".FBKeepAliveService\" android:exported=\"false\" android:foregroundServiceType=\"mediaPlayback\" />\n    </application>#" "$M"
for P in POST_NOTIFICATIONS WAKE_LOCK FOREGROUND_SERVICE FOREGROUND_SERVICE_MEDIA_PLAYBACK REQUEST_IGNORE_BATTERY_OPTIMIZATIONS WRITE_EXTERNAL_STORAGE; do
  grep -q "android.permission.$P\"" "$M" && echo "$P present" || { echo "ERROR: could not add $P"; exit 1; }
done

echo "== camera, microphone, location and media (site permissions) =="
for P in android.permission.CAMERA android.permission.RECORD_AUDIO android.permission.MODIFY_AUDIO_SETTINGS android.permission.ACCESS_COARSE_LOCATION android.permission.ACCESS_FINE_LOCATION android.permission.READ_MEDIA_IMAGES android.permission.READ_MEDIA_VIDEO android.permission.READ_MEDIA_AUDIO; do
  add_before_app "$P"
done
grep -q 'android.permission.READ_EXTERNAL_STORAGE"' "$M" || sed -i "s#<application#    <uses-permission android:name=\"android.permission.READ_EXTERNAL_STORAGE\" android:maxSdkVersion=\"32\" />\n    <application#" "$M"
for F in android.hardware.camera android.hardware.microphone android.hardware.location; do
  grep -q "$F" "$M" || sed -i "s#<application#    <uses-feature android:name=\"$F\" android:required=\"false\" />\n    <application#" "$M"
done
for P in CAMERA RECORD_AUDIO ACCESS_FINE_LOCATION READ_MEDIA_IMAGES; do
  grep -q "android.permission.$P\"" "$M" && echo "$P present" || { echo "ERROR: could not add $P"; exit 1; }
done
grep -q 'FBKeepAliveService' "$M" && echo "keep-alive service declared" || { echo "ERROR: could not declare FBKeepAliveService"; exit 1; }

echo "== HTTP and HTTPS browser intent filter =="
if ! grep -q "android.intent.action.VIEW" "$M"; then
  FILT='            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="http" />
                <data android:scheme="https" />
            </intent-filter>'
  # insert after the line that closes the first <activity ...> start tag (Capacitor's launcher activity)
  awk -v F="$FILT" '
    !done && /<activity/ { inact = 1 }
    { print }
    inact && !done && />/ { print F; done = 1; inact = 0 }
  ' "$M" > "$M.new"
  mv "$M.new" "$M"
fi
grep -q "android.intent.action.VIEW" "$M" && echo "browser intent filter present" || { echo "ERROR: could not add the browser intent filter"; exit 1; }

echo "== launcher activity must be singleTask (links from other apps reuse the running app) =="
grep -q 'android:launchMode="singleTask"' "$M" || sed -i '0,/<activity/s//<activity android:launchMode="singleTask"/' "$M"
grep -q 'android:launchMode="singleTask"' "$M" && echo "singleTask present" || { echo "ERROR: could not set launchMode singleTask"; exit 1; }

echo "== network security config (user certificate authorities, http fallback) =="
mkdir -p android/app/src/main/res/xml
cp ci/network_security_config.xml android/app/src/main/res/xml/network_security_config.xml
grep -q "networkSecurityConfig" "$M" || sed -i 's#<application#<application android:networkSecurityConfig="@xml/network_security_config"#' "$M"
grep -q "networkSecurityConfig" "$M" && echo "network security config present" || { echo "ERROR: could not add networkSecurityConfig"; exit 1; }

echo "== package visibility (Android 11+: lets Festers see Conduint and the autofill / passkey apps) =="
grep -q "<queries>" "$M" || sed -i 's#<application#    <queries>\n        <package android:name="com.fezzy.conduint" />\n        <intent><action android:name="android.service.autofill.AutofillService" /></intent>\n        <intent><action android:name="android.service.credentials.CredentialProviderService" /></intent>\n    </queries>\n    <application#' "$M"
grep -q "<queries>" "$M" && echo "queries block present" || { echo "ERROR: could not add the queries block"; exit 1; }

echo "== native plugins =="
D=android/app/src/main/java/com/fezzy/festersbrowser
mkdir -p "$D"
cp native/*.java "$D/"
# V1.20.2: fail fast (before Gradle) if MainActivity registers a plugin class that was not copied.
for C in $(grep -o 'registerPlugin([A-Za-z0-9_]*\.class)' native/MainActivity.java | sed 's/registerPlugin(//; s/\.class)//'); do
  [ -f "$D/$C.java" ] && echo "plugin $C present" || { echo "ERROR: MainActivity registers $C but $D/$C.java was not copied"; exit 1; }
done
echo "native plugins copied"

echo "== version code and signing =="
V=$(node -p "require('./package.json').version")
IFS=. read -r MA MI PA <<< "$V"
CODE=$((MA*10000 + MI*100 + PA))
sed -i "s/versionCode [0-9][0-9]*/versionCode $CODE/; s/versionName \"[^\"]*\"/versionName \"$V\"/" "$G"
grep -q "versionCode $CODE" "$G" && echo "versionCode $CODE, versionName $V" || { echo "ERROR: could not set versionCode"; exit 1; }
rm -f .ci-signing.env .ci-signing.jks
# V1.14.6: the key alias is optional. It is read from the keystore, and a half-set signing setup now stops the build
# instead of quietly falling back to a throwaway key (that fallback is what made updates refuse to install).
KS_HAVE_B64=0; KS_HAVE_PASS=0; KS_HAVE_ALIAS=0
[ -n "${FB_KEYSTORE_B64:-}" ] && KS_HAVE_B64=1
[ -n "${FB_KEYSTORE_PASSWORD:-}" ] && KS_HAVE_PASS=1
[ -n "${FB_KEY_ALIAS:-}" ] && KS_HAVE_ALIAS=1
if [ $((KS_HAVE_B64 + KS_HAVE_PASS + KS_HAVE_ALIAS)) -gt 0 ] && [ $((KS_HAVE_B64 + KS_HAVE_PASS)) -lt 2 ]; then
  echo "ERROR: signing is only partly set up. Needed: FB_KEYSTORE_B64 and FB_KEYSTORE_PASSWORD. FB_KEY_ALIAS is optional."
  echo "  FB_KEYSTORE_B64 set: $KS_HAVE_B64 (1 = yes)   FB_KEYSTORE_PASSWORD set: $KS_HAVE_PASS (1 = yes)   FB_KEY_ALIAS set: $KS_HAVE_ALIAS (1 = yes)"
  echo "  If a variable is marked Protected, only protected branches and tags can read it. This ref may not be protected."
  exit 1
fi
if [ "$KS_HAVE_B64" = 1 ] && [ "$KS_HAVE_PASS" = 1 ]; then
  KF="$PWD/.ci-signing.jks"
  printf '%s' "$FB_KEYSTORE_B64" | base64 -d > "$KF" 2>/dev/null
  [ -s "$KF" ] || { echo "ERROR: FB_KEYSTORE_B64 did not decode to a keystore. Create it with: base64 -w0 festers-signing.jks"; exit 1; }
  command -v keytool >/dev/null 2>&1 || { echo "ERROR: keytool is not available in this job, so the key alias cannot be checked"; exit 1; }
  KS_LIST="$(keytool -list -keystore "$KF" -storepass:env FB_KEYSTORE_PASSWORD 2>&1)" || {
    echo "ERROR: could not open the keystore. Check that FB_KEYSTORE_PASSWORD is the store password and that FB_KEYSTORE_B64 is the base64 of the .jks file."
    exit 1
  }
  KS_ALIASES="$(printf '%s\n' "$KS_LIST" | awk -F', ' '/PrivateKeyEntry/ {print $1}')"
  KS_COUNT=$(printf '%s\n' "$KS_ALIASES" | grep -c . || true)
  [ "$KS_COUNT" -ge 1 ] || { echo "ERROR: the keystore has no private key entry"; exit 1; }
  KS_ALIAS_USE=""
  if [ "$KS_HAVE_ALIAS" = 1 ]; then
    KS_ALIAS_USE="$(printf '%s\n' "$KS_ALIASES" | grep -ixF -- "$FB_KEY_ALIAS" | head -n 1 || true)"
    if [ -z "$KS_ALIAS_USE" ]; then
      echo "ERROR: FB_KEY_ALIAS does not match any key in the keystore. Keys found:"
      printf '%s\n' "$KS_ALIASES" | sed 's/^/  - /'
      exit 1
    fi
  elif [ "$KS_COUNT" -eq 1 ]; then
    KS_ALIAS_USE="$KS_ALIASES"
    echo "FB_KEY_ALIAS not set, using the only key in the keystore: $KS_ALIAS_USE"
  else
    KS_ALIAS_USE="$(printf '%s\n' "$KS_ALIASES" | grep -ixF -- festers | head -n 1 || true)"
    if [ -z "$KS_ALIAS_USE" ]; then
      echo "ERROR: FB_KEY_ALIAS is not set and the keystore has several keys. Set FB_KEY_ALIAS to one of:"
      printf '%s\n' "$KS_ALIASES" | sed 's/^/  - /'
      exit 1
    fi
    echo "FB_KEY_ALIAS not set, using the key named $KS_ALIAS_USE"
  fi
  {
    printf 'export FB_KS_FILE=%q\n' "$KF"
    printf 'export FB_KS_PASS=%q\n' "$FB_KEYSTORE_PASSWORD"
    printf 'export FB_KS_ALIAS=%q\n' "$KS_ALIAS_USE"
  } > .ci-signing.env
  chmod 600 .ci-signing.env "$KF"
  KS_FP="$(keytool -list -v -keystore "$KF" -storepass:env FB_KEYSTORE_PASSWORD -alias "$KS_ALIAS_USE" 2>/dev/null | awk '/SHA256:/ {print $2; exit}')"
  echo "signing certificate SHA-256: ${KS_FP:-unknown} (the same value on every build means updates install over each other)"
  grep -q "FB_KS_FILE" "$G" || sed -i '0,/^android {/s//android {\n    signingConfigs {\n        debug {\n            storeFile file(System.getenv("FB_KS_FILE"))\n            storePassword System.getenv("FB_KS_PASS")\n            keyAlias System.getenv("FB_KS_ALIAS")\n            keyPassword System.getenv("FB_KS_PASS")\n        }\n    }/' "$G"
  grep -q "FB_KS_FILE" "$G" && echo "persistent signing key in use" || { echo "ERROR: could not add the signing config"; exit 1; }
else
  echo "WARNING: no signing variables found (they may be Protected and this ref is not protected), so this build uses a throwaway debug key and cannot update over a build signed with a different key"
fi
echo "setup done"
