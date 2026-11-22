"""Inventory of requested security tools and their honest integration state.

Presence on PATH does not mean a tool is configured or safe to auto-run. High-impact
features remain disabled unless a reviewed, scoped adapter is implemented.
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
    automation: str
    note: str


TOOLS = (
    Tool("Nmap", "nmap", "Bounded service discovery on approved hosts", "active", "planned", "Requires explicit host and port scope."),
    Tool("Masscan", "masscan", "High-speed port discovery", "active", "disabled", "High traffic rate; not enabled for unattended scans."),
    Tool("Amass", "amass", "Passive asset and DNS enumeration", "passive", "planned", "Passive sources only in the safe profile."),
    Tool("Gobuster", "gobuster", "Content and directory discovery", "active", "disabled", "Brute-force-style path enumeration is not in unattended mode."),
    Tool("ffuf", "ffuf", "Web fuzzing", "active", "disabled", "Fuzzing can create load and alter application state."),
    Tool("Nessus", "nessus", "Commercial vulnerability assessment", "active", "external", "Requires a separately licensed installation and supported host."),
    Tool("OpenVAS / Greenbone", "gvm-cli", "Vulnerability management", "active", "external", "Heavy server-side service; not an Android-local package."),
    Tool("Nikto", "nikto", "Web-server configuration checks", "active", "planned", "Only bounded checks against explicitly approved web targets."),
    Tool("Burp Suite", "burpsuite", "Web security proxy and testing suite", "interactive", "external", "Requires separate installation; proxy workflows are interactive."),
    Tool("OWASP ZAP", "zap.sh", "Web application baseline and passive analysis", "active", "planned", "Safe baseline profile only; active attack rules excluded."),
    Tool("sqlmap", "sqlmap", "SQL injection testing", "active", "disabled", "Exploit-oriented testing is not run unattended."),
    Tool("Metasploit Framework", "msfconsole", "Exploit validation framework", "active", "disabled", "Exploit execution is excluded from automatic scans."),
    Tool("BloodHound", "bloodhound", "Active Directory relationship analysis", "local", "external", "Requires a separately provisioned AD data collection workflow."),
    Tool("Impacket", "impacket-smbclient", "Windows protocol and AD utilities", "active", "restricted", "Only non-authenticating inventory adapters may be considered."),
    Tool("NetExec", "nxc", "Network service assessment", "active", "disabled", "Credential and lateral-movement features are not unattended."),
    Tool("LinPEAS / WinPEAS", "linpeas.sh", "Local privilege configuration auditing", "local", "planned", "Run only against a host or image the operator controls."),
    Tool("Hashcat", "hashcat", "Password hash recovery", "local", "disabled", "Password cracking is not part of the automated scanner."),
    Tool("John the Ripper", "john", "Password hash auditing", "local", "disabled", "Password cracking is not part of the automated scanner."),
    Tool("Hydra", "hydra", "Login authentication testing", "active", "disabled", "Online password guessing is not run automatically."),
    Tool("Aircrack-ng", "aircrack-ng", "Wireless network auditing", "local", "disabled", "Requires compatible Wi-Fi hardware and explicit lab workflow."),
    Tool("BeEF", "beef-xss", "Browser exploitation framework", "active", "disabled", "Browser hooking and exploitation are excluded."),
)


def tool_inventory() -> dict:
    items = []
    for tool in TOOLS:
        row = asdict(tool)
        row["installed"] = shutil.which(tool.binary) is not None
        row["status"] = (
            "installed_not_integrated" if row["installed"]
            else "not_installed"
        )
        items.append(row)
    return {
        "tools": items,
        "installed": sum(item["installed"] for item in items),
        "total": len(items),
        "automatic_profile": "passive-web",
        "note": (
            "Only the passive-web job is currently executable through the API. "
            "Tool presence is not proof of a working adapter. Disabled tools require "
            "a separate safety and compatibility review; external products need "
            "their own licenses and supported hosts."
        ),
    }
