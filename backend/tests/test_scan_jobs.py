import asyncio

from app import scan_jobs


def test_create_job_runs_and_returns_result(monkeypatch):
    async def fake_scan(target):
        return {"target": target, "score": 100, "findings": []}

    monkeypatch.setattr(scan_jobs, "scan_website", fake_scan)
    scan_jobs._JOBS.clear()
    scan_jobs._ACTIVE = 0

    async def scenario():
        created = await scan_jobs.create_web_scan("https://example.com")
        assert created["status"] == "queued"
        job_id = created["id"]

        for _ in range(50):
            current = await scan_jobs.get_job(job_id)
            if current["status"] in {"completed", "failed"}:
                break
            await asyncio.sleep(0)

        assert current["status"] == "completed"
        assert current["result"]["score"] == 100
        assert current["progress"] == 100

    asyncio.run(scenario())


def test_job_snapshot_cannot_mutate_internal_state():
    scan_jobs._JOBS.clear()
    scan_jobs._ACTIVE = 0
    job = {"id": "snapshot-test", "status": "queued", "created_at": "2026-11-22T00:00:00+00:00"}
    scan_jobs._JOBS[job["id"]] = job

    async def scenario():
        snapshot = await scan_jobs.get_job(job["id"])
        snapshot["status"] = "completed"
        assert scan_jobs._JOBS[job["id"]]["status"] == "queued"

    asyncio.run(scenario())
