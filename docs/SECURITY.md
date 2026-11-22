# Security model

Sentinel is designed for defensive assessment of owned or explicitly authorized assets.

## Current guardrails
- HTTP and HTTPS targets only.
- Reject localhost and non-public IP addresses, including DNS names resolving to private/reserved addresses.
- No redirect following without revalidating each destination.
- Bounded connection timeout and limited redirect count.
- Passive response-header, TLS metadata, and robots.txt checks only.
- No crawling of discovered paths, port scanning, exploitation, password testing, or destructive requests.

## Before production
Add authenticated accounts, per-user rate limits, durable audit logs, scan quotas, background job isolation, SSRF regression tests, abuse reporting, and an explicit target-ownership verification workflow. Run the scanner in an isolated network environment with outbound egress restrictions. Do not expose it as an unrestricted public scanning proxy.
