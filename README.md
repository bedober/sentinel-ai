# Sentinel AI

Sentinel AI is an authorized-first security posture scanner for websites and systems. The initial release performs **non-invasive checks**: HTTP security headers, TLS certificate metadata, DNS resolution, robots/sitemap discovery, and basic page metadata. It does not exploit vulnerabilities, brute-force credentials, crawl private paths, or run destructive tests.

> Only scan domains and systems you own or have explicit written permission to assess.

## Project layout

- `backend/` — FastAPI API and passive scanner
- `frontend/` — React + Vite dashboard
- `android/` — Kotlin Android app with an on-device passive HTTPS assessor and Alpine rootfs bootstrap
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


## Standalone Android Linux environment

Sentinel is being developed as a standalone Android application with its own Linux userland, native terminal, package manager, workspace, and reviewed tool catalogue. Termux is not intended to be a dependency. The runtime is not complete yet; see [the Android standalone architecture](docs/ANDROID_STANDALONE_ARCHITECTURE.md) for the design, tool catalogue, security requirements, and honest implementation status. Do not advertise the current APK as a Termux replacement until the runtime and physical-device tests are complete.


## Android APK testing

The GitHub Actions workflow **Sentinel AI CI and Android APK** builds a debug APK and uploads it as the `sentinel-ai-debug-apk` artifact. Open the repository's Actions tab, select a successful run, and download that artifact. Extract the ZIP and install `app-debug.apk` on an Android device with installation from that source allowed. Debug artifacts are for testing and are not signed production releases.

The current Android app runs passive HTTPS checks locally, without a hosted API. Its Alpine rootfs installer is present, but the standalone shell is not yet operational: PRoot provisioning, interactive PTY sessions, package installation, and physical-device validation remain unfinished.


## Android Linux runtime update

The Android CI workflow now builds an ARM64 PRoot runtime from pinned upstream sources and bundles the executable, loader and corresponding licence notices into the APK. On first launch, the Terminal tab installs that runtime, downloads and verifies the Alpine minirootfs, and runs a shell/APK self-test. The Tools tab installs packages on demand through Alpine's signed package repositories. A persistent pipe-backed shell is available; it is not yet a full PTY terminal emulator and cannot reliably run full-screen terminal apps or provide terminal resize/Ctrl-C/job control. PRoot limitations on Android remain (for example, raw sockets, packet capture, Docker and some security tools may not work). Physical-device testing is still required before calling this a complete Termux alternative.


## Background scan jobs (beta)

The API now exposes a background job interface for the currently supported **passive web posture** profile. The app can submit a target once, receive a job ID, and poll for progress/results without keeping a command prompt open.

Start a job (only for a target you own or have written permission to assess):

```bash
curl -X POST http://127.0.0.1:8000/api/scan/jobs \
  -H 'Content-Type: application/json' \
  -d '{"target":"https://example.com","authorized":true}'
```

Poll the returned ID:

```bash
curl http://127.0.0.1:8000/api/scan/jobs/JOB_ID
```

This beta job manager is process-local: jobs are lost when the API process restarts and it is not suitable for multi-worker production deployments. Add a durable queue and database before production.

### Requested tool integration status

The API inventory now lists all 21 requested tools and reports whether a binary is present separately from whether an adapter is implemented. **The inventory is not a claim that all tools are installed or functional.** Only the passive web posture profile currently runs automatically. Several requested products require external licensed installations or dedicated server infrastructure (for example Nessus, Burp Suite editions, and Greenbone/OpenVAS). Tools whose primary workflows involve exploitation, credential guessing/cracking, browser hooking, or high-rate fuzzing remain disabled for unattended execution.

The next safe integration stages are bounded TCP service discovery on explicitly approved ports, passive DNS/asset discovery, and a ZAP passive baseline. Any active checks need per-target authorization, rate limits, cancellation, audit logs, and clear impact warnings. No automatic exploit, password attack, lateral movement, or wireless cracking workflow is provided.
