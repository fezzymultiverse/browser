# Building with GitLab CI

The pipeline is defined in `.gitlab-ci.yml`.

## Jobs
- `build_apk`: runs on pushes to `main`, on tags starting with `v`, and on web-triggered pipelines. Output: `festers-browser-debug.apk` as a job artifact (kept 1 year).
- `release_apk`: runs only for tags starting with `v` and creates a GitLab Release linking the APK.

## What `build_apk` does
1. Installs Node 22, JDK 21 (image `eclipse-temurin:21-jdk-jammy`) and `librsvg2-bin`.
2. `ci/install-android-sdk.sh`: Android command line tools, platform 35, build tools 35.0.0 (cached).
3. `npm install`, then `npx cap add android` generates the `android/` project.
4. `ci/android-setup.sh`: adds permissions and libraries, copies `native/*.java` into the generated project, sets versionCode and versionName from `package.json`, configures signing.
5. Generates icons and splash from `assets/icon.svg`, runs `npx cap sync android`.
6. `./gradlew assembleDebug`.

## Version numbers
`package.json` version `A.B.C` gives versionCode `A*10000 + B*100 + C`. Example: `1.23.4` is `12304`. Raise it for every build you want to install over an older one.

## Releasing
```bash
git tag v1.23.4
git push origin v1.23.4
```
Lowercase `v` is required.

## Variables (Settings > CI/CD > Variables)
| Name | Purpose |
|---|---|
| `FB_KEYSTORE_B64` | base64 of your `.jks` file |
| `FB_KEYSTORE_PASSWORD` | store and key password |
| `FB_KEY_ALIAS` | optional; read from the keystore if left out |
| `CONDUINT_TRUST_FESTERS_SHA256` | optional, for the Conduint passkey bridge |

If a variable is **Protected**, only protected branches and tags can use it. Protect `main` and `v*` tags, or untick Protected.

## Reading a failed job
Search the log for `What went wrong` and `error:`. Errors after `BUILD FAILED` (such as `cp: cannot stat ... app-debug.apk` or `No files to upload`) are results of the failure, not the cause. Always fix the first error.
