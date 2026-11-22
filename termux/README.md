# Sentinel AI on Termux (Android)

This setup uses Termux + `proot-distro` + Debian. It is a practical Android userland, not a rooted Linux kernel or a substitute for a dedicated Linux server.

## Install

1. Install the current Termux app from F-Droid or the official Termux GitHub releases. Do not mix APKs/add-ons from different signing sources.
2. Open Termux and run:

   ```bash
   pkg update && pkg upgrade -y
   pkg install -y git
   git clone https://github.com/bedober/sentinel-ai.git
   cd sentinel-ai
   bash termux/install-sentinel.sh
   ```

3. Enter the Debian environment:

   ```bash
   proot-distro login debian
   ```

4. Check which commands really installed:

   ```bash
   for t in nmap masscan amass gobuster ffuf nikto zaproxy sqlmap \
     impacket-smbclient john hydra aircrack-ng hashcat; do
     command -v "$t" || echo "NOT INSTALLED: $t"
   done
   ```

The installer checks each Debian package and reports unavailable packages. Package availability depends on Debian release and CPU architecture; it does not fake successful installs.

## The 21 requested tools: Android/Termux reality

| Tool | Termux/Debian status |
|---|---|
| Nmap | CLI package; raw packet features may be restricted; TCP connect scans are more compatible |
| Masscan | Package may be available; raw packet scanning generally needs kernel capabilities unavailable in unrooted Termux |
| Amass | Package availability varies by Debian release/architecture |
| Gobuster | Package availability varies; Go build is a fallback |
| ffuf | Package availability varies; Go build is a fallback |
| Nessus | Vendor-supported Linux server/architecture and licence required; do not redistribute |
| OpenVAS / Greenbone | Run on a dedicated supported Linux server; heavyweight services and database required |
| Nikto | CLI package where available |
| Burp Suite | Separate vendor download/licence; GUI and Java requirements; not a Termux-native CLI package |
| OWASP ZAP | Package availability varies; GUI/headless operation needs Java and sufficient RAM |
| sqlmap | Package availability varies; use only in a lab or explicitly authorized test |
| Metasploit Framework | Large framework with platform-specific dependencies; use a supported Linux VM/server rather than assuming Android package compatibility |
| BloodHound | Requires a supported BloodHound deployment and database/collector workflow; not a single Termux binary |
| Impacket | Debian package may be available; verify installed command names |
| NetExec | Python/Linux dependency and architecture compatibility vary; install only in a dedicated lab environment |
| LinPEAS / WinPEAS | Audit scripts are run on the host being assessed; they are not Android system packages |
| Hashcat | Package may exist, but Android GPU/OpenCL support is usually unavailable; CPU-only operation can be very slow |
| John the Ripper | CLI package may be available; format and performance depend on build |
| Hydra | Package availability varies; use only for controlled authentication testing on accounts/systems you administer |
| Aircrack-ng | Package may be available; monitor mode/injection requires compatible Wi-Fi hardware, drivers and often root |
| BeEF | Separate framework/service with browser and server dependencies; not a one-command Termux package |

## Important limitations

- Unrooted Android does not provide the same kernel capabilities as a full Linux server. Raw sockets, packet injection, monitor mode, privileged networking, system services and GPU compute may not work.
- Nessus and Burp Suite are commercial products with their own licence terms. Sentinel must not bundle or redistribute them.
- Greenbone, BloodHound and BeEF are services/platforms, not simple standalone binaries.
- Do not install random third-party Termux repositories or run opaque one-line remote scripts as root. Review package source and licence first.
- The installer does not run scans, brute-force logins, exploit targets, or change network settings. It only prepares the local userland and installs repository packages that are available.

For reliable full-featured execution of all 21 integrations, use a dedicated Ubuntu/Debian x86_64 or ARM64 server where each vendor supports it, then connect the Android GUI to that worker using authentication and a strict target allowlist.
