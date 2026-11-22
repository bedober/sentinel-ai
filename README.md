# Sentinel AI

Sentinel AI is an authorized-first security posture scanner and GUI-first security workstation. The initial release performs passive website checks and provides a reviewed tool catalogue. Tool availability is reported honestly; a catalogue entry does not mean an adapter is ready.

> Only scan systems you own or have explicit written permission to assess.

## Project layout

- `backend/` — FastAPI API and passive scanner
- `frontend/` — React + Vite dashboard
- `android/` — Android prototype plus the full Termux-source fork build pipeline; the fork packages its own terminal, bootstrap, shell and package manager into one APK
- `worker/` — isolated Linux worker service (first adapter: bounded Nmap TCP connect scan)
- `shell/` — optional development-only server shell (not required by the Android APK)
- `docs/` — security, deployment and architecture notes

## Run Sentinel as a standalone Android app

Install the fork APK from the `sentinel-ai-full-termux-fork-debug-apk` artifact in GitHub Actions. It is built from pinned Termux application source, rebranded as Sentinel AI, and includes its own terminal, bootstrap and package manager. No separately installed Termux app or Android root access is required. The initial fork retains Android package id `com.termux` because upstream bootstrap binaries are compiled for that app-data prefix; it cannot coexist with the official Termux app. The Sentinel security-dashboard migration into the fork is still in progress. See [full Termux fork integration](docs/ANDROID_FULL_TERMUX_FORK.md).

## Run the API and dashboard locally

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

In another terminal:

```bash
cd frontend
npm install
npm run dev
```

Set `VITE_API_BASE=http://127.0.0.1:8000` if the API is not on the same origin.

## Deploy the Linux server worker

Sentinel can run the heavy tools on a separate Linux server instead of relying on Android's restricted networking environment. The worker is an isolated Docker service; its port is not published to the internet.

Requirements: Ubuntu Server 24.04 LTS (or compatible Linux), Docker Engine and Docker Compose.

```bash
cp .env.server.example .env
```

Edit `.env`:
- Set `SENTINEL_WORKER_TOKEN` and `SENTINEL_CLIENT_TOKEN` to two different long random secrets. Enter the client token in the Android app; it is kept in memory for the current session.
- Set `SENTINEL_ALLOWED_CIDRS` to only the exact IP ranges you administer or have written authorization to test. The sample `192.0.2.10/32` is documentation-only and must be replaced.
- Never set the allowlist to `0.0.0.0/0`.

Then start:

```bash
docker compose up -d --build api worker
docker compose logs -f worker
```

The API calls the worker over the private Compose network at `http://worker:8090`. Check `GET /api/worker/health`; submit an authorized bounded TCP scan to `POST /api/worker/nmap` with JSON fields `target`, `ports`, and `authorized: true`. The worker resolves the target, checks every resulting IP against the administrator allowlist, and passes only checked IP literals to Nmap. It caps scans at 256 ports and 180 seconds. It accepts no arbitrary shell commands.

**Production:** put the API behind HTTPS and user authentication, rate limits, audit logs and abuse monitoring. Do not expose the worker port. Use host firewall rules and network egress restrictions. The existing terminal service is a separate single-user development feature and must not be exposed directly to the public internet.

See [worker deployment instructions](worker/README.md).

## Free and open-source licensing

The Sentinel application code is MIT licensed. Third-party tools retain their own licences; there is no single licence covering the 21 tools. Prefer permissive or recognized free/open-source projects where a practical alternative exists. Keep third-party notices and source/licence references for every packaged component.

Important exception: Nmap is free for end users under the Nmap Public Source License, which is a custom licence and is not plain GPL. Nmap's maintainers state that redistribution or embedding in commercial products requires an OEM licence. Review the exact terms before distributing a Sentinel product that bundles or embeds Nmap; do not assume that "free to use" means unrestricted redistribution. See the [Nmap licence](https://nmap.org/npsl/) and [OEM terms](https://nmap.org/oem/).

Nessus and Burp Suite are not treated as free/open-source components. Sentinel must not redistribute them. Prefer Greenbone Community Edition and OWASP ZAP for relevant open-source vulnerability-management and web-testing workflows, subject to their own component licences and deployment requirements. Some tools require compatible hardware, dedicated services or customer-supplied installations.

## Hugging Face AI

Set `HF_TOKEN` and optionally `HF_MODEL` in the backend environment to enable AI-generated explanations of already-collected findings. AI explanations are advisory only and cannot initiate scans.

## Android APK testing

The GitHub Actions workflow builds the full Termux-source fork and uploads it as `sentinel-ai-full-termux-fork-debug-apk`. The existing Compose/PRoot implementation is retained as a migration prototype and is not the primary Android artifact. Debug artifacts are for testing, not production release. Physical-device validation and the in-app Sentinel console integration remain outstanding.

## License

Sentinel-specific code: MIT — see [LICENSE](LICENSE). The forked Termux Android application source is GPL-3.0-only, with upstream component exceptions. The combined Android fork must be distributed with the applicable GPL source and notices; see [Android third-party notices](android/THIRD_PARTY_NOTICES.md) and [fork integration notes](docs/ANDROID_FULL_TERMUX_FORK.md).
