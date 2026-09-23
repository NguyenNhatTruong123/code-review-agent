import { useCallback, useEffect, useState } from 'react';
import { api, query } from './api.js';

const severities = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
const emptyRule = { name: '', description: '', category: 'MAINTAINABILITY', severity: 'MEDIUM', languages: 'ALL', instruction: '', matchText: '', suggestedFix: '', enabled: true };
const emptySet = { name: '', description: '', ruleIds: [], enabled: true };

function ErrorNotice({ message, clear }) {
  return message ? <div className="notice error" role="alert">{message}<button type="button" onClick={clear} aria-label="Dismiss error">×</button></div> : null;
}

function Auth({ onLogin }) {
  const [mode, setMode] = useState('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      if (mode === 'register') await api('/auth/register', { method: 'POST', body: { username, password } });
      await api('/auth/login', { method: 'POST', body: { username, password } });
      onLogin(await api('/auth/me'));
    } catch (e) { setError(e.message); }
    finally { setBusy(false); }
  }
  return <main className="auth-shell">
    <div className="brand-mark">{'{ }'}</div>
    <h1>Code Review Agent</h1>
    <p>Review public GitHub repositories or pasted code against your rules.</p>
    <form className="card auth-card" onSubmit={submit}>
      <h2>{mode === 'login' ? 'Sign in' : 'Create account'}</h2>
      <ErrorNotice message={error} clear={() => setError('')} />
      <label>Username<input value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" required minLength={3} maxLength={80} /></label>
      <label>Password<input type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required minLength={12} /></label>
      <button disabled={busy} className="primary">{busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}</button>
      <button type="button" className="text-button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); }}>
        {mode === 'login' ? 'Need an account? Register' : 'Already have an account? Sign in'}
      </button>
    </form>
  </main>;
}

function ReviewForm({ sets, onCreated }) {
  const [source, setSource] = useState('repository');
  const [repositoryUrl, setRepositoryUrl] = useState('');
  const [ref, setRef] = useState('');
  const [repoInfo, setRepoInfo] = useState(null);
  const [code, setCode] = useState('');
  const [fileName, setFileName] = useState('');
  const [language, setLanguage] = useState('');
  const [ruleSetId, setRuleSetId] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const enabledSets = sets.filter(set => set.enabled);
  async function inspect() {
    setError(''); setBusy(true); setRepoInfo(null);
    try { setRepoInfo(await api(`/github/inspect?${query({ url: repositoryUrl })}`)); }
    catch (e) { setError(e.message); }
    finally { setBusy(false); }
  }
  async function submit(event) {
    event.preventDefault(); setError(''); setBusy(true);
    try {
      const result = source === 'repository'
        ? await api('/reviews/repository', { method: 'POST', body: { repositoryUrl, ref, ruleSetId } })
        : await api('/reviews/paste', { method: 'POST', body: { code, fileName, language, ruleSetId } });
      onCreated(result);
    } catch (e) { setError(e.message); }
    finally { setBusy(false); }
  }
  return <section className="card">
    <div className="section-heading"><div><h2>New review</h2><p>Select source code and the rules to apply.</p></div></div>
    <div className="segmented" role="group" aria-label="Source type">
      <button type="button" className={source === 'repository' ? 'selected' : ''} onClick={() => setSource('repository')}>GitHub repository</button>
      <button type="button" className={source === 'paste' ? 'selected' : ''} onClick={() => setSource('paste')}>Paste code</button>
    </div>
    <ErrorNotice message={error} clear={() => setError('')} />
    <form onSubmit={submit} className="form-stack">
      {source === 'repository' ? <>
        <label>Public repository URL<input type="url" placeholder="https://github.com/owner/repo" value={repositoryUrl} onChange={e => { setRepositoryUrl(e.target.value); setRepoInfo(null); }} required /></label>
        <div className="inline"><button type="button" onClick={inspect} disabled={busy || !repositoryUrl}>Inspect repository</button>{repoInfo && <span className="muted">Default branch: {repoInfo.defaultBranch}</span>}</div>
        <label>Branch or commit <span className="optional">(optional)</span><input value={ref} onChange={e => setRef(e.target.value)} placeholder={repoInfo?.defaultBranch || 'Uses default branch'} list="branches" /></label>
        <datalist id="branches">{repoInfo?.branches?.map(branch => <option key={branch} value={branch} />)}</datalist>
      </> : <>
        <label>Code<textarea className="code-input" value={code} onChange={e => setCode(e.target.value)} required maxLength={100000} rows={12} placeholder="Paste source code here" /></label>
        <div className="grid-two"><label>File name <span className="optional">(optional)</span><input value={fileName} onChange={e => setFileName(e.target.value)} placeholder="Example.java" /></label>
          <label>Language<select value={language} onChange={e => setLanguage(e.target.value)}><option value="">Detect from file name</option>{['JAVA', 'JAVASCRIPT', 'TYPESCRIPT', 'JSX', 'TSX', 'PYTHON', 'GO', 'CSHARP', 'RUBY', 'PHP', 'RUST', 'KOTLIN', 'SWIFT', 'C', 'CPP', 'VUE', 'SQL'].map(item => <option key={item}>{item}</option>)}</select></label></div>
      </>}
      <label>Rule set<select value={ruleSetId} onChange={e => setRuleSetId(e.target.value)} required><option value="">Select a rule set</option>{enabledSets.map(set => <option key={set.id} value={set.id}>{set.name}</option>)}</select></label>
      {enabledSets.length === 0 && <p className="muted">Create and enable a rule set before starting a review.</p>}
      <p className="hint">Code is processed by the server. Rules without a literal match use the configured AI provider.</p>
      <button className="primary" disabled={busy || !ruleSetId}>{busy ? 'Submitting…' : 'Start review'}</button>
    </form>
  </section>;
}

function ReviewDetail({ review, rules, onRefresh, onBack }) {
  const [findings, setFindings] = useState({ items: [], total: 0, page: 0, size: 50 });
  const [severity, setSeverity] = useState('');
  const [ruleId, setRuleId] = useState('');
  const [file, setFile] = useState('');
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const load = useCallback(async () => {
    try { setFindings(await api(`/reviews/${review.id}/findings?${query({ severity, ruleId, file, page, size: 50 })}`)); }
    catch (e) { setError(e.message); }
  }, [review.id, severity, ruleId, file, page]);
  useEffect(() => { load(); }, [load, review.status]);
  useEffect(() => {
    if (!['QUEUED', 'RUNNING'].includes(review.status)) return undefined;
    const timer = setInterval(onRefresh, 2500);
    return () => clearInterval(timer);
  }, [review.status, onRefresh]);
  async function feedback(findingId, value) {
    try { await api(`/reviews/${review.id}/findings/${findingId}/feedback`, { method: 'POST', body: { feedback: value } }); await load(); }
    catch (e) { setError(e.message); }
  }
  async function cancel() {
    try { await api(`/reviews/${review.id}/cancel`, { method: 'POST' }); await onRefresh(); }
    catch (e) { setError(e.message); }
  }
  return <div className="stack">
    <button type="button" className="back-button" onClick={onBack}>← All reviews</button>
    <section className="card">
      <div className="section-heading"><div><h2>{review.repositoryUrl || review.fileName || 'Review'}</h2><p>Created {new Date(review.createdAt).toLocaleString()}</p></div><span className={`status ${review.status.toLowerCase()}`}>{review.status.replaceAll('_', ' ')}</span></div>
      <div className="meta-grid"><div><b>Rule set</b><span>{review.ruleSetName}</span></div><div><b>Scanned files</b><span>{review.scannedFiles}</span></div><div><b>Skipped files</b><span>{review.skippedFiles}</span></div>{review.commitSha && <div><b>Commit</b><code title={review.commitSha}>{review.commitSha.slice(0, 12)}</code></div>}</div>
      {review.warning && <div className="notice warning">{review.warning}</div>}{review.error && <div className="notice error">{review.error}</div>}
      {['QUEUED', 'RUNNING'].includes(review.status) && <div className="inline"><span className="muted">Review in progress…</span><button type="button" onClick={cancel}>Cancel</button></div>}
      <div className="summary">{severities.map(s => <span key={s} className={`severity ${s.toLowerCase()}`}>{s}: {review.summary?.[s] || 0}</span>)}</div>
    </section>
    <section className="card">
      <div className="section-heading"><div><h2>Findings</h2><p>{findings.total} matching findings</p></div></div>
      <ErrorNotice message={error} clear={() => setError('')} />
      <div className="filter-row"><label>Severity<select value={severity} onChange={e => { setSeverity(e.target.value); setPage(0); }}><option value="">All</option>{severities.map(s => <option key={s}>{s}</option>)}</select></label>
        <label>Rule<select value={ruleId} onChange={e => { setRuleId(e.target.value); setPage(0); }}><option value="">All</option>{rules.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}</select></label>
        <label>File<input value={file} onChange={e => { setFile(e.target.value); setPage(0); }} placeholder="Exact file path" /></label></div>
      {findings.items.length === 0 ? <p className="empty">No findings for these filters.</p> : findings.items.map(f => <article className="finding" key={f.id}>
        <div className="finding-head"><span className={`severity ${f.severity.toLowerCase()}`}>{f.severity}</span><h3>{f.title}</h3><span className="muted">{f.source}</span></div>
        <p className="location">{review.commitSha && f.lineStart ? <a href={`${review.repositoryUrl}/blob/${review.commitSha}/${f.filePath.split('/').map(encodeURIComponent).join('/')}#L${f.lineStart}${f.lineEnd !== f.lineStart ? `-L${f.lineEnd}` : ''}`} target="_blank" rel="noopener noreferrer">{f.filePath}:{f.lineStart}{f.lineEnd !== f.lineStart ? `–${f.lineEnd}` : ''} ↗</a> : <>{f.filePath}{f.lineStart ? `:${f.lineStart}${f.lineEnd !== f.lineStart ? `–${f.lineEnd}` : ''}` : ''}</>}</p>
        <p>{f.explanation}</p><pre className="evidence">{f.evidence}</pre><p><b>Suggested fix:</b> {f.suggestedFix}</p>
        <div className="finding-foot"><span className="muted">Rule: {rules.find(r => r.id === f.ruleId)?.name || f.ruleId} · v{f.ruleVersion}</span>
          <label>Feedback<select value={f.feedback || ''} onChange={e => feedback(f.id, e.target.value)}><option value="">No feedback</option><option value="HELPFUL">Helpful</option><option value="IRRELEVANT">Irrelevant</option><option value="FALSE_POSITIVE">False positive</option></select></label></div>
      </article>)}
      {findings.total > 50 && <div className="inline pagination"><button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button><span>Page {page + 1}</span><button disabled={(page + 1) * 50 >= findings.total} onClick={() => setPage(page + 1)}>Next</button></div>}
    </section>
  </div>;
}

function RuleManager({ rules, reload }) {
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyRule);
  const [error, setError] = useState('');
  async function save(event) {
    event.preventDefault(); setError('');
    try { await api(`/rules${editing ? `/${editing}` : ''}`, { method: editing ? 'PUT' : 'POST', body: form }); setEditing(null); setForm(emptyRule); await reload(); }
    catch (e) { setError(e.message); }
  }
  async function remove(id) {
    if (!window.confirm('Delete this rule?')) return;
    try { await api(`/rules/${id}`, { method: 'DELETE' }); await reload(); }
    catch (e) { setError(e.message); }
  }
  return <div className="stack"><section className="card"><h2>{editing ? 'Edit rule' : 'Create rule'}</h2><p className="muted">A literal match runs without AI. Leave it empty for semantic AI review.</p>
    <ErrorNotice message={error} clear={() => setError('')} />
    <form onSubmit={save} className="form-stack"><div className="grid-two"><label>Name<input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} required maxLength={120} /></label><label>Category<input value={form.category || ''} onChange={e => setForm({ ...form, category: e.target.value })} /></label></div>
      <label>Description<input value={form.description || ''} onChange={e => setForm({ ...form, description: e.target.value })} /></label>
      <div className="grid-two"><label>Severity<select value={form.severity} onChange={e => setForm({ ...form, severity: e.target.value })}>{severities.map(s => <option key={s}>{s}</option>)}</select></label><label>Languages <span className="optional">(comma separated or ALL)</span><input value={form.languages} onChange={e => setForm({ ...form, languages: e.target.value })} required /></label></div>
      <label>Instruction<textarea value={form.instruction} onChange={e => setForm({ ...form, instruction: e.target.value })} required rows={3} maxLength={4000} /></label>
      <label>Literal match <span className="optional">(optional, case sensitive)</span><input value={form.matchText || ''} onChange={e => setForm({ ...form, matchText: e.target.value })} maxLength={500} /></label>
      <label>Suggested fix<input value={form.suggestedFix || ''} onChange={e => setForm({ ...form, suggestedFix: e.target.value })} /></label>
      <label className="check"><input type="checkbox" checked={form.enabled} onChange={e => setForm({ ...form, enabled: e.target.checked })} /> Enabled</label>
      <div className="inline"><button className="primary">{editing ? 'Save rule' : 'Create rule'}</button>{editing && <button type="button" onClick={() => { setEditing(null); setForm(emptyRule); }}>Cancel</button>}</div>
    </form></section>
    <section className="card"><h2>Your rules</h2>{rules.length === 0 && <p className="empty">No rules yet.</p>}{rules.map(rule => <div className="list-row" key={rule.id}><div><b>{rule.name}</b><span className="muted">{rule.severity} · {rule.languages} · v{rule.version} · {rule.enabled ? 'Enabled' : 'Disabled'}</span></div><div className="inline"><button onClick={() => { setEditing(rule.id); setForm({ name: rule.name, description: rule.description || '', category: rule.category || '', severity: rule.severity, languages: rule.languages, instruction: rule.instruction, matchText: rule.matchText || '', suggestedFix: rule.suggestedFix || '', enabled: rule.enabled }); window.scrollTo(0, 0); }}>Edit</button><button onClick={() => remove(rule.id)}>Delete</button></div></div>)}</section>
  </div>;
}

function SetManager({ sets, rules, reload }) {
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptySet);
  const [error, setError] = useState('');
  async function save(event) {
    event.preventDefault(); setError('');
    try { await api(`/rule-sets${editing ? `/${editing}` : ''}`, { method: editing ? 'PUT' : 'POST', body: form }); setEditing(null); setForm(emptySet); await reload(); }
    catch (e) { setError(e.message); }
  }
  async function remove(id) {
    if (!window.confirm('Delete this rule set?')) return;
    try { await api(`/rule-sets/${id}`, { method: 'DELETE' }); await reload(); }
    catch (e) { setError(e.message); }
  }
  return <div className="stack"><section className="card"><h2>{editing ? 'Edit rule set' : 'Create rule set'}</h2><ErrorNotice message={error} clear={() => setError('')} />
    <form onSubmit={save} className="form-stack"><label>Name<input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} required maxLength={120} /></label>
      <label>Description<input value={form.description || ''} onChange={e => setForm({ ...form, description: e.target.value })} /></label>
      <fieldset><legend>Rules</legend>{rules.length === 0 && <p>Create a rule first.</p>}{rules.map(rule => <label className="check" key={rule.id}><input type="checkbox" checked={form.ruleIds.includes(rule.id)} onChange={e => setForm({ ...form, ruleIds: e.target.checked ? [...form.ruleIds, rule.id] : form.ruleIds.filter(id => id !== rule.id) })} /> {rule.name} <span className="muted">({rule.severity}{rule.enabled ? '' : ', disabled'})</span></label>)}</fieldset>
      <label className="check"><input type="checkbox" checked={form.enabled} onChange={e => setForm({ ...form, enabled: e.target.checked })} /> Enabled</label>
      <div className="inline"><button className="primary" disabled={form.ruleIds.length === 0}>{editing ? 'Save rule set' : 'Create rule set'}</button>{editing && <button type="button" onClick={() => { setEditing(null); setForm(emptySet); }}>Cancel</button>}</div>
    </form></section>
    <section className="card"><h2>Your rule sets</h2>{sets.map(set => <div className="list-row" key={set.id}><div><b>{set.name}</b><span className="muted">{set.ruleIds.length} rules · {set.enabled ? 'Enabled' : 'Disabled'}</span></div><div className="inline"><button onClick={() => { setEditing(set.id); setForm({ name: set.name, description: set.description || '', ruleIds: set.ruleIds, enabled: set.enabled }); window.scrollTo(0, 0); }}>Edit</button><button onClick={() => remove(set.id)}>Delete</button></div></div>)}</section>
  </div>;
}

function App() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState('new');
  const [rules, setRules] = useState([]);
  const [sets, setSets] = useState([]);
  const [reviews, setReviews] = useState([]);
  const [reviewPage, setReviewPage] = useState({ total: 0, page: 0, size: 50 });
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState('');
  const reload = useCallback(async () => {
    try {
      const [nextRules, nextSets, nextReviews] = await Promise.all([api('/rules'), api('/rule-sets'), api('/reviews?page=0&size=50')]);
      setRules(nextRules); setSets(nextSets); setReviews(nextReviews.items); setReviewPage(nextReviews);
    } catch (e) { setError(e.message); }
  }, []);
  async function loadReviewPage(page) {
    try {
      const result = await api(`/reviews?${query({ page, size: 50 })}`);
      setReviews(result.items); setReviewPage(result);
    } catch (e) { setError(e.message); }
  }
  useEffect(() => { api('/auth/me').then(me => { setUser(me); return reload(); }).catch(() => {}).finally(() => setLoading(false)); }, [reload]);
  const refreshSelected = useCallback(async () => {
    if (!selected) return;
    try { setSelected(await api(`/reviews/${selected.id}`)); }
    catch (e) { setError(e.message); }
  }, [selected?.id]);
  async function logout() {
    try { await api('/auth/logout', { method: 'POST' }); setUser(null); setSelected(null); }
    catch (e) { setError(e.message); }
  }
  if (loading) return <main className="loading">Loading…</main>;
  if (!user) return <Auth onLogin={async me => { setUser(me); await reload(); }} />;
  return <div className="app-shell">
    <aside className="sidebar"><div className="brand"><span className="brand-mark">{'{ }'}</span><div>Code Review<br /><strong>Agent</strong></div></div>
      <nav aria-label="Main navigation">{[['new', 'New review'], ['reviews', 'Reviews'], ['rules', 'Rules'], ['sets', 'Rule sets']].map(([id, label]) => <button key={id} className={tab === id ? 'active' : ''} onClick={() => { setTab(id); setSelected(null); if (id === 'reviews') reload(); }}>{label}</button>)}</nav>
      <div className="account"><span>{user.username}</span><button onClick={logout}>Sign out</button></div>
    </aside>
    <main className="content"><header className="page-header"><div><h1>{selected ? 'Review details' : { new: 'New review', reviews: 'Review history', rules: 'Coding rules', sets: 'Rule sets' }[tab]}</h1><p>Inspect source code with rules you control.</p></div></header>
      <ErrorNotice message={error} clear={() => setError('')} />
      {selected ? <ReviewDetail review={selected} rules={rules} onRefresh={refreshSelected} onBack={() => { setSelected(null); setTab('reviews'); reload(); }} /> : <>
        {tab === 'new' && <ReviewForm sets={sets} onCreated={async review => { setSelected(review); setTab('reviews'); await reload(); }} />}
        {tab === 'reviews' && <section className="card"><h2>Recent reviews</h2>{reviews.length === 0 && <p className="empty">No reviews yet. Start one from a public repository or paste code.</p>}{reviews.map(review => <button className="review-row" key={review.id} onClick={() => setSelected(review)}><div><b>{review.repositoryUrl || review.fileName}</b><span className="muted">{review.ruleSetName} · {new Date(review.createdAt).toLocaleString()}{review.commitSha ? ` · ${review.commitSha.slice(0, 12)}` : ''}</span></div><span className={`status ${review.status.toLowerCase()}`}>{review.status.replaceAll('_', ' ')}</span></button>)}{reviewPage.total > 50 && <div className="inline pagination"><button disabled={reviewPage.page === 0} onClick={() => loadReviewPage(reviewPage.page - 1)}>Previous</button><span>Page {reviewPage.page + 1}</span><button disabled={(reviewPage.page + 1) * 50 >= reviewPage.total} onClick={() => loadReviewPage(reviewPage.page + 1)}>Next</button></div>}</section>}
        {tab === 'rules' && <RuleManager rules={rules} reload={reload} />}
        {tab === 'sets' && <SetManager sets={sets} rules={rules} reload={reload} />}
      </>}
    </main>
  </div>;
}

export default App;
