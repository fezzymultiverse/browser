cd "$CI_PROJECT_DIR"
[ -n "$KEYSTORE_BASE64" ] || { echo "no keystore"; exit 0; }
echo "$KEYSTORE_BASE64" | base64 -d > /tmp/release.keystore
AS="$(ls -d $ANDROID_HOME/build-tools/* | sort -V | tail -1)/apksigner"
D=android/app/build/outputs/apk/debug
"$AS" sign --ks /tmp/release.keystore --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEYSTORE_PASSWORD --out $D/app-signed.apk $D/app-debug.apk
