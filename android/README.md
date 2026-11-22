# Sentinel AI — Full Termux fork for Android

Sentinel AI is being built as a **standalone Android application based on the full Termux app source**. The fork includes its own terminal emulator, terminal sessions/service, bootstrap installation and package manager. Users install one Sentinel AI APK; they do not install or launch a separate Termux app.

## Current full-fork build

- Pinned upstream Termux application source: `8629e632fcb95da272221be327db653fb24befe9`.
- Sentinel AI app branding and project links applied during the source build.
- Official Termux bootstrap and signed package repositories retained for shell compatibility.
- Debug APK built from source by GitHub Actions and uploaded as `sentinel-ai-full-termux-fork-debug-apk`.
- The existing Compose/PRoot implementation remains in this repository as a migration prototype; it is not the primary Android artifact.

Build locally with:

```bash
bash scripts/build-termux-fork.sh
```

The script clones the pinned upstream source, applies the Sentinel branding patch, preserves upstream licence files and builds the standalone APK using the upstream Gradle wrapper.

## Package identity constraint

The official Termux bootstrap binaries have `/data/data/com.termux/files/usr` compiled into them. The initial fork therefore keeps Android application id `com.termux` while changing the visible app name to Sentinel AI.

This means:
- Sentinel AI is standalone and does not depend on a separately installed Termux application.
- It cannot coexist with the official Termux app on the same Android profile.
- If Android reports a package/signature conflict, uninstall the official Termux app before installing this fork.
- A unique package id such as `ai.sentinel.app` is not a safe Gradle-only change. It requires rebuilding the Termux bootstrap and all packages with a matching custom prefix.

## Install and first launch

1. Open the repository's **Actions** tab.
2. Open the latest successful **Sentinel AI CI and Android APK** workflow run.
3. Download the `sentinel-ai-full-termux-fork-debug-apk` artifact.
4. Extract and install the APK on an Android device supported by the upstream build.
5. Launch Sentinel AI and allow its built-in bootstrap installer to complete.
6. Use the terminal and `pkg` package manager inside the app.

This is a debug build, not a production-signed release. A successful CI build does not replace physical-device testing.

## Sentinel console and security tools

The current fork milestone supplies the terminal foundation. The next integration stage is to migrate the existing Sentinel Compose security dashboard into the forked Android application and add an in-app route between the graphical console and terminal.

The repository's 21-tool catalogue is not proof that all engines are installed or executable. Each adapter must be connected and tested. Some products require Linux servers, dedicated services, compatible hardware, Android root capabilities or commercial licences. Nessus and Burp Suite are not redistributed by Sentinel. Do not mark a tool functional until an actual execution test passes.

Only assess systems you own or are explicitly authorized to test.

## Licensing

The Termux app source is GPL-3.0-only. Component-specific exceptions are listed in upstream `LICENSE.md` and `termux-shared/LICENSE.md`. The build retains those files and source headers and generates a fork notice. See [third-party notices](THIRD_PARTY_NOTICES.md).

The Sentinel-specific project code retains its own licence, but the full Android fork must comply with the upstream GPL and all third-party component licences.
