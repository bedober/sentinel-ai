import React, { useEffect, useRef, useState } from 'react';
const API = import.meta.env.VITE_API_BASE || 'http://localhost:8000';
export default function App() {
  const [target,setTarget] = useState('https://example.com');
  const [authorized,setAuthorized] = useState(false);
  const [busy,setBusy] = useState(false);
  const [result,setResult] = useState(null);
  const [error,setError] = useState('');
  const [inventory,setInventory] = useState(null);
  const [inventoryError,setInventoryError] = useState('');
  const [command,setCommand] = useState('');
  const [terminalBusy,setTerminalBusy] = useState(false);
  const [history,setHistory] = useState([{type:'system',text:'Sentinel AI terminal v0.3.0 — restricted command environment. Type help to begin.'}]);
  const terminalEnd = useRef(null);
  useEffect(() => {
    fetch(API + '/api/tools')
      .then(async r => { if (!r.ok) throw new Error('Tool inventory unavailable'); return r.json(); })
      .then(setInventory).catch(e => setInventoryError(e.message));
  }, []);
  useEffect(() => { terminalEnd.current?.scrollIntoView({behavior:'smooth'}); }, [history]);
  async function scan(e) {
    e.preventDefault(); setBusy(true); setError(''); setResult(null);
    try {
      const r = await fetch(API + '/api/scan',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({target,authorized})});
      const data = await r.json(); if (!r.ok) throw new Error(data.detail || 'Scan failed');
      setResult(data);
    } catch(err) { setError(err.message || 'Could not reach the scanner API.'); }
    finally { setBusy(false); }
  }
  async function runCommand(e) {
    e.preventDefault();
    const cmd = command.trim();
    if (!cmd || terminalBusy) return;
    setCommand('');
    if (cmd.toLowerCase() === 'clear') { setHistory([]); return; }
    setHistory(h => [...h,{type:'prompt',text:cmd}]);
    setTerminalBusy(true);
    try {
      const r = await fetch(API + '/api/terminal/execute',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({command:cmd,authorized})});
      const data = await r.json();
      if (!r.ok) throw new Error(data.detail || 'Command failed');
      setHistory(h => [...h,{type:data.kind === 'error' ? 'error' : 'output',text:data.output || ''}]);
      if (data.result) setResult(data.result);
    } catch(err) {
      setHistory(h => [...h,{type:'error',text:err.message || 'Terminal API unavailable.'}]);
    } finally { setTerminalBusy(false); }
  }
  return <main className="shell">
    <header><div className="brandmark">S</div><div><strong>SENTINEL <span>AI</span></strong><small>SECURITY POSTURE MONITOR</small></div><label className="status">● PASSIVE MODE</label></header>
    <section className="hero"><p className="eyebrow">DEFENSIVE SECURITY • AUTHORIZED TESTING</p><h1>See your security<br/><em>before attackers do.</em></h1><p className="muted">Run a safe, non-invasive website configuration review and turn findings into clear next steps.</p></section>
    <form className="scanbox" onSubmit={scan}><label htmlFor="target">WEBSITE URL</label><div className="inputrow"><input id="target" value={target} onChange={e=>setTarget(e.target.value)} placeholder="https://yourdomain.com" required/><button disabled={busy || !authorized}>{busy?'SCANNING…':'SCAN WEBSITE →'}</button></div>
      <label className="consent"><input type="checkbox" checked={authorized} onChange={e=>setAuthorized(e.target.checked)}/> I own this website or have explicit permission to assess it.</label>
    </form>
    {error && <div className="error">{error}</div>}
    <section className="results terminal-panel">
      <div className="terminal-heading"><div><span className="terminal-lights">● ● ●</span><h2>Sentinel Terminal</h2></div><small>TERMUX-STYLE CONSOLE</small></div>
      <div className="terminal-screen" role="log" aria-live="polite">
        {history.map((line,i)=><div className={'terminal-line '+line.type} key={i}>{line.type==='prompt' ? <><b>sentinel@mobile:~$</b> {line.text}</> : line.text}</div>)}
        {terminalBusy && <div className="terminal-line system">Running approved command…</div>}
        <div ref={terminalEnd}/>
      </div>
      <form className="terminal-input" onSubmit={runCommand}><span>sentinel@mobile:~$</span><input value={command} onChange={e=>setCommand(e.target.value)} placeholder="Type help…" aria-label="Terminal command" autoComplete="off" disabled={terminalBusy}/><button disabled={terminalBusy || !command.trim()}>RUN</button></form>
      <div className="terminal-foot"><span>{authorized ? '● AUTHORIZATION CONFIRMED' : '○ SCAN AUTHORIZATION REQUIRED'}</span><span>Restricted command set · no arbitrary shell</span></div>
    </section>
    {result && <section className="results"><div className="score"><div><small>SECURITY CONFIGURATION SCORE</small><strong>{result.score}<i>/100</i></strong></div><span>HTTP {result.http_status}</span></div><p className="muted">{result.target}</p><h2>Findings</h2>{result.findings.map(f=><article className="finding" key={f.id}><span className={'severity '+f.severity}>{f.severity}</span><div><b>{f.title}</b><p>{f.evidence}</p><small>{f.recommendation}</small></div></article>)}<p className="footnote">{result.notice}</p></section>}
    <section className="results"><h2>Security engine readiness</h2>
      {inventory ? <><p className="muted">{inventory.installed} of {inventory.total} command-line tools detected on this API host.</p>
        {inventory.tools.map(t=><article className="finding" key={t.name}><span className={'severity '+(t.installed?'low':'info')}>{t.installed?'DETECTED':'MISSING'}</span><div><b>{t.name}</b><p>{t.purpose}</p><small>{t.status.replaceAll('_',' ')} · {t.mode} assessment</small></div></article>)}
        <p className="footnote">{inventory.note}</p></> : <p className="muted">{inventoryError || 'Checking installed tools…'}</p>}
    </section>
    <footer><span>Sentinel AI · Early-stage project</span><span>Passive checks only · Tool execution disabled</span></footer>
  </main>;
}
