"""Restricted Linux worker for Sentinel AI.

Only runs bounded Nmap TCP connect/version checks against administrator-allowlisted
CIDRs. Commands are passed as argv (never through a shell), and results are parsed
from XML. This worker is not a general-purpose command execution endpoint.
"""
from __future__ import annotations

import asyncio
import ipaddress
import os
import secrets
import shutil
import socket
import xml.etree.ElementTree as ET
from typing import Iterable

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

TOKEN = os.getenv("SENTINEL_WORKER_TOKEN", "")
ALLOWED_CIDRS = [
    ipaddress.ip_network(item.strip(), strict=False)
    for item in os.getenv("SENTINEL_ALLOWED_CIDRS", "").split(",")
    if item.strip()
]
MAX_PORTS = 256
app = FastAPI(title="Sentinel AI Linux Worker", version="0.1.0")


class NmapRequest(BaseModel):
    target: str = Field(min_length=1, max_length=253)
    ports: str = Field(default="1-1024", max_length=512)
    authorized: bool = False


def _auth(authorization: str | None) -> None:
    if not TOKEN or not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Worker authentication is not configured.")
    supplied = authorization[7:]
    if not secrets.compare_digest(supplied, TOKEN):
        raise HTTPException(401, "Invalid worker token.")


def _parse_ports(raw: str) -> str:
    selected: set[int] = set()
    for part in raw.split(","):
        part = part.strip()
        if not part:
            raise ValueError("Empty port entry.")
        if "-" in part:
            bits = part.split("-", 1)
            if not all(bit.isdigit() for bit in bits):
                raise ValueError("Ports must be numbers or numeric ranges.")
            start, end = map(int, bits)
            if start < 1 or end > 65535 or start > end:
                raise ValueError("Invalid port range.")
            if end - start + 1 > MAX_PORTS:
                raise ValueError(f"A maximum of {MAX_PORTS} ports may be scanned.")
            selected.update(range(start, end + 1))
        else:
            if not part.isdigit() or not 1 <= int(part) <= 65535:
                raise ValueError("Invalid TCP port.")
            selected.add(int(part))
        if len(selected) > MAX_PORTS:
            raise ValueError(f"A maximum of {MAX_PORTS} ports may be scanned.")
    if not selected:
        raise ValueError("At least one TCP port is required.")
    return ",".join(str(port) for port in sorted(selected))


def _resolve_and_check(target: str) -> list[str]:
    if not ALLOWED_CIDRS:
        raise ValueError("Server administrator has not configured allowed target CIDRs.")
    try:
        ipaddress.ip_address(target)
        addresses = {str(ipaddress.ip_address(target))}
    except ValueError:
        try:
            infos = socket.getaddrinfo(target, None, type=socket.SOCK_STREAM)
            addresses = {str(ipaddress.ip_address(info[4][0])) for info in infos}
        except (socket.gaierror, ValueError) as exc:
            raise ValueError("Target hostname could not be resolved.") from exc
    if not addresses:
        raise ValueError("Target did not resolve to an address.")
    for address in addresses:
        ip = ipaddress.ip_address(address)
        if not any(ip.version == network.version and ip in network for network in ALLOWED_CIDRS):
            raise ValueError("Target is outside the server administrator's allowlist.")
    return sorted(addresses)


def _parse_xml(xml_data: str) -> dict:
    root = ET.fromstring(xml_data)
    hosts = []
    for host in root.findall("host"):
        addr = next((node.get("addr") for node in host.findall("address")
                     if node.get("addrtype") in ("ipv4", "ipv6")), None)
        ports = []
        for port in host.findall("./ports/port"):
            state = port.find("state")
            if state is None or state.get("state") != "open":
                continue
            service = port.find("service")
            ports.append({
                "port": int(port.get("portid", "0")),
                "protocol": port.get("protocol", "tcp"),
                "service": service.get("name", "unknown") if service is not None else "unknown",
                "product": service.get("product", "") if service is not None else "",
                "version": service.get("version", "") if service is not None else "",
            })
        hosts.append({"address": addr, "ports": ports})
    return {"hosts": hosts, "open_ports": sum(len(h["ports"]) for h in hosts)}


@app.get("/health")
async def health():
    return {"status": "ok", "service": "sentinel-linux-worker",
            "nmap_available": shutil.which("nmap") is not None,
            "allowlist_configured": bool(ALLOWED_CIDRS)}


@app.post("/api/nmap")
async def run_nmap(body: NmapRequest, authorization: str | None = Header(default=None)):
    _auth(authorization)
    if not body.authorized:
        raise HTTPException(400, "Confirm written authorization for this target.")
    if shutil.which("nmap") is None:
        raise HTTPException(503, "Nmap is not installed on the Linux worker.")
    try:
        ports = _parse_ports(body.ports)
        targets = _resolve_and_check(body.target)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc

    results = []
    for target_ip in targets:
        command = ["nmap", "-sT", "-sV", "--reason", "--open", "-n",
                   "-p", ports, "-oX", "-", target_ip]
        try:
            process = await asyncio.create_subprocess_exec(
                *command, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE
            )
            stdout, stderr = await asyncio.wait_for(process.communicate(), timeout=180)
        except asyncio.TimeoutError:
            process.kill()
            await process.wait()
            raise HTTPException(504, "Nmap scan exceeded the 180-second worker limit.")
        if process.returncode not in (0, 1):
            raise HTTPException(502, "Nmap failed: " + stderr.decode("utf-8", "replace")[:500])
        try:
            results.append(_parse_xml(stdout.decode("utf-8", "replace")))
        except ET.ParseError as exc:
            raise HTTPException(502, "Nmap returned invalid XML output.") from exc
    return {"tool": "nmap", "mode": "tcp-connect", "targets": targets,
            "ports": ports, "results": results,
            "notice": "Authorized, allowlisted TCP scan. No raw-packet or UDP scan was run."}
