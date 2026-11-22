# Sentinel AI — Standalone Android

Sentinel AI is a native Android application. It does **not** require Termux, a separately installed terminal, Android root, or an external Linux distribution.

## What is included

- Kotlin + Jetpack Compose Android interface.
- Built-in terminal and graphical tool catalogue.
- Bundled ARM64 Android PRoot runtime, built in CI from pinned upstream source and accompanied by source/licence notices.
- Alpine Linux 3.24.4 minirootfs downloaded on first setup into Sentinel's private app storage.
- SHA-256 verification against Alpine's published checksum before rootfs extraction.
- Package installation through Alpine's signed repositories.
- On-device passive HTTPS posture assessment.
- A bounded local Nmap TCP-connect adapter, subject to package availability and Android networking limits.

## Install and first launch

1. Open the repository's **Actions** tab.
2. Open the latest successful **Sentinel AI CI and Android APK** workflow run.
3. Download the `sentinel-ai-standalone-debug-apk` artifact.
4. Extract the artifact and install `app-debug.apk` on a compatible ARM64 Android device.
5. Open Sentinel and select **Terminal → Install / Repair Linux Environment**.
6. Keep an internet connection available while the Alpine root filesystem is downloaded and verified.
7. After the startup self-test succeeds, use the built-in Terminal and Tools tabs.

The debug APK is for testing, not a production-signed release. Android may require the user to allow installation from the selected file manager/browser.

## Device and storage requirements

- Current build target: `arm64-v8a` (64-bit ARM).
- Minimum Android API: 26.
- First setup requires internet access.
- Allow several hundred megabytes for the Linux root filesystem and additional space for installed tools. Exact usage depends on the package selection.
- No root access or Termux installation is required.

## Honest tool status

The graphical catalogue lists all 21 requested security products, but catalogue entries are not proof that their engines are installed or executable. The current local execution adapter is limited; most entries remain pending, require a compatible upstream package, dedicated service, hardware, or a separate commercial licence. Nessus and Burp Suite are not redistributed by Sentinel. OpenVAS/Greenbone, BloodHound and BeEF need separate service deployments. Raw packet access, monitor mode, injection, GPU acceleration and kernel-dependent tools may not work on stock Android.

The app must display an unavailable/pending state rather than simulate a successful run. Only run assessments against systems you own or are explicitly authorized to test.

## Current limitations

- The terminal is pipe-backed, not a full PTY terminal emulator. Terminal resizing, reliable Ctrl-C, shell job control and full-screen terminal applications are not supported yet.
- PRoot is a userspace compatibility layer; it does not provide a separate Linux kernel or root privileges.
- The app's Android PRoot and Alpine bootstrap still require physical-device validation. A successful CI build alone does not prove compatibility with every Android vendor device.
- The current APK is ARM64-only and is not production-signed.

## Build from source

The GitHub Actions workflow:
1. Checks out a pinned Android PRoot builder revision.
2. Builds the ARM64 PRoot runtime and embeds it in the APK with upstream source and licence notices.
3. Builds the Android app with Gradle.
4. Verifies that the PRoot executable, loader files and source archive are present in the APK.
5. Uploads the debug APK as a 14-day workflow artifact.

The build is reproducible from the repository workflow, but the output remains a debug build until a production signing configuration is supplied.

See [standalone architecture](../docs/ANDROID_STANDALONE_ARCHITECTURE.md) and [third-party notices](THIRD_PARTY_NOTICES.md).
