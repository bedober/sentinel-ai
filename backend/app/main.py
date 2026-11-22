import os
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field, HttpUrl
from .scanner import scan_website

app = FastAPI(title="Sentinel AI API", version="0.1.0",
              description="Authorized, passive website security posture checks.")
origins = [x.strip() for x in os.getenv("CORS_ORIGINS", "http://localhost:5173").split(",") if x.strip()]
app.add_middleware(CORSMiddleware, allow_origins=origins, allow_credentials=False,
                   allow_methods=["GET", "POST"], allow_headers=["Content-Type"])

class ScanRequest(BaseModel):
    target: str = Field(min_length=4, max_length=2048)
    authorized: bool = Field(description="Must be true only when you own or are authorized to assess this target.")

@app.get("/health")
async def health():
    return {"status": "ok", "service": "sentinel-ai"}

@app.post("/api/scan")
async def create_scan(body: ScanRequest):
    if not body.authorized:
        raise HTTPException(400, "Confirm that you own or have written permission to assess this target.")
    try:
        return await scan_website(body.target)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    except Exception as exc:
        # Do not expose internal network or stack details to clients.
        raise HTTPException(502, "The target could not be scanned safely. Check the URL and try again.") from exc

@app.get("/")
async def root():
    return {"name": "Sentinel AI", "docs": "/docs", "health": "/health"}
