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


## Linux worker deployment

For a self-hosted Linux worker, configure `.env` from `.env.server.example`. Set a high-entropy `SENTINEL_WORKER_TOKEN` and an administrator-controlled `SENTINEL_ALLOWED_CIDRS` allowlist. Start the private worker with:

```bash
docker compose up -d --build api worker
```

The API reaches the worker over the internal Compose network. Do not publish port 8090. The first adapter is an explicitly authorized Nmap TCP connect/version scan, with a 256-port cap and a 180-second timeout. The worker container runs non-root, drops Linux capabilities and does not mount the Docker socket. The target must resolve entirely inside the configured allowlist.

See [worker/README.md](../worker/README.md) for configuration and safety details. The worker is not a general-purpose remote shell. Before exposing the API, add HTTPS, user authentication, rate limiting, audit logs, and per-account authorization.

Nmap uses its own Nmap Public Source License. It is free for end users, but its maintainers require an OEM licence for redistribution or embedding in commercial products. Review [the Nmap licence](https://nmap.org/npsl/) and [OEM terms](https://nmap.org/oem/) before shipping a product bundle.
