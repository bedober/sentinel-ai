"""Bounded in-process job manager for authorized passive web posture scans.

This beta intentionally limits concurrency and retained history. Replace this
manager with a durable queue/database before multi-worker production use.
"""
from __future__ import annotations

import asyncio
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any

from .scanner import scan_website

MAX_ACTIVE_JOBS = 4
MAX_RETAINED_JOBS = 100
JOB_TTL = timedelta(hours=6)
_JOBS: dict[str, dict[str, Any]] = {}
_LOCK = asyncio.Lock()
_ACTIVE = 0


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _prune_locked() -> None:
    """Drop expired completed jobs, then oldest completed jobs if over capacity."""
    cutoff = datetime.now(timezone.utc) - JOB_TTL
    completed = []
    for job_id, job in _JOBS.items():
        if job["status"] in {"completed", "failed"}:
            try:
                created = datetime.fromisoformat(job["created_at"])
            except (TypeError, ValueError):
                created = cutoff - timedelta(seconds=1)
            if created < cutoff:
                completed.append((created, job_id))
    for _, job_id in completed:
        _JOBS.pop(job_id, None)

    if len(_JOBS) >= MAX_RETAINED_JOBS:
        completed = sorted(
            ((job["created_at"], job_id) for job_id, job in _JOBS.items()
             if job["status"] in {"completed", "failed"})
        )
        while len(_JOBS) >= MAX_RETAINED_JOBS and completed:
            _, job_id = completed.pop(0)
            _JOBS.pop(job_id, None)


async def create_web_scan(target: str) -> dict[str, Any]:
    global _ACTIVE
    job_id = str(uuid.uuid4())
    job = {
        "id": job_id,
        "target": target,
        "profile": "passive-web",
        "status": "queued",
        "progress": 0,
        "created_at": _now(),
        "updated_at": _now(),
        "result": None,
        "error": None,
    }
    async with _LOCK:
        _prune_locked()
        if _ACTIVE >= MAX_ACTIVE_JOBS:
            raise RuntimeError("Scanner is busy. Wait for an active scan to finish.")
        if len(_JOBS) >= MAX_RETAINED_JOBS:
            raise RuntimeError("Scan history is full. Try again later.")
        _JOBS[job_id] = job
        _ACTIVE += 1
    asyncio.create_task(_run(job_id, target))
    return public_job(job)


async def _run(job_id: str, target: str) -> None:
    global _ACTIVE
    async with _LOCK:
        job = _JOBS.get(job_id)
        if not job:
            _ACTIVE = max(0, _ACTIVE - 1)
            return
        job.update(status="running", progress=10, updated_at=_now())
    try:
        result = await scan_website(target)
        async with _LOCK:
            job = _JOBS.get(job_id)
            if job:
                job.update(status="completed", progress=100, result=result, updated_at=_now())
    except ValueError as exc:
        async with _LOCK:
            job = _JOBS.get(job_id)
            if job:
                job.update(status="failed", error=str(exc), updated_at=_now())
    except Exception:
        async with _LOCK:
            job = _JOBS.get(job_id)
            if job:
                job.update(status="failed", error="The scan could not complete safely.", updated_at=_now())
    finally:
        async with _LOCK:
            _ACTIVE = max(0, _ACTIVE - 1)


def public_job(job: dict[str, Any]) -> dict[str, Any]:
    # Return a detached snapshot so API consumers cannot mutate shared job state.
    return dict(job)


async def get_job(job_id: str) -> dict[str, Any] | None:
    async with _LOCK:
        job = _JOBS.get(job_id)
        return public_job(job) if job else None
