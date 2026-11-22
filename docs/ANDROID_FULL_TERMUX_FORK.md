# Full Termux fork integration

Sentinel AI is moving from the custom PRoot/Alpine prototype to a **full Termux application-source fork**. The fork supplies the Android terminal emulator, long-running terminal service, bootstrap installation, shell prefix, and `pkg`/APT package manager. The Sentinel security console remains project-owned and will be integrated into this application rather than requiring another installed app.

## Reproducible upstream base

- Upstream: https://github.com/termux/termux-app
- Pinned source commit: `8629e632fcb95da272221be327db653fb24befe9`
- Package build system: https://github.com/termux/termux-packages
- Upstream app licence: GPL-3.0-only. See upstream `LICENSE.md` and `termux-shared/LICENSE.md` for component exceptions.
- The build script preserves upstream licence and copyright files and writes a fork notice into the build tree.

Run `scripts/build-termux-fork.sh` to clone the pinned source, apply Sentinel branding and build a standalone debug APK. GitHub Actions will run the same script and publish the fork APK as a separate test artifact.

## Standalone install and package identity

The fork is a complete Android application. The user does not install Termux separately, and Sentinel does not invoke an external Termux package or intent.

**Important package-prefix constraint:** official Termux bootstrap binaries are compiled with the prefix `/data/data/com.termux/files/usr`. The initial fork therefore retains Android application id `com.termux` while changing the visible brand to Sentinel AI. This makes the APK self-contained, but it cannot coexist with the official Termux app on the same Android profile. If Android reports a package/signature conflict, uninstall the official Termux app before installing this fork. A unique Sentinel application id requires a full rebuild of the Termux bootstrap and package repository with a matching prefix; changing only Gradle's application id would break the shell.

## Integration stages

1. **Forked terminal foundation:** upstream terminal emulator, terminal sessions/service, bootstrap and package manager built into the Sentinel-branded APK.
2. **Sentinel console integration:** migrate the current Compose security dashboard and local tool adapters into the fork's Android application, with a dashboard launcher and an in-app terminal entry point.
3. **Tool packaging:** install compatible open-source CLI tools through signed package repositories; mark server-dependent, proprietary, root-only and hardware-dependent tools accurately.
4. **Release hardening:** add reproducible builds, source/license manifests, device smoke tests, release signing and artifact verification.

The existing `android/app` Compose/PRoot prototype is retained during migration and is not evidence that the Termux fork's dashboard integration is complete. Do not describe all 21 catalogue entries as installed or working until each adapter is tested on-device.
