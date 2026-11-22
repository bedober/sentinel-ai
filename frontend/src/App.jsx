import React, { useState } from 'react';
const API = import.meta.env.VITE_API_BASE || 'http://localhost:8000';
export default function App() {
  const [target,setTarget] = useState('https://example.com');
  const [authorized,setAuthorized] = useState(false);
  const [busy,setBusy] = useState(false);
  const [result,setResult] = useState(null);
  const [error,setError] = useState('');
  async function scan(e) {
    e.preventDefault(); setBusy(true); setError(''); setResult(null);
    try {
      const r = await fetch(API + '/api/scan',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({target,authorized})});
      const data = await r.json(); if (!r.ok) throw new Error(data.detail || 'Scan failed');
      setResult(data);
    } catch(err) { setError(err.message || 'Could not reach the scanner API.'); }
    finally { setBusy(false); }
  }
  return <main className="shell">
    <header><div className="brandmark">S</div><div><strong>SENTINEL <span>AI</span></strong><small>SECURITY POSTURE MONITOR</small></div><label className="status">● PASSIVE MODE</label></header>
    <section className="hero"><p className="eyebrow">DEFENSIVE SECURITY • AUTHORIZED TESTING</p><h1>See your security<br/><em>before attackers do.</em></h1><p className="muted">Run a safe, non-invasive website configuration review and turn findings into clear next steps.</p></section>
    <form className="scanbox" onSubmit={scan}><label htmlFor="target">WEBSITE URL</label><div className="inputrow"><input id="target" value={target} onChange={e=>setTarget(e.target.value)} placeholder="https://yourdomain.com" required/><button disabled={busy || !authorized}>{busy?'SCANNING…':'SCAN WEBSITE →'}</button></div>
      <label className="consent"><input type="checkbox" checked={authorized} onChange={e=>setAuthorized(e.target.checked)}/> I own this website or have explicit permission to assess it.</label>
    </form>
    {error && <div className="error">{error}</div>}
    {result && <section className="results"><div className="score"><div><small>SECURITY CONFIGURATION SCORE</small><strong>{result.score}<i>/100</i></strong></div><span>HTTP {result.http_status}</span></div><p className="muted">{result.target}</p><h2>Findings</h2>{result.findings.map(f=><article className="finding" key={f.id}><span className={'severity '+f.severity}>{f.severity}</span><div><b>{f.title}</b><p>{f.evidence}</p><small>{f.recommendation}</small></div></article>)}<p className="footnote">{result.notice}</p></section>}
    <footer><span>Sentinel AI · Open source starter</span><span>Passive checks only · No exploitation</span></footer>
  </main>;
}
