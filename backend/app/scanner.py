"""Passive, bounded website posture checks. No exploit or port scanning."""
from __future__ import annotations

import asyncio
import ipaddress
import socket
import ssl
from datetime import datetime, timezone
from urllib.parse import urlparse, urljoin

import httpx

SECURITY_HEADERS = {
    "strict-transport-security": ("high", "Enable HSTS on HTTPS sites to enforce secure transport."),
    "content-security-policy": ("high", "Add a restrictive Content-Security-Policy tailored to the application."),
    "x-content-type-options": ("medium", "Set X-Content-Type-Options: nosniff."),
    "referrer-policy": ("low", "Set a privacy-conscious Referrer-Policy."),
    "permissions-policy": ("low", "Explicitly restrict browser features with Permissions-Policy."),
    "x-frame-options": ("medium", "Use DENY/SAMEORIGIN or CSP frame-ancestors to control framing."),
}

def validate_target(raw: str) -> str:
    parsed = urlparse(raw if "://" in raw else "https://" + raw)
    if parsed.scheme not in ("http", "https") or not parsed.hostname:
        raise ValueError("Enter a valid HTTP or HTTPS website.")
    if parsed.username or parsed.password:
        raise ValueError("URLs containing credentials are not allowed.")
    host = parsed.hostname.rstrip(".").lower()
    if host in ("localhost",) or host.endswith(".localhost"):
        raise ValueError("Localhost targets are not allowed.")
    try:
        ip = ipaddress.ip_address(host)
        if not ip.is_global:
            raise ValueError("Private, loopback, and reserved IP targets are not allowed.")
    except ValueError as exc:
        if "not allowed" in str(exc):
            raise
        # A non-IP hostname is resolved and checked below.
    try:
        infos = socket.getaddrinfo(host, None, type=socket.SOCK_STREAM)
    except socket.gaierror as exc:
        raise ValueError("Target hostname could not be resolved.") from exc
    addresses = {ipaddress.ip_address(info[4][0]) for info in infos}
    if not addresses or any(not addr.is_global for addr in addresses):
        raise ValueError("Target resolves to a private, loopback, or reserved address.")
    return parsed.geturl()

async def scan_website(raw_url: str) -> dict:
    url = validate_target(raw_url)
    started = datetime.now(timezone.utc).isoformat()
    timeout = httpx.Timeout(8.0, connect=4.0)
    limits = httpx.Limits(max_connections=5, max_keepalive_connections=2)
    async with httpx.AsyncClient(
        timeout=timeout, limits=limits, follow_redirects=False,
        headers={"User-Agent": "SentinelAI-PassiveScanner/0.1 (+authorized security assessment)"}
    ) as client:
        response = await client.get(url)
        # Follow at most three public HTTP(S) redirects, validating each hop.
        hops = 0
        while response.is_redirect and hops < 3:
            location = response.headers.get("location")
            if not location:
                break
            url = validate_target(urljoin(str(response.url), location))
            response = await client.get(url)
            hops += 1
        headers = {k.lower(): v for k, v in response.headers.items()}
        findings = []
        for name, (severity, advice) in SECURITY_HEADERS.items():
            if name not in headers:
                findings.append({"id": name, "title": f"Missing {name}", "severity": severity,
                                 "status": "missing", "evidence": "Header not present in response.",
                                 "recommendation": advice})
        if urlparse(url).scheme != "https":
            findings.append({"id": "https", "title": "Website does not use HTTPS", "severity": "high",
                             "status": "missing", "evidence": "Final response URL uses HTTP.",
                             "recommendation": "Configure HTTPS and redirect HTTP traffic to HTTPS."})
        else:
            findings.append({"id": "https", "title": "HTTPS is enabled", "severity": "info",
                             "status": "passed", "evidence": "Final response URL uses HTTPS.",
                             "recommendation": "Keep TLS certificates renewed and protocols current."})
        # Fetch a tiny, same-origin robots.txt only; do not crawl its paths.
        parsed = urlparse(url)
        robots_url = f"{parsed.scheme}://{parsed.netloc}/robots.txt"
        robots_status = None
        try:
            robots = await client.get(robots_url)
            robots_status = robots.status_code
        except httpx.HTTPError:
            pass
        # TLS certificate metadata: no custom trust bypass.
        tls = {"checked": parsed.scheme == "https"}
        if parsed.scheme == "https":
            try:
                ctx = ssl.create_default_context()
                with socket.create_connection((parsed.hostname, parsed.port or 443), timeout=4) as sock:
                    with ctx.wrap_socket(sock, server_hostname=parsed.hostname) as ssock:
                        cert = ssock.getpeercert()
                        tls["subject"] = dict(x[0] for x in cert.get("subject", []))
                        tls["issuer"] = dict(x[0] for x in cert.get("issuer", []))
                        tls["expires"] = cert.get("notAfter")
            except (OSError, ssl.SSLError):
                tls["metadata_error"] = "Certificate metadata could not be retrieved."
        score_weights = {"high": 15, "medium": 8, "low": 4}
        deductions = sum(score_weights.get(f["severity"], 0) for f in findings if f["status"] == "missing")
        return {
            "target": url, "scanned_at": started, "http_status": response.status_code,
            "server": headers.get("server", "Not disclosed"),
            "content_type": headers.get("content-type", "Unknown"),
            "robots_txt_status": robots_status, "tls": tls,
            "score": max(0, 100 - deductions), "findings": findings,
            "checks": ["HTTP response headers", "HTTPS redirect status", "TLS certificate metadata", "robots.txt availability"],
            "notice": "Passive checks only. A missing header is a configuration observation, not proof of an exploitable vulnerability."
        }
