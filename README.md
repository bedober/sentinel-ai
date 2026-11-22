# Sentinel AI

Sentinel AI is an authorized-first security posture scanner for websites and systems. The initial release performs **non-invasive checks**: HTTP security headers, TLS certificate metadata, DNS resolution, robots/sitemap discovery, and basic page metadata. It does not exploit vulnerabilities, brute-force credentials, crawl private paths, or run destructive tests.

> Only scan domains and systems you own or have explicit written permission to assess.

## Project layout

- `backend/` — FastAPI API and passive scanner
- `frontend/` — React + Vite dashboard
- `android/` — Kotlin Android starter
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
