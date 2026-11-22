#!/data/data/com.termux/files/usr/bin/bash
# Sentinel AI Termux bootstrap.
# Creates an isolated Debian userland with best-effort packages.
# Run only in an authorized lab or on systems you own/have written permission to test.
set -euo pipefail

if [ -z "\${PREFIX:-}" ] || [ ! -d "$PREFIX" ]; then
  echo "Run this script inside Termux, not in a regular Android shell."
  exit 1
fi

echo "[1/4] Updating Termux and installing the Linux userland manager..."
pkg update -y
pkg upgrade -y
pkg install -y proot-distro git curl wget nano

if ! proot-distro list | grep -qE '(^|[[:space:]])debian([[:space:]]|$)'; then
  echo "Debian is not listed by proot-distro. Check 'proot-distro list' and update Termux."
  exit 1
fi

if ! proot-distro login debian -- true >/dev/null 2>&1; then
  echo "[2/4] Installing Debian..."
  proot-distro install debian
else
  echo "[2/4] Debian already installed."
fi

echo "[3/4] Installing available Debian security packages..."
proot-distro login debian -- bash -lc '
set -u
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl wget git python3 python3-venv python3-pip pipx golang-go ruby-full default-jre-headless build-essential pkg-config libffi-dev libssl-dev

# Install only packages present in the configured Debian repositories.
# Missing packages are reported rather than silently represented as installed.
packages=(
  nmap masscan amass gobuster ffuf nikto zaproxy sqlmap
  impacket-scripts john hydra aircrack-ng hashcat
  dirb dnsutils whois net-tools iproute2
)
for package in "\${packages[@]}"; do
  if apt-cache show "$package" >/dev/null 2>&1; then
    echo "[install] $package"
    apt-get install -y "$package" || echo "[warning] Install failed: $package"
  else
    echo "[not in repo] $package"
  fi
done

mkdir -p "$HOME/sentinel-lab"/{reports,targets,wordlists}
cat > "$HOME/sentinel-lab/README.txt" <<EOF
Sentinel AI local lab
Use tools only against systems you own or have explicit written permission to assess.
This is a Debian userland inside Termux, not a full privileged Linux host.
Android restrictions may prevent raw packet, monitor-mode, GPU/OpenCL, or service features.
EOF
echo "Debian tool installation pass complete."
'

echo "[4/4] Creating Sentinel launcher..."
mkdir -p "$HOME/.local/bin"
cat > "$HOME/.local/bin/sentinel-linux" <<'EOF'
#!/data/data/com.termux/files/usr/bin/bash
exec proot-distro login debian -- "$@"
EOF
chmod +x "$HOME/.local/bin/sentinel-linux"

echo
echo "Setup pass finished."
echo "Enter Debian: proot-distro login debian"
echo "Run Debian shell via: sentinel-linux bash"
echo "Review tool availability inside Debian: command -v nmap masscan amass gobuster ffuf nikto zaproxy sqlmap"
echo "Note: commercial/server tools and hardware-dependent tools need separate supported hosts."
