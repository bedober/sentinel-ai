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

This is the first vertical slice, not completion of the 21-tool catalogue. The Nmap package installation and scan handoff must be validated on physical Android hardware before being described as device-tested.

## Next integration work

1. Build and inspect the APK; verify the dashboard is the launcher and the terminal still opens.
2. Install on ARM64 Android 12 hardware; test first bootstrap, Nmap install and a scan against an explicitly authorized test host.
3. Add result capture/status reporting to the dashboard rather than relying only on terminal output.
4. Add tool cards only after package availability and adapter behavior are confirmed.
5. Keep remote worker and server-only tools visibly distinct from local Android execution.

## Safety and licensing

Use TCP connect scanning only against authorized systems. Preserve Termux GPL-3.0-only source and notices, including component-specific exceptions. Do not bundle proprietary scanners or imply unsupported root/hardware features are available.
