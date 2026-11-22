# Sentinel AI — local Android Linux shell

This mode runs directly on Android using **Termux**. It does not need a VPS, Docker, root access, or a cloud terminal.

## Install

1. Install Termux from F-Droid or the official Termux GitHub releases. Avoid mixing Termux with add-ons installed from a different source.
2. Open Termux and clone the project:

```bash
pkg update -y
pkg install -y git
git clone https://github.com/bedober/sentinel-ai.git
cd sentinel-ai
chmod +x scripts/termux-shell.sh
./scripts/termux-shell.sh
```

3. If the optional `ttyd` package is available, the script starts a browser terminal at `http://127.0.0.1:7681`. Otherwise it opens a normal interactive Bash session in Termux.
4. The working directory is `~/sentinel-workspace`.

## Included tools

The setup installs the Termux packages for Bash, Python, Git, curl, wget, OpenSSH, Nmap, DNS utilities, jq, nano, vim and process utilities. Termux's package manager is `pkg` (a wrapper around apt); install additional packages with `pkg install PACKAGE`.

Some desktop Linux packages are unavailable or behave differently on Android/ARM. Nmap and other network utilities may have Android-specific limitations. This is a Termux userland, not a full virtual machine or a rooted Linux kernel.

## Security

- The terminal binds to `127.0.0.1` only. Do not expose it on `0.0.0.0`, forward the port, or publish it to the internet.
- Anyone with access to the unlocked phone can use the local shell. Protect the device with a screen lock.
- Do not run untrusted scripts, paste unknown commands, or store API keys in shell history.
- Use Sentinel AI scans only against systems you own or have explicit permission to assess.
- The shell is a local developer environment, not a sandbox against other apps running under the same Android user.

## Dashboard integration

The web dashboard cannot automatically reach a user's phone loopback address from a remote browser. On the same phone, open the dashboard in the Android app's local WebView only after the app has an explicit local-shell connection flow. A remote Vercel page's `127.0.0.1` points to the visitor's own device/browser context and is not a cloud connection to your phone.

## Stop / restart

Stop with Ctrl+C. Restart by running:

```bash
cd ~/sentinel-ai
./scripts/termux-shell.sh
```

The shell's files persist in Termux home storage. Back up important work separately.
