# FAQ

**Is it open source?** Yes. MIT licence, see LICENSE.

**Does it send my data anywhere?** The vault and backups stay on the device. Features that call outside services (breach check, DNS-over-HTTPS lookup, AI assistant with your own keys, update check) do so only when you use them.

**Why a debug APK?** CI builds `assembleDebug`. It is signed with your persistent key so updates install over each other.

**Where is the update check?** It reads the repo set in `CONFIG.repo` near the top of the script in `www/index.html`. Change it if you host releases elsewhere.

**Can I build on my phone?** Not practically on rootless Termux. Use GitLab CI.

**How do I bump the version?** Edit `version` in `package.json`; the versionCode follows. See [[Building with GitLab CI]].
