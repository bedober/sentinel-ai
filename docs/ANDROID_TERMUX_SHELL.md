# Sentinel Shell — original Android shell interface

Sentinel Shell's command service, request handler, session-token flow and browser interface are original project code written for this repository. It does not embed a third-party terminal emulator, web-terminal UI or terminal server.

## Install

Install Termux from F-Droid or official Termux GitHub releases, then run:

```bash
pkg install -y git
git clone https://github.com/bedober/sentinel-ai.git
cd sentinel-ai
chmod +x scripts/termux-shell.sh
./scripts/termux-shell.sh
```

Open `http://127.0.0.1:8765` on the same phone and enter the one-time token printed in Termux.

## Behaviour and security

- Binds only to IPv4 loopback; do not forward or expose this port.
- A random per-start bearer token protects command requests.
- Commands run as the Termux user, not root, with a 20-second process timeout and bounded output.
- `cd` and `pwd` persist through Sentinel's session state. Other commands run in a fresh Bash process; shell variables, aliases, background jobs and interactive full-screen programs do not persist. This is an original command console, not a complete terminal emulator yet.
- It can modify files accessible to the Termux account. Do not paste untrusted commands.
- Termux, Bash, Python, Git, Nmap and other packages are third-party projects with their own licences. Sentinel AI does not claim ownership of them. Include applicable notices and a software bill of materials when redistributing packages.
- This is not legal advice or a guarantee against infringement. Review the repository licence and dependency licences before commercial distribution.
