# Sentinel AI Linux Worker

A deployable Linux-side worker for the Android GUI and Sentinel API. The first
adapter is Nmap TCP connect/version discovery. It accepts no shell commands and
uses a fixed argv template.

## Requirements

- Ubuntu Server 24.04 LTS or another Linux host with Docker Engine + Compose.
- A long random worker token.
- An administrator-maintained target CIDR allowlist.
- HTTPS and authentication in front of the public API. The worker itself is only
  exposed on the private Compose network.

## Configure

Copy `.env.server.example` to `.env`, set a unique `SENTINEL_WORKER_TOKEN`,
and set `SENTINEL_ALLOWED_CIDRS` to the exact networks this server is authorized
to assess. Example lab-only value: `192.0.2.10/32`. Do not use `0.0.0.0/0`.
The example documentation range is intentionally non-routable; replace it with
your own explicitly authorized target ranges.

Start:

```bash
docker compose up -d --build api worker
docker compose logs -f worker
```

The API container reaches the worker at `http://worker:8090`. The worker has no
published host port. Check worker health from the API host's private Docker
network or through the API's health relay.

## Safety model

- Worker requires a bearer token and an explicit authorization attestation.
- Targets must resolve entirely inside administrator-configured CIDRs.
- DNS is resolved before execution; Nmap receives the checked IP literals.
- TCP connect scan only; no raw socket, UDP, exploit, password, or shell features.
- Maximum 256 explicitly selected ports and a 180-second process limit.
- Container runs as non-root, drops Linux capabilities, and has no Docker socket.

This is an initial server adapter, not a claim that all 21 catalogue tools are
integrated. Add each future adapter as a fixed, reviewed argument builder with
its own resource limits, licence review, and tests.
