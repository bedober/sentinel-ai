# Full Termux fork and Sentinel dashboard

## Architecture

Sentinel AI is built from a pinned Termux Android application source revision. The fork supplies the terminal emulator, terminal session/service, bootstrap, package prefix and package manager inside one Android APK. No external Termux app is required.

The Android package id remains `com.termux` because the official bootstrap is compiled for `/data/data/com.termux/files/usr`. A unique package id is a separate bootstrap/package-rebuild project, not a manifest-only change.

## Dashboard integration milestone

The build script injects `com.termux.app.SentinelDashboardActivity` into the fork and makes it the launcher. The original `com.termux.app.TermuxActivity` remains available as the terminal screen.

The dashboard currently includes:
- An in-app terminal launch action.
- An Nmap installation action using the fork's own `pkg` command.
- A graphical Nmap TCP-connect scan form.
- Hostname/IPv4 validation, IPv4 octet validation, numeric port validation, maximum 32 port entries and a maximum of 4,096 ports per scan.
- Argument-array command dispatch through the app's internal `TermuxService`; no shell string is built from target input.

This is the first vertical slice, not completion of the 21-tool catalogue. The dashboard now includes a status/output panel and 21 named catalogue entries; only Nmap is wired to a GUI command workflow, and its output handoff is not yet device-verified. The Nmap package installation and scan handoff must be validated on physical Android hardware before being described as device-tested.

## Next integration work

1. Build and inspect the APK; verify the dashboard is the launcher and the terminal still opens.
2. Install on ARM64 Android 12 hardware; test first bootstrap, Nmap install and a scan against an explicitly authorized test host.
3. Add result capture/status reporting to the dashboard rather than relying only on terminal output.
4. Keep the catalogue explicit: only Nmap has a GUI workflow; the other 20 are marked not integrated, external-service/license dependent, target-host-only or hardware/root constrained.
5. Add a captured command-output/status panel and verify it with Nmap on Android 12.
5. Keep remote worker and server-only tools visibly distinct from local Android execution.

## Safety and licensing

Use TCP connect scanning only against authorized systems. Preserve Termux GPL-3.0-only source and notices, including component-specific exceptions. Do not bundle proprietary scanners or imply unsupported root/hardware features are available.


## Signed test APK

CI can optionally sign the debug test APK when the owner configures a private keystore in the four `SENTINEL_RELEASE_*` GitHub Actions secrets. See [Android signing setup](ANDROID_SIGNING.md). This produces a signed test artifact only; a production release variant and physical Android 12 validation remain outstanding.
