import os
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from .scanner import scan_website
from .tool_registry import tool_inventory
from .terminal import execute_terminal_command

app = FastAPI(title="Sentinel AI API", version="0.2.0",
              description="Authorized security posture checks and tool readiness inventory.")
origins = [x.strip() for x in os.getenv("CORS_ORIGINS", "http://localhost:5173").split(",") if x.strip()]
app.add_middleware(CORSMiddleware, allow_origins=origins, allow_credentials=False,
                   allow_methods=["GET", "POST"], allow_headers=["Content-Type"])

class ScanRequest(BaseModel):
    target: str = Field(min_length=4, max_length=2048)
    authorized: bool = Field(description="True only when you own or have written permission to assess this target.")

@app.get("/health")
async def health():
    return {"status": "ok", "service": "sentinel-ai"}

@app.post("/api/terminal/execute")
async def terminal_execute(body: dict):
    command = str(body.get("command", ""))[:500]
    authorized = body.get("authorized") is True
    try:
        return await execute_terminal_command(command, authorized)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    except Exception:
        raise HTTPException(502, "Terminal command failed safely.")

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
        raise HTTPException(502, "The target could not be scanned safely.") from exc

@app.get("/")
async def root():
    return {"name": "Sentinel AI", "docs": "/docs", "health": "/health", "tools": "/api/tools"}

from .ai import explain_findings

class ExplainRequest(BaseModel):
    findings: list[dict] = Field(min_length=1, max_length=30)

@app.post("/api/explain")
async def explain(body: ExplainRequest):
    try:
        explanation = await explain_findings(body.findings)
        return {"explanation": explanation, "provider": "Hugging Face Inference API"}
    except Exception as exc:
        raise HTTPException(502, "AI explanation service is unavailable.") from exc
