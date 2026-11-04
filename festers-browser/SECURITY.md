# Security Policy

## Reporting a vulnerability
Please do not open a public issue for a security bug. On GitLab, open a **confidential issue** (tick "This issue is confidential" when creating it), or contact the maintainer privately through the project owner's profile. Include the app version (Settings > About), your Android version, what you did, and what happened.

## What to expect
You will get an acknowledgement, and the fix will be listed in CHANGELOG.md once released. This is a one-person project, so timing varies.

## Supported versions
Only the latest release is supported. Update before reporting if you can.

## Design notes
- Vault: AES-256-GCM, PBKDF2-SHA256 (600,000 rounds). Nothing leaves the device.
- Backups: AES-256-GCM, PBKDF2-SHA256 (250,000 rounds), password never stored.
- Breach check: only the first 5 hex characters of a SHA-1 hash are sent.
- Pages run in sandboxed iframes with no referrer. NoScript removes `allow-scripts`.
- Updates install over each other only when every build is signed with the same persistent key. Keep that key private and backed up.

## Known limits
- Pages load in iframes, so sites that send frame-blocking headers open in the in-app browser instead.
- There is no built-in VPN tunnel. That needs a native Android VpnService.
- The 4-digit PIN is a convenience with attempt limits, not a strong factor.
- Debug APKs are built by CI. They are signed with your key but are debug builds.
