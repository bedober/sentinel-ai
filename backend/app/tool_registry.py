"""Read-only inventory of optional security assessment engines.

The API reports whether binaries are present; it never launches scans. Tool execution
is deliberately delegated to an isolated worker and must be enabled only with
written authorization and per-target scope controls.
"""
from __future__ import annotations

import shutil
from dataclasses import asdict, dataclass


@dataclass(frozen=True)
class Tool:
    name: str
    binary: str
    purpose: str
    mode: str
    execution: str = "not_connected"


TOOLS = (
    Tool("Nmap", "nmap", "TCP service and port discovery", "active"),
    Tool("OWASP ZAP", "zap.sh", "Web application baseline and passive analysis", "active"),
    Tool("Nuclei", "nuclei", "Template-based known-exposure checks", "active"),
    Tool("WhatWeb", "whatweb", "Web technology fingerprinting", "passive"),
    Tool("Subfinder", "subfinder", "Passive subdomain discovery", "passive"),
    Tool("Amass", "amass", "Attack-surface and DNS enumeration", "passive"),
    Tool("Nikto", "nikto", "Web-server configuration checks", "active"),
    Tool("testssl.sh", "testssl.sh", "TLS protocol and cipher audit", "active"),
    Tool("Trivy", "trivy", "Container, filesystem and dependency scanning", "local"),
    Tool("Semgrep", "semgrep", "Source-code static analysis", "local"),
)


def tool_inventory() -> dict:
    items = []
    for tool in TOOLS:
        available = shutil.which(tool.binary) is not None
        row = asdict(tool)
        row["installed"] = available
        row["status"] = "installed_not_integrated" if available else "not_installed"
        items.append(row)
    return {
        "tools": items,
        "installed": sum(item["installed"] for item in items),
        "total": len(items),
        "note": (
            "Binary presence is not proof of a working integration. Scan execution "
            "is disabled until isolated worker adapters, authorization verification, "
            "target allowlisting and regression tests are configured."
        ),
    }
