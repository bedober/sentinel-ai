"""Small background job manager for authorized, non-invasive web posture scans.

Jobs are process-local in this beta. Use a durable queue/database before multi-worker
or production deployment. No arbitrary shell commands are accepted by this API.
"""
from __future__ import annotations

import asyncio
import uuid
from datetime import datetime, timezone
from typing import Any

from .scanner import scan_website

_JOBS: dict[str, dict[str, Any]] = {}
_LOCK = asyncio.Lock()


async def create_web_scan(target: str) -> dict[str, Any]:
    job_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc).isoformat()
    job = {
        "id": job_id,
        "target": target,
        "profile": "passive-web",
        "status": "queued",
        "progress": 0,
        "created_at": now,
        "updated_at": now,
        "result": None,
        "error": None,
    }
    async with _LOCK:
        _JOBS[job_id] = job
    asyncio.create_task(_run(job_id, target))
    return public_job(job)


async def _run(job_id: str, target: str) -> None:
    async with _LOCK:
        job = _JOBS.get(job_id)
        if not job:
            return
        job.update(status="running", progress=10,
                   updated_at=datetime.now(timezone.utc).isoformat())
    try:
        result = await scan_website(target)
        async with _LOCK:
            job = _JOBS.get(job_id)
            if job:
                job.update(status="completed", progress=100, result=result,
                            updated_at=datetime.now(timezone.utc).isoformat())
    except ValueError as exc:
        async with _LOCK:
            job = _JOBS.get(job_id)
            if job:
                job.update(status="failed", error=str(exc),
                           updated_at=datetime.now(timezone.utc).isoformat())
    except Exception:
        async with _LOCK:
            job = _JOBS.get(job_id)
            if job:
                job.update(status="failed",
                           error="The scan could not complete safely.",
                           updated_at=datetime.now(timezone.utc).isoformat())


def public_job(job: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in job.items()}


async def get_job(job_id: str) -> dict[str, Any] | None:
    async with _LOCK:
        job = _JOBS.get(job_id)
        return public_job(job) if job else None
