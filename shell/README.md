# Sentinel Linux Shell

This service provides an interactive Ubuntu Bash environment through ttyd.

## Security requirements
- Put the shell behind an HTTPS reverse proxy and an authenticated identity-aware gateway.
- Do not expose port 7681 directly to the public internet.
- Run on a dedicated Docker host, never mount the Docker socket into this container.
- Keep the container non-root, do not use privileged mode, and do not mount host paths other than the dedicated workspace volume.
- For multi-user production use, provision one container and one volume per authenticated user. The compose example below is a single-user development instance.
- Restrict outbound network access at the host firewall to destinations needed for updates and approved assessments.
- Back up workspaces and apply CPU, memory, PID and storage quotas.

The image includes Bash, Python, Git, curl, wget, jq, DNS utilities, Nmap, whois and common editor/network tools. Package installation is intentionally not enabled inside the non-root session. Add reviewed packages to the image and rebuild it instead of granting users root or unrestricted sudo.

## Start locally
Set up an HTTPS/authenticated reverse proxy before remote use. For local testing only:
`docker compose up --build shell`

Open http://127.0.0.1:7681. Do not publish this port publicly.
