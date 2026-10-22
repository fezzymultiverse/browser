# Troubleshooting

## Build problems
| Symptom | Likely cause and fix |
|---|---|
| `error: ';' expected` / `illegal start of expression` in a `.java` file | Typo in native code, such as a comment without `//`. Fix the first line named. |
| `cp: cannot stat ... app-debug.apk` | Gradle failed earlier. Scroll up to `What went wrong`. |
| `No files to upload` | Same cause: no APK was built. |
| `SDK location not found` / SDK components fail | Check `ci/install-android-sdk.sh` output. |
| `OutOfMemoryError` | Raise `-Xmx` in `GRADLE_OPTS` in `.gitlab-ci.yml`. |
| `signing certificate` differs between builds | Variables missing or not available to the branch (Protected). See [[Signing Key]]. |

## App problems
- **App will not update, "conflicts with existing package"**: the signing key changed. Export an encrypted vault backup, uninstall, reinstall.
- **Black page or page will not load**: Settings > Autofill & Passkeys > Check status, and the Page check panel.
- **"Secure connection failed"**: open the error bar to see issuer, dates and SHA-256, then decide on Continue anyway for that site only.
- **Downloads missing**: files go to `Downloads/Festers`.
- **Passkey prompt does nothing**: see [[Passkeys]].
