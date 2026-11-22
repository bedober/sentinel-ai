#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PORT="${SENTINEL_SHELL_PORT:-7681}"
BIND="127.0.0.1"

if ! command -v pkg >/dev/null 2>&1; then
  echo "Run this inside Termux (install it from F-Droid or the official GitHub releases)."
  exit 1
fi

echo "[Sentinel AI] Updating Termux packages..."
pkg update -y
pkg upgrade -y
pkg install -y bash coreutils git python curl wget openssh nmap dnsutils jq nano vim procps termux-api

if ! command -v ttyd >/dev/null 2>&1; then
  echo "[Sentinel AI] Installing ttyd if available in your Termux repositories..."
  if pkg install -y ttyd; then
    :
  else
    echo "ttyd is not available in the configured repositories."
    echo "Use the interactive Termux shell directly; do not expose an unauthenticated terminal server."
    exec bash -l
  fi
fi

mkdir -p "$HOME/sentinel-workspace"
cd "$HOME/sentinel-workspace"

echo
echo "Sentinel AI local Linux shell"
echo "Address: http://127.0.0.1:$PORT"
echo "Workspace: $HOME/sentinel-workspace"
echo "Stop with Ctrl+C."
echo "This listener is loopback-only; do not change the bind address to 0.0.0.0."
exec ttyd --writable --interface "$BIND" --port "$PORT" bash -l
