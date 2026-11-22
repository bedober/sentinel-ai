#!/data/data/com.termux/files/usr/bin/python
"""Sentinel Shell: original local command console for Termux."""
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
import json, os, secrets, shlex, subprocess, threading

ROOT = Path.home() / "sentinel-workspace"
ROOT.mkdir(parents=True, exist_ok=True)
TOKEN = secrets.token_urlsafe(32)
BASH = str(Path(os.environ.get("PREFIX", "/data/data/com.termux/files/usr")) / "bin/bash")
STATE = {"cwd": str(ROOT)}
LOCK = threading.Lock()

PAGE = """<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>Sentinel Shell</title><style>
body{margin:0;background:#0b1117;color:#dce7ef;font:15px monospace;display:flex;flex-direction:column;height:100vh}
header{padding:15px;background:#14212b;color:#6be0ae}#cwd{float:right;color:#9bb0bf;font-size:12px}
#out{padding:16px;white-space:pre-wrap;overflow:auto;flex:1;line-height:1.5}
form{display:flex;padding:12px;background:#14212b;gap:8px}input{flex:1;min-width:0;background:#0b1117;color:white;border:1px solid #486174;padding:12px;font:inherit}button{background:#167858;color:white;border:0;padding:0 18px}
.prompt{color:#6be0ae}.error{color:#ff9999}</style></head><body><header><b>SENTINEL SHELL</b><span id=cwd></span></header><main id=out>Original Sentinel command console. Enter help for available commands.\n</main><form id=f><input id=c autocomplete=off placeholder='Enter command…' autofocus><button>RUN</button></form>
<script>
const out=document.querySelector('#out'),cwd=document.querySelector('#cwd'),input=document.querySelector('#c');
let token=sessionStorage.getItem('sentinel_token')||prompt('Enter the one-time token printed in Termux');if(token)sessionStorage.setItem('sentinel_token',token);
function line(t,cl=''){let e=document.createElement('div');e.className=cl;e.textContent=t;out.append(e);out.scrollTop=out.scrollHeight}
document.querySelector('#f').onsubmit=async e=>{e.preventDefault();let command=input.value.trim();if(!command)return;input.value='';line((cwd.textContent||'~')+' $ '+command,'prompt');
try{let r=await fetch('/run',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+token},body:JSON.stringify({command})});let d=await r.json();if(!r.ok)throw Error(d.error);cwd.textContent=d.cwd;if(d.output)line(d.output);if(d.error)line(d.error,'error')}catch(x){line(String(x),'error')}};
</script></body></html>"""

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def reply(self, code, obj, kind="application/json"):
        raw = obj.encode() if isinstance(obj, str) else json.dumps(obj).encode()
        self.send_response(code); self.send_header("Content-Type", kind+"; charset=utf-8")
        self.send_header("Content-Length", str(len(raw))); self.send_header("Cache-Control","no-store")
        self.end_headers(); self.wfile.write(raw)
    def do_GET(self):
        if self.path in ("/","/index.html"): self.reply(200,PAGE,"text/html")
        else: self.reply(404,{"error":"Not found"})
    def do_POST(self):
        if self.path!="/run": return self.reply(404,{"error":"Not found"})
        if self.headers.get("Authorization")!="Bearer "+TOKEN: return self.reply(401,{"error":"Invalid session token"})
        size=int(self.headers.get("Content-Length","0"))
        if size>8192: return self.reply(413,{"error":"Request too large"})
        try:
            command=json.loads(self.rfile.read(size)).get("command","")
            if not isinstance(command,str) or not command.strip() or len(command)>4000: raise ValueError("Command must be 1–4000 characters")
            with LOCK:
                if command.strip()=="pwd": output=STATE["cwd"]; error=""
                elif command.strip()=="clear": output=""; error=""
                elif command.strip().startswith("cd"):
                    args=shlex.split(command)
                    if len(args)>2: raise ValueError("Usage: cd [directory]")
                    path=Path(args[1] if len(args)==2 else str(ROOT)).expanduser()
                    if not path.is_absolute(): path=Path(STATE["cwd"])/path
                    path=path.resolve()
                    if not path.is_dir(): raise ValueError("Directory not found")
                    STATE["cwd"]=str(path); output=""; error=""
                else:
                    p=subprocess.run([BASH,"-lc",command],cwd=STATE["cwd"],capture_output=True,text=True,timeout=20)
                    output=p.stdout[-30000:]; error=p.stderr[-12000:]
                    if p.returncode: error=(error+"\n[exit "+str(p.returncode)+"]").strip()
                self.reply(200,{"output":output,"error":error,"cwd":STATE["cwd"]})
        except subprocess.TimeoutExpired: self.reply(408,{"error":"Command exceeded 20 seconds","cwd":STATE["cwd"]})
        except Exception as exc: self.reply(400,{"error":str(exc),"cwd":STATE["cwd"]})

if __name__=="__main__":
    print("\nSENTINEL SHELL — local session")
    print("Open on this phone: http://127.0.0.1:8765")
    print("One-time token: "+TOKEN)
    print("Workspace: "+str(ROOT))
    print("Runs as your Termux user; stop with Ctrl+C.\n")
    HTTPServer(("127.0.0.1",8765),Handler).serve_forever()
