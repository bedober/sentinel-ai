# Deployment notes

Deploy the backend container to a trusted host. Set CORS_ORIGINS to the exact dashboard origins. Set HF_TOKEN and HF_MODEL only when using optional Hugging Face explanations.

Do not expose an unrestricted scanner publicly. Before public launch add authentication, account rate limits, audit logs, verified asset ownership, outbound network egress restrictions, DNS-rebinding protections, and abuse monitoring. This starter performs passive checks; it is not a complete penetration-testing engine.

## Android build
Set API_BASE_URL in android/app/build.gradle.kts to your deployed HTTPS API base URL, then run:
```bash
cd android
gradle assembleDebug
```
APK output: app/build/outputs/apk/debug/app-debug.apk.

GitHub Actions builds the debug APK on pushes to main and uploads it as the sentinel-ai-debug-apk artifact. Debug builds are for testing. A public production release needs a privately stored signing key and a signed release build.
