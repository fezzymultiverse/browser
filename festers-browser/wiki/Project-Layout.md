# Project Layout

```
www/index.html            the app (one file)
native/*.java             Android plugins and MainActivity, copied into android/ by CI
ci/android-setup.sh       patches the generated Android project, sets version and signing
ci/install-android-sdk.sh installs the Android SDK pieces
ci/network_security_config.xml
assets/                   icon.svg, bojack.svg
capacitor.config.json     appId com.fezzy.festersbrowser
package.json              version and Capacitor 7 plugins
.gitlab-ci.yml            build_apk and release_apk
.gitlab/                  issue templates, merge request template, CODEOWNERS
wiki/                     source for this wiki
CHANGELOG.md  HANDOFF.md  CONTRIBUTING.md  SECURITY.md  CODE_OF_CONDUCT.md  LICENSE
```

`android/` is generated on every build and is git-ignored.
