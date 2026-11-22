# Sentinel Standalone Android Linux Environment

## Product requirement

Sentinel is a standalone Android application. Installing and opening Sentinel must not require Termux, another terminal app, root access, or a separately installed Linux distribution.

The Android application owns its terminal UI, session manager, package catalogue, workspace, environment lifecycle, and tool inventory.

## Runtime architecture

- **Native Android host:** Kotlin + Jetpack Compose. Owns permissions, storage, downloads, environment status, terminal tabs, and lifecycle.
- **Linux userland:** Alpine Linux root filesystem installed in Sentinel's private app storage. It is a userland, not a new kernel or a virtual machine.
- **Process compatibility:** a reviewed, ABI-specific PRoot executable launches the Linux userland without root. PRoot and every bundled binary must be built or obtained from a trusted upstream release and kept with its licence notices.
- **Terminal engine:** an original Sentinel PTY/session layer connects the native terminal view to interactive shell processes. It must support resize, stdin/stdout, Ctrl-C, working directories, history, and session cleanup. The current Python HTTP command console is a prototype and is not this engine.
- **Package manager:** Alpine `apk` inside the private root filesystem. Sentinel adds an original UI/catalogue for install, update, remove, and licence inspection; it must not silently bypass upstream package signatures.
- **Workspace:** app-private persistent workspace by default. User-selected shared folders use Android Storage Access Framework; do not give the Linux process unrestricted Android storage access.
- **Tool catalogue:** a versioned manifest maps each tool to its Alpine package, upstream project, licence, supported ABI, install size, and verification status.

## Initial tool catalogue

The first reviewed catalogue should cover these categories (availability depends on the selected Alpine release and Android ABI):

| Category | Candidate packages/tools |
|---|---|
| Shell and core | bash, busybox, coreutils, findutils, grep, sed, gawk, less, util-linux |
| Languages | python3, py3-pip, nodejs, npm, ruby, perl |
| Development | git, openssh, curl, wget, jq, make, clang, cmake |
| Network diagnostics | iproute2, bind-tools, whois, nmap, tcpdump (where supported) |
| Web/security review | nmap, nikto, testssl.sh, whatweb, nuclei, semgrep, trivy, zap (resource-heavy; some require separate runtimes) |
| Editors | nano, vim |
| Archive/files | tar, gzip, xz, zip, unzip, file |

This is a target catalogue, not a claim that these packages are already bundled, installed, or working. Some tools are unavailable on Android/Alpine, require additional dependencies, or have restrictive platform requirements. Each entry must be tested before being marked supported.

## Installation model

1. Install the Sentinel APK.
2. On first launch, detect Android ABI, free storage, and network availability.
3. Show the rootfs size, tool bundle size, and licence notices before download.
4. Download signed/checksummed, pinned environment artifacts from trusted upstream sources.
5. Verify hashes/signatures before extraction; reject path traversal and unsafe archive entries.
6. Install into Sentinel's private files directory; never request root.
7. Run a self-test (shell, package manager, DNS, HTTPS, workspace read/write).
8. Show a truthful inventory: bundled, installed, update available, unsupported, or failed.

Offer a minimal offline starter image and optional tool packs. "All tools pre-installed" should mean all tools in the declared supported catalogue, not every Linux package in existence. Publish the exact bundle manifest and sizes for each release.

## Security requirements

- Never expose a shell HTTP server on LAN or the public internet.
- Run Linux processes as the Android app UID, without root or Android permissions escalation.
- Use per-session process limits, timeouts, output bounds, and clean shutdown.
- Keep environment files and secrets in app-private storage; redact secrets from logs.
- Pin rootfs and tool versions; verify signatures/hashes and maintain an SBOM plus licence notices.
- Require explicit authorization and scope controls for network/security assessment tools.
- Do not include exploit automation, credential attacks, persistence, stealth, or destructive actions as default workflows.

## Platform limitations

PRoot emulates filesystem and identity behavior in userspace; it does not provide a separate Linux kernel. Kernel-dependent tools, raw sockets, packet capture, system services, Docker, and some network utilities may not work on ordinary Android devices. Android vendor restrictions and battery/background limits also apply. These limitations must be visible in the app.

## Current implementation status

The repository now includes a native Compose scanner, an Alpine rootfs bootstrapper, a CI-built Android PRoot runtime, a persistent pipe-backed shell, and an Alpine package installer UI. It is an independent Android app and does not require Termux. It is **not yet a full terminal replacement**: the current shell uses process pipes rather than a PTY, so terminal resize, reliable Ctrl-C, job control, and full-screen TUI applications (such as Vim or htop) are not supported. Physical-device validation is still required.


## Implementation status — 22 November 2026

The current branch now contains:
- A native Compose workspace with Scanner, Terminal, and Tools tabs.
- A private Alpine 3.24.4 minirootfs installer for arm64-v8a.
- SHA-256 verification against Alpine's published checksum file before extraction.
- Staging-directory installation and archive path/symlink checks.
- A package catalogue for the initial shell, development, network, editor, and security package groups.
- A bounded command-runner integration point that invokes a PRoot executable from Sentinel's private files directory when one is provisioned.

Still not complete:
- No Android-compatible PRoot executable is currently shipped or installed by the app. Consequently, Linux commands and APK package installation remain disabled.
- No interactive PTY, terminal resize, persistent shell session, command history, or background jobs yet.
- Catalogue entries are not evidence that every package exists for every Alpine architecture or that every tool works on Android.
- No offline all-tools bundle, signed Sentinel tool manifest, SBOM, release APK, or physical-device test has been completed.
- The Python localhost shell remains a separate prototype and is not the Android runtime.

Do not mark this project production-ready or describe it as a complete Termux replacement until those blockers are implemented and verified on physical Android devices. The rootfs installer is a foundation, not a completed Linux runtime.


## Updated implementation status — 22 November 2026

Implemented in the current source:
- CI checks out the Android PRoot build project at a pinned commit, builds the ARM64 runtime with the Android NDK, and bundles its executable and loader files into the APK.
- The build includes upstream PRoot and talloc licence files alongside the builder MIT notice; the app does not copy Termux UI or Termux application code.
- First-run setup installs the bundled PRoot runtime and downloads Alpine 3.24.4 minirootfs for arm64-v8a, verifies Alpine's published SHA-256, extracts into private app storage, then runs a shell/APK self-test.
- Terminal tab starts a persistent interactive shell process and accepts multiple commands through an app-owned console.
- Tools tab can install the listed packages through Alpine apk; package signatures are verified by Alpine's package manager and configured repositories.
- The workspace directory is mounted inside the guest at /sentinel-host.

Remaining limitations:
- The terminal is pipe-backed, not a PTY terminal emulator. No terminal resize, reliable Ctrl-C, shell job control, or full-screen TUI support.
- PRoot is a userspace compatibility layer, not a Linux kernel or VM. Raw sockets, packet capture, Docker, system services, and some network/security utilities may fail on stock Android.
- The CI-built PRoot package and Alpine bootstrap have not yet been exercised on the user's physical Samsung device. Do not mark the runtime as device-verified until the app self-test and package installs pass there.
- Tool packages are installed on demand, not all preinstalled. Package availability depends on the selected Alpine release and architecture.
- No production signing key or Play Store release has been configured.
