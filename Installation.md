# Installation

## Get the APK
1. Open the project on GitLab, then **Build > Pipelines** (or **Jobs**).
2. Open the latest green `build_apk` job.
3. Click **Download** under Job artifacts, or **Browse** and take `festers-browser-debug.apk`.
4. For tagged releases (`v1.23.4` and so on), use **Deploy > Releases**.

## Install on the phone
1. Copy the APK to the phone and open it.
2. Android will ask you to allow installs from that app (browser or file manager). Allow it.
3. Install.

## Updating
An update installs over the old app only when it is signed with the **same key**. See [[Signing Key]]. If Android says the package conflicts, the key changed: export your encrypted vault backup first, uninstall, then install.

## Make it your default browser
Android Settings > Apps > Default apps > Browser app > Festers Browser.
