# Signing Key

Android only installs an update over an app when both are signed with the same key. The CI prints the signing certificate SHA-256 on every build. **It must be identical every time.**

## One-time setup (Termux)
```bash
pkg install openjdk-17
keytool -genkeypair -v -keystore festers-signing.jks -alias festers \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 festers-signing.jks
```
Use the same password for the store and the key. Save the base64 output as `FB_KEYSTORE_B64` and the password as `FB_KEYSTORE_PASSWORD` in GitLab CI/CD variables. See [[Building with GitLab CI]].

## Rules
- Never commit the `.jks` file or the passwords.
- Keep a private backup of the `.jks`. If it is lost, the next update needs an uninstall first.
- If the job log says "throwaway key", the variables were not available to that branch (check Protected).
