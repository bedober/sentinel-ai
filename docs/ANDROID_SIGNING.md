# Android APK signing setup

The repository never stores a signing keystore or passwords. Generate a private upload/release key on a trusted computer and keep a secure offline backup. Never commit the keystore or password files.

Example (choose your own secure passwords and alias):

```bash
keytool -genkeypair -v -keystore sentinel-release.jks -alias sentinel-release -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 sentinel-release.jks
```

Add these four GitHub Actions repository secrets under **Settings → Secrets and variables → Actions**:

- `SENTINEL_RELEASE_KEYSTORE_BASE64`: base64 output of the keystore
- `SENTINEL_RELEASE_KEY_ALIAS`: alias used when creating the key
- `SENTINEL_RELEASE_STORE_PASSWORD`: keystore password
- `SENTINEL_RELEASE_KEY_PASSWORD`: private-key password

The workflow creates a separately named `sentinel-ai-signed-test-apk` artifact only when all four secrets are present, then verifies its APK signature with Android `apksigner`. Without the secrets, normal CI continues and signing is explicitly skipped.

**Important release limitation:** the current upstream fork build target is `:app:assembleDebug`. The signed artifact is therefore a signed test build, not yet a production-optimized `assembleRelease` build or Play Store release. Before public distribution, add and validate a dedicated release variant, confirm versioning and update/signature continuity, test installation and upgrade on Android 12 hardware, and publish matching GPL source/notices. The private key must remain under the project owner's control.
