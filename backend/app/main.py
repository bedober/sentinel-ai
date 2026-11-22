"""Sentinel AI API: scoped passive web checks and authenticated Linux worker gateway."""
from __future__ import annotations

import ipaddress
import logging
import os
import re
import secrets
from urllib.parse import urlsplit

import httpx
from fastapi import FastAPI, Header, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field, field_validator

from .ai import explain_findings
from .scanner import scan_website
from .scan_jobs import create_web_scan, get_job
from .terminal import execute_terminal_command
from .tool_registry import tool_inventory

logger = logging.getLogger("sentinel.api")

app = FastAPI(
    title="Sentinel AI API",
    version="0.4.0",
    description="Authorized security posture checks and authenticated Linux worker gateway.",
)

_default_origins = "http://localhost:5173"
origins = [
    item.strip().rstrip("/")
    for item in os.getenv("CORS_ORIGINS", _default_origins).split(",")
    if item.strip()
]
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=False,
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type", "Authorization"],
)


class ScanRequest(BaseModel):
    target: str = Field(min_length=4, max_length=2048)
    authorized: bool = Field(
        description="True only when you own or have written permission to assess this target."
    )


class NmapRequest(BaseModel):
    target: str = Field(min_length=1, max_length=253)
    ports: str = Field(default="1-1024", min_length=1, max_length=512)
    authorized: bool = False

    @field_validator("target")
    @classmethod
    def validate_target(cls, value: str) -> str:
        value = value.strip()
        if not value or any(char in value for char in "/\\@?#;$' "):
            raise ValueError("Enter one hostname or IP address, not a URL or command.")
        try:
            ipaddress.ip_address(value)
            return value
        except ValueError:
            pass
        if len(value) > 253 or not re.fullmatch(
            r"(?=.{1,253}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(?:\.(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?))*",
            value,
        ):
            raise ValueError("Enter a valid hostname or IP address.")
        return value

    @field_validator("ports")
    @classmethod
    def validate_ports(cls, value: str) -> str:
        value = value.strip()
        if not re.fullmatch(r"\d+(?:-\d+)?(?:,\d+(?:-\d+)?)*", value):
            raise ValueError("Ports must be numbers or comma-separated ranges, e.g. 22,80,443,8000-8100.")
        total = 0
        for part in value.split(","):
            pieces = [int(piece) for piece in part.split("-")]
            start, end = pieces[0], pieces[-1]
            if not (1 <= start <= 65535 and 1 <= end <= 65535 and start <= end):
                raise ValueError("Port values must be between 1 and 65535, with valid ascending ranges.")
            total += end - start + 1
            if total > 4096:
                raise ValueError("A single request is limited to 4096 ports.")
        return value


class TerminalRequest(BaseModel):
    command: str = Field(default="", max_length=500)
    authorized: bool = False


class ExplainRequest(BaseModel):
    findings: list[dict] = Field(min_length=1, max_length=30)


def _worker_config() -> tuple[str, str]:
    url = os.getenv("SENTINEL_WORKER_URL", "").strip().rstrip("/")
    token = os.getenv("SENTINEL_WORKER_TOKEN", "").strip()
    if not url or not token:
        raise HTTPException(503, "Linux worker is not configured on this server.")
    parsed = urlsplit(url)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc or parsed.username or parsed.password:
        logger.error("SENTINEL_WORKER_URL is invalid.")
        raise HTTPException(503, "Linux worker URL is invalid.")
    return url, token


def _require_client_token(authorization: str | None) -> None:
    expected = os.getenv("SENTINEL_CLIENT_TOKEN", "").strip()
    if not expected:
        raise HTTPException(503, "Linux worker API access is not configured.")
    supplied = authorization[7:].strip() if authorization and authorization.startswith("Bearer ") else ""
    if not supplied or not secrets.compare_digest(supplied, expected):
        raise HTTPException(401, "Invalid Linux server access token.")


def _worker_error_detail(response: httpx.Response) -> str:
    try:
        payload = response.json()
        detail = payload.get("detail") if isinstance(payload, dict) else None
        if isinstance(detail, str) and detail.strip():
            return detail[:300]
    except ValueError:
        pass
    return "Linux worker rejected the request."


@app.get("/health")
async def health():
    return {"status": "ok", "service": "sentinel-ai", "version": app.version}


@app.get("/api/worker/health")
async def worker_health(authorization: str | None = Header(default=None)):
    _require_client_token(authorization)
    url, token = _worker_config()
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(8.0, connect=3.0)) as client:
            response = await client.get(
                url + "/health",
                headers={"Authorization": "Bearer " + token},
            )
            response.raise_for_status()
            data = response.json()
            if not isinstance(data, dict):
                raise ValueError("Worker health response must be a JSON object.")
            return data
    except (httpx.HTTPError, ValueError) as exc:
        logger.warning("Linux worker health check failed: %s", type(exc).__name__)
        raise HTTPException(503, "Linux worker is unavailable.") from exc


@app.post("/api/worker/nmap")
async def worker_nmap(
    body: NmapRequest,
    authorization: str | None = Header(default=None),
):
    _require_client_token(authorization)
    if not body.authorized:
        raise HTTPException(400, "Confirm written authorization for this target.")
    url, token = _worker_config()
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(190.0, connect=5.0)) as client:
            response = await client.post(
                url + "/api/nmap",
                json=body.model_dump(),
                headers={"Authorization": "Bearer " + token},
            )
        if response.status_code >= 400:
            raise HTTPException(response.status_code, _worker_error_detail(response))
        try:
            result = response.json()
        except ValueError as exc:
            logger.error("Linux worker returned a non-JSON Nmap response.")
            raise HTTPException(502, "Linux worker returned an invalid response.") from exc
        if not isinstance(result, dict):
            raise HTTPException(502, "Linux worker returned an invalid response.")
        return result
    except httpx.TimeoutException as exc:
        raise HTTPException(504, "Linux worker request timed out.") from exc
    except httpx.HTTPError as exc:
        logger.warning("Linux worker Nmap request failed: %s", type(exc).__name__)
        raise HTTPException(502, "Linux worker connection failed.") from exc


@app.post("/api/terminal/execute")
async def terminal_execute(body: TerminalRequest):
    try:
        return await execute_terminal_command(body.command, body.authorized)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    except Exception as exc:
        logger.exception("Restricted terminal command failed.")
        raise HTTPException(502, "Terminal command failed safely.") from exc


@app.get("/api/scan/jobs/{job_id}")
async def scan_job_status(job_id: str):
    job = await get_job(job_id)
    if not job:
        raise HTTPException(404, "Scan job not found or expired after server restart.")
    return job


@app.post("/api/scan/jobs")
async def start_background_scan(body: ScanRequest):
    if not body.authorized:
        raise HTTPException(400, "Confirm that you own or have written permission to assess this target.")
    try:
        return await create_web_scan(body.target)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(429, str(exc)) from exc


@app.get("/api/tools")
async def tools_status():
    return tool_inventory()


@app.post("/api/scan")
async def create_scan(body: ScanRequest):
    if not body.authorized:
        raise HTTPException(400, "Confirm that you own or have written permission to assess this target.")
    try:
        return await scan_website(body.target)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    except Exception as exc:
        logger.exception("Passive website scan failed.")
        raise HTTPException(502, "The target could not be scanned safely.") from exc


@app.get("/")
async def root():
    return {
        "name": "Sentinel AI",
        "version": app.version,
        "docs": "/docs",
        "health": "/health",
        "tools": "/api/tools",
        "linux_worker": "/api/worker/health",
    }


@app.post("/api/explain")
async def explain(body: ExplainRequest):
    try:
        explanation = await explain_findings(body.findings)
        return {"explanation": explanation, "provider": "Hugging Face Inference API"}
    except Exception as exc:
        logger.warning("AI explanation service failed: %s", type(exc).__name__)
        raise HTTPException(502, "AI explanation service is unavailable.") from exc
