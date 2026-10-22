# Festers Browser

A private, security-first mobile browser in a single HTML file, packaged for Android with Capacitor. Mascot: Bojack, a Labrador-golden retriever mix with a spiked security collar (assets/bojack.svg).

## Features
- Security posture: Shields, Tracker protection, NoScript, HTTPS-only, Threat protection and VPN guard, each with an on/off switch and live stats
- Shields: live tracker blocking with a toolbar counter and panel (native view)
- Two themes: Festers Light (orange and red, black outlines, black fade) and Festers Dark, switched from the home screen
- Burner windows (private tabs), Profile with vault sync status, device passkey (fingerprint / screen lock)
- Full-setup zip backup and import, plus a password-locked .festers backup (no email, nothing leaves the phone)
- Menu with collapsible groups (Page, Tools, Extensions, Data and view): site controls, recent tabs, zoom, find, translate, reading mode, desktop site, install shortcut, help
- Optional top bar (tabs + toolbar) and bottom bar; hide either one and a red menu button keeps everything reachable
- Settings as a vertical list (Firefox/Edge style) with orange highlights; Vault, Notes and Secrets live in Settings
- Security dashboard with a live posture score (HTTPS-only, tracker mode, NoScript, vault, auto-lock, auto-clear)
- Tabs, private tabs (isolated storage, no history), bangs, bookmarks, history, Scorch
- NoScript mode with per-site allow list
- Encrypted password vault with TOTP, password generator, CSV import
- Breach checker (Have I Been Pwned, k-anonymity) for single passwords or the whole vault
- DNS-over-HTTPS lookup, Private DNS hostnames, VPN verification (IP before/after)
- Offline pages: cleaned copies with scripts removed
- Encrypted backup you can email to yourself; restore from file
- Update checker (GitHub Releases) and registration
- Media player: audio, video, images; optional native timed wake lock for extended playback
- Android Permissions settings: notification request, default-browser selection, battery settings, app settings and timed media wake lock
- HTTP/HTTPS intent filters so Android can offer Festers Browser as the default browser; optional native partial wake lock for extended playback
- Vault quick unlock: fingerprint (Android Keystore + BiometricPrompt) and an optional 4-digit PIN with attempt limits
- Media Downloader extension: find and save video, audio, images and PDFs from the page you are on; long-press Save image
- Notepad, extensions (Wayback, Archive.today, VirusTotal, WHOIS, SSL Labs, DNS check, custom)
- Groq AI assistant (bring your own keys, key health meter, private by default)

## Passkey bridge (V1.19.5, build-fixed in V1.20.2)

- Native `FBCredentialPlugin` exposes `createPasskey({ requestJson })` and `getPasskey({ requestJson })` through AndroidX Credential Manager.
- An additional Android BiometricPrompt gate runs before the Credential Manager operation. Credential Manager can still display its own system UI.
- The JavaScript receives `registrationResponseJson` or `authenticationResponseJson`; send these to your relying-party server for WebAuthn verification. Never treat a local callback as server-side proof of identity.
- This app is a passkey relying party, not a credential-provider app. Do not register a `CredentialProviderService` unless implementing a provider that supplies credentials to other apps.
- The Android CI workflow already adds AndroidX Credential Manager and Biometric libraries to the generated Android project.

Example:
```js
const passkeys = Capacitor.Plugins.FBCredentialPlugin;
const created = await passkeys.createPasskey({
  requestJson: JSON.stringify(serverCreationOptions)
});
const signedIn = await passkeys.getPasskey({
  requestJson: JSON.stringify(serverRequestOptions)
});
```

## Android permissions and media notes
- Android permissions are not silently granted: the user must approve notification prompts and choose the default browser in Android settings.
- File access stays scoped to the app Downloads/Festers area and Android file-picker flows; this does not request unrestricted access to all storage.
- Battery optimisation settings open Android system settings; users decide whether to exempt the app.
- The media wake lock is temporary (up to four hours per activation) and is not a full background-media notification/service. Media websites may still restrict background playback or DRM.

## Honest limits
- Media Downloader cannot save blob: streams (most large video sites), HLS/DASH playlists (copy link only) or DRM content.
- The 4-digit PIN is a convenience: it has only 10,000 combinations, so it is protected by attempt limits (5 wrong tries erase it) rather than by strength. The fingerprint and master password are the strong factors.
- Pages load in iframes; sites that forbid framing open in the in-app browser.
- No built-in VPN tunnel (needs Android VpnService). The VPN tab verifies an external VPN.
- Exports save to Downloads/Festers through the native file plugin. In a plain browser they fall back to a normal download.
- Email breach lookups need a paid HIBP key, so that button opens haveibeenpwned.com.
- Update registration has no server; it stores your email locally and can send a pre-filled email.

## Project layout
```
www/index.html            the app
capacitor.config.json     appId com.fezzy.festersbrowser
package.json              Capacitor 7 + browser/share/filesystem plugins
assets/                   icon.svg, icon.png, splash.png
.github/workflows/        APK build + release
```

## V1.12.4
GitLab CI added (`.gitlab-ci.yml`, `ci/android-setup.sh`, `ci/install-android-sdk.sh`). In GitLab go to Settings > CI/CD > Variables and add `FB_KEYSTORE_B64`, `FB_KEYSTORE_PASSWORD` and `FB_KEY_ALIAS` (same values as in V1.12.3 below). If you mark them Protected, only protected branches and tags (main and v* tags, once protected) can use them; an unprotected ref builds with a throwaway key and prints a warning. Push to `main` to build; the APK is the `festers-browser-debug.apk` job artifact. Tag `v1.21.6` to also create a GitLab release. The in-app update check still reads the GitHub repo in `CONFIG.repo`. Not yet run on GitLab.

## V1.12.3
Updates install over the old app once the persistent signing key is set up. One-time setup: in Termux run `pkg install openjdk-17`, then `keytool -genkeypair -v -keystore festers-signing.jks -alias festers -keyalg RSA -keysize 2048 -validity 10000` (use the same password for the store and the key), then `base64 -w0 festers-signing.jks`. Save the output as CI secret FB_KEYSTORE_B64, the password as FB_KEYSTORE_PASSWORD and the alias (festers) as FB_KEY_ALIAS. Keep the .jks file backed up somewhere private and never commit it: if it is lost, the next update needs an uninstall again. Moving to this key from an older build needs one uninstall, so export the encrypted vault backup first.

## V1.12.2
Downloads manager View and folder browsing, notification permission result, direct battery exemption, wake lock as a foreground service with a Stop button, and zip extraction MIME fix. See CHANGELOG.md. Device build and runtime tests are still required.

## Configure
Edit `CONFIG` near the top of the script in `www/index.html`:
```js
const CONFIG={repo:'philfesters/festers-browser',registerEmail:''};
```

## Build the APK (recommended: GitHub Actions)
1. Create the repo `festers-browser` on GitHub and push this folder.
2. Actions tab > "Build Android APK" > Run workflow. Download the artifact.
3. Tag `v1.21.6` (lowercase v, the workflow matches `v*`) to publish the APK as a GitHub Release (the in-app update check reads it).

## Build locally (PC with Android Studio, JDK 21, Node 22)
```bash
npm install
npx cap add android
rsvg-convert -w 1024 -h 1024 assets/icon.svg -o assets/icon.png && cp assets/icon.png assets/icon-only.png
rsvg-convert -w 2732 -h 2732 assets/icon.svg -o assets/splash.png
npm run assets
npx cap sync android
npx cap open android   # or: cd android && ./gradlew assembleDebug
```
Building Android on rootless Termux is not practical; use Actions.

## Git
```bash
git init && git add . && git commit -m "Festers Browser 1.20.2"
git branch -M main
git remote add origin https://github.com/philfesters/festers-browser.git
git push -u origin main
```

## License
MIT. See LICENSE.

V1.14.6 signing note: in GitLab only FB_KEYSTORE_B64 and FB_KEYSTORE_PASSWORD are required. FB_KEY_ALIAS is optional and is read from the keystore. If a variable is Protected, run the pipeline on a protected branch or tag, or untick Protected. The job log prints the signing certificate SHA-256; it must be the same on every build for updates to install over each other.

V1.14.7 black pages and passkeys: see CHANGELOG. After installing, open Settings > Autofill & Passkeys > Check status and read the status line; it says whether the WebView offers passkeys, whether the app-name header is hidden, and why a page last stopped.

V1.16.0 device dashboard (battery, RAM, network, storage, CPU): see CHANGELOG.
V1.15.0 Gemini + Groq provider layer, routing, bulk key add, save-password bar and key button: see CHANGELOG. Add a Groq and a Gemini key under Settings > AI Providers & Keys, then use Test every key.

V1.20.2 build fix: the passkey plugin now uses Credential Manager's Java callback methods (`createCredentialAsync`, `getCredentialAsync`). V1.20.0/V1.20.1: Back/Forward buttons, tab pin/groups, recently closed tabs, Downloads search/filters. See CHANGELOG. Real build and device tests are still required.

V1.21.0: AI keys can be imported from a file (Settings > AI Providers & Keys > From a file); `intent://` links fall back to their web address when no app handles them. See CHANGELOG.

V1.21.1: Google sign-in pages automatically use Chrome-style browser hints. See CHANGELOG.
