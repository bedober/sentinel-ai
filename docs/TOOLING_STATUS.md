# Security engine integration status

Sentinel AI currently runs its own passive HTTP/TLS posture checks. This file distinguishes
tool names in the roadmap from tools that are genuinely executable today.

## Planned engines

| Engine | Intended role | Mode | Current integration |
|---|---|---|---|
| Nmap | TCP service discovery | Active | Not installed/connected |
| OWASP ZAP | Web application baseline | Active | Not installed/connected |
| Nuclei | Known exposure templates | Active | Not installed/connected |
| WhatWeb | Technology fingerprinting | Passive | Not installed/connected |
| Subfinder | Passive subdomain discovery | Passive | Not installed/connected |
| Amass | DNS/attack-surface mapping | Passive | Not installed/connected |
| Nikto | Web server checks | Active | Not installed/connected |
| testssl.sh | TLS configuration audit | Active | Not installed/connected |
| Trivy | Dependency/container/filesystem audit | Local | Not installed/connected |
| Semgrep | Source-code static analysis | Local | Not installed/connected |

The `GET /api/tools` endpoint reports binary presence on the API host. A present binary
is reported as `installed_not_integrated`; it is not falsely described as a working scanner.
The current public API does not invoke any of these tools.

## Required before enabling execution

1. Run tools in a separate, disposable worker/container, not in Vercel Functions.
2. Verify control of each target using DNS TXT or HTTP challenge; a checkbox alone is not verification.
3. Enforce scope (exact domains/IPs/ports), deny private/reserved ranges after DNS resolution,
   pin resolved addresses, and re-check every redirect and DNS answer.
4. Use rate limits, concurrency caps, hard timeouts, output-size limits, job cancellation,
   per-customer quotas and immutable audit logs.
5. Keep active scans opt-in, low-impact by default, and block exploit, brute-force,
   credential stuffing, persistence, evasion and destructive templates.
6. Run integration tests against intentionally vulnerable local fixtures only, never public targets.

## Hosting boundary

Vercel is appropriate for the React dashboard and lightweight API routes. Long-running
network scanners and Docker-based tools need a separate container worker host. Configure
the dashboard's `VITE_API_BASE` to that API's HTTPS URL after the worker is deployed.
