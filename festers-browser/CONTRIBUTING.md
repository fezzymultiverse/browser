# Contributing

Thanks for helping with Festers Browser. By contributing you agree your work is released under the MIT License (see LICENSE).

## Ground rules
- Small, focused merge requests. One fix or feature each.
- The app is one file: `www/index.html`. Native Android code lives in `native/` and is copied into the generated project by `ci/android-setup.sh`. Keep edits surgical.
- Never edit the generated `android/` folder; it is rebuilt on every CI run and is git-ignored.
- Never commit signing keys, `.jks` files, passwords, API keys or `.ci-signing.*` files.
- Say what you tested. If you could not compile or run on a device, say so.

## Before you open a merge request
1. JavaScript: extract the page script and run `node --check` on it, or open `www/index.html` in a browser and check the console.
2. Java: make sure it compiles. A stray character breaks the whole build (V1.23.3 failed this way). If you cannot build, at least check braces and that every comment starts with `//` or `/* */`.
3. Add a line to `CHANGELOG.md` under a new version heading, and bump `version` in `package.json` (the versionCode is derived from it: `major*10000 + minor*100 + patch`).
4. Update `HANDOFF.md` with what changed and what is still untested.

## Build
CI builds on every push to `main`. See the wiki page "Building with GitLab CI" for variables and tagging.

## Reporting bugs and ideas
Use the issue templates (Bug, Feature request). For security problems do not open a public issue; see SECURITY.md.

## Conduct
Be kind. See CODE_OF_CONDUCT.md.
