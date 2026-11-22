# Sentinel AI

Sentinel AI is an authorized-first security posture scanner for websites and systems. The initial release performs **non-invasive checks**: HTTP security headers, TLS certificate metadata, DNS resolution, robots/sitemap discovery, and basic page metadata. It does not exploit vulnerabilities, brute-force credentials, crawl private paths, or run destructive tests.

> Only scan domains and systems you own or have explicit written permission to assess.

## Project layout

- `backend/` — FastAPI API and passive scanner
- `frontend/` — React + Vite dashboard
- `android/` — Kotlin Android starter
- `shell/` — isolated Ubuntu Bash terminal service
- `docs/` — security and deployment notes

## Quick start (API)

```bash
cd backend
python -m venv .venv
# Windows: .venv\\Scripts\\activate
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

Open http://127.0.0.1:8000/docs.

## Quick start (dashboard)

```bash
cd frontend
npm install
npm run dev
```

Set `VITE_API_BASE=http://127.0.0.1:8000` if the API is not on the same origin.

## Hugging Face AI

Set `HF_TOKEN` and optionally `HF_MODEL` in the backend environment to enable AI-generated explanations of already-collected findings. The model is advisory only: it cannot initiate scans or execute commands. Model availability, license, and safety behavior depend on the selected Hugging Face model/provider.

## Scope and safeguards

- HTTP(S) URLs only; reject localhost, private, link-local, and reserved IP ranges.
- Require the operator to attest that they own or are authorized to assess the target.
- Passive checks only; strict request timeouts, response-size limits, and bounded redirects.
- No exploit payloads, credential attacks, persistence, stealth, or destructive testing.
- This starter is not a penetration-testing certification or a substitute for a professional assessment.

## License

MIT — see [LICENSE](LICENSE).


## Full Linux shell (Docker)

Sentinel includes a separate Ubuntu 24.04 terminal container with Bash, Python, Git,
curl, wget, jq, DNS utilities, Nmap, whois and common editors.

Start the single-user local development shell:

```bash
docker compose up --build shell
```

The shell binds to `127.0.0.1:7681` only. For the dashboard, configure
`VITE_SHELL_URL` to the terminal URL served through your own authenticated HTTPS
reverse proxy, then rebuild the frontend.

**Production warning:** the compose shell is a single-user development instance.
Do not expose ttyd directly to the public internet. Put it behind authentication
and HTTPS, deploy it on a dedicated Docker host, and provision a separate
container and persistent workspace per authenticated user. Keep it non-root,
without privileged mode or a Docker socket mount. Apply network egress controls,
backups, quotas and session expiry. Package installation is handled by rebuilding
the reviewed image rather than granting root or unrestricted sudo in sessions.


## Local Android Linux shell (Termux)

For a shell that runs on your Android phone without a VPS or Docker, follow [docs/ANDROID_TERMUX_SHELL.md](docs/ANDROID_TERMUX_SHELL.md). The bootstrap script installs Bash, Python, Git, curl, wget, Nmap and common utilities in Termux. If `ttyd` is available, it listens on `127.0.0.1:7681`; otherwise it opens an interactive Termux Bash session.

This is a Termux Linux userland, not a full virtual machine. The native Android app does not yet embed or manage Termux sessions. Do not expose the local terminal to the public internet.
