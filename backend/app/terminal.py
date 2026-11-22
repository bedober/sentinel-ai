"""Restricted Termux-style command interface.

This is deliberately NOT an operating-system shell. Only explicit, reviewed
commands are accepted; user input is never passed to subprocess or eval.
"""
from urllib.parse import urlparse
from .scanner import scan_website
from .tool_registry import tool_inventory

HELP = """Sentinel AI terminal
  help                 Show this help
  version              Show Sentinel API version
  pwd                  Show the virtual workspace path
  ls                   List virtual workspace entries
  tools                Show security-engine readiness
  scan <https-url>     Run passive checks (requires authorization)
  clear                Clear the terminal screen (client-side)

This is a restricted security console, not a general Linux shell.
Arbitrary shell commands, package installation, privilege escalation,
exploit execution, and access to host files are disabled.
"""

async def execute_terminal_command(command: str, authorized: bool = False):
    raw = command.strip()
    if not raw:
        return {"kind": "output", "output": ""}
    parts = raw.split()
    verb = parts[0].lower()
    if verb == "help" and len(parts) == 1:
        return {"kind": "output", "output": HELP}
    if verb == "version" and len(parts) == 1:
        return {"kind": "output", "output": "Sentinel AI API v0.3.0"}
    if verb == "pwd" and len(parts) == 1:
        return {"kind": "output", "output": "/sentinel/workspace"}
    if verb == "ls" and len(parts) == 1:
        return {"kind": "output", "output": "reports/\ntargets/\ntools/\nREADME.txt"}
    if verb == "tools" and len(parts) == 1:
        inventory = tool_inventory()
        lines = [f"{item['name']}: {'detected' if item['installed'] else 'not installed'} ({item['status']})"
                 for item in inventory.get("tools", [])]
        return {"kind": "output", "output": "\n".join(lines) or "No tool inventory available."}
    if verb == "scan" and len(parts) == 2:
        if not authorized:
            return {"kind": "error", "output": "Permission required. Enable the authorization checkbox before running a scan."}
        target = parts[1]
        parsed = urlparse(target)
        if parsed.scheme != "https" or not parsed.netloc:
            return {"kind": "error", "output": "Use a complete HTTPS URL, for example: scan https://example.com"}
        result = await scan_website(target)
        return {"kind": "scan", "output": f"Passive scan complete: {result.get('score', 'N/A')}/100\nHTTP status: {result.get('http_status', 'N/A')}\nFindings: {len(result.get('findings', []))}", "result": result}
    return {"kind": "error", "output": f"Command not available: {verb}. Type 'help' for supported commands."}
