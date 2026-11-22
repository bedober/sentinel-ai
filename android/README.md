# Sentinel AI Android

Sentinel AI is an Android security workspace under active development. The current APK provides an **on-device passive HTTPS posture assessment** and an installer for an app-private Alpine Linux root filesystem.

## Installable build

The latest CI build is produced by the `.github/workflows/build.yml` workflow as the artifact `sentinel-ai-debug-apk`. It is a debug APK for testing, not a signed production release. Download it from the repository's Actions run.

## Current Android capabilities

- Website assessment runs locally on the phone; no hosted Sentinel API is required.
- Checks HTTPS response status, DNS resolution, certificate validity/expiry, common security headers, clickjacking headers, cookie attributes and server banner disclosure.
- Downloads the official Alpine minirootfs and verifies its published SHA-256 before extraction into private app storage.
- Does not request Android root access and does not depend on Termux.

## Not yet complete

The standalone Linux environment is **not yet a complete Termux replacement**. The current app does not yet provision a compatible PRoot executable, provide an interactive PTY shell, install packages through a verified package-manager UI, or include the full security-tool catalogue. The terminal remains unavailable until those components are implemented and tested on physical devices.

## Safety

The website assessor performs conservative passive checks only. It does not exploit vulnerabilities, brute-force logins, crawl private paths, or run destructive tests. Only assess systems you own or are authorized to test.
