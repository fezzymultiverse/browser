# Passkeys

## Web passkeys
Switched per site in Settings > Autofill & Passkeys. On by default; Google, GitHub and GitLab are on the skip list (editable) because their own sign-in works better without the bridge.

## Conduint bridge
Festers hands passkey create/get requests to Conduint (V0.50.9 or newer) through `FBWebPlugin.PkBridge`. Conduint shows the Festers certificate and pins it after one confirmation. Nothing needs to be pasted.

The bridge steps aside (normal `navigator.credentials` runs) when:
- the tab is a burner tab
- the site is on the skip list
- Web passkeys is off
- the page is not https
- the request is a conditional-UI request
- Conduint is missing or too old

## Native Credential Manager plugin
`FBCredentialPlugin` exposes `createPasskey({ requestJson })` and `getPasskey({ requestJson })` using AndroidX Credential Manager, with a BiometricPrompt gate first. Send the returned JSON to your relying-party server for verification. A local callback is not proof of identity.

## Checking status
Settings > Autofill & Passkeys > Check status. It shows On/Off pills for the passkey app, Web passkeys and the autofill service, and what to do next.
