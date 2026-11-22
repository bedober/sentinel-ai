#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
if ! command -v pkg >/dev/null 2>&1; then
  echo "Run this inside Termux. Install Termux from F-Droid or official GitHub releases."
  exit 1
fi
pkg update -y
pkg install -y python bash coreutils git curl wget openssh nmap dnsutils jq nano vim procps
mkdir -p "$HOME/sentinel-workspace"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
exec python "$SCRIPT_DIR/sentinel-shell.py"
