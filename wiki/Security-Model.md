# Security Model

## What is protected
- **Vault**: AES-256-GCM, key from PBKDF2-SHA256 with 600,000 rounds. Stays on the device.
- **Backups**: AES-256-GCM, PBKDF2-SHA256 with 250,000 rounds. The password is never stored.
- **Breach check**: only the first 5 hex characters of the SHA-1 hash are sent (k-anonymity).
- **Pages**: sandboxed iframes with no referrer; NoScript removes `allow-scripts`.
- **Certificates**: forged or self-signed certificates fail. "Continue anyway" needs your decision, shows issuer, dates and SHA-256, and applies per site.
- **Network security config**: trusts the phone's built-in authorities and any authority you install yourself.

## What is not protected
- No built-in VPN tunnel (needs Android VpnService). The VPN tab only verifies an external VPN.
- The 4-digit PIN has 10,000 combinations; attempt limits protect it, not its strength. Fingerprint and master password are the strong factors.
- Media Downloader cannot save blob: streams, HLS/DASH playlists or DRM content.
- Debug builds are not hardened release builds.

## Reporting a problem
See SECURITY.md in the repository. Use a confidential issue.
