import { useState } from 'react';
import { api, query } from '../../api.js';
import ErrorNotice from '../../components/ErrorNotice.jsx';
import { SOURCE_LANGUAGES } from '../../constants.js';

export default function ReviewForm({ sets, onCreated }) {
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
          <label>Language<select value={language} onChange={e => setLanguage(e.target.value)}><option value="">Detect from file name</option>{SOURCE_LANGUAGES.map(item => <option key={item}>{item}</option>)}</select></label></div>
      </>}
      <label>Rule set<select value={ruleSetId} onChange={e => setRuleSetId(e.target.value)} required><option value="">Select a rule set</option>{enabledSets.map(set => <option key={set.id} value={set.id}>{set.name}</option>)}</select></label>
      {enabledSets.length === 0 && <p className="muted">Create and enable a rule set before starting a review.</p>}
      <p className="hint">Code is processed by the server. Rules without a literal match use the configured AI provider.</p>
      <button className="primary" disabled={busy || !ruleSetId}>{busy ? 'Submitting…' : 'Start review'}</button>
    </form>
  </section>;
}