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

The repository currently has a native Compose scanner screen and an original Python localhost command-console prototype that runs through Termux. It is **not yet** a standalone Linux environment, does not include PRoot or an Alpine rootfs, and does not ship the catalogue above. Do not advertise the Android build as a Termux replacement until the PRoot runtime, rootfs installer, native PTY terminal, package manager UI, licence/SBOM bundle, and physical-device tests are complete.
