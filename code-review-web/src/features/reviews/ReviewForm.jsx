import { useRef, useState } from 'react';
import { api, query } from '../../api.js';
import ErrorNotice from '../../components/ErrorNotice.jsx';
import { SOURCE_LANGUAGES } from '../../constants.js';

export default function ReviewForm({ sets, onCreated }) {
  const [source, setSource] = useState('repository');
  const [repositoryUrl, setRepositoryUrl] = useState('');
  const [refChoice, setRefChoice] = useState('');
  const [manualRef, setManualRef] = useState('');
  const [repoInfo, setRepoInfo] = useState(null);
  const [inspectStatus, setInspectStatus] = useState('idle');
  const inspectRequest = useRef(0);
  const [code, setCode] = useState('');
  const [fileName, setFileName] = useState('');
  const [language, setLanguage] = useState('');
  const [ruleSetId, setRuleSetId] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const enabledSets = sets.filter(set => set.enabled);
  const branches = [...new Set(repoInfo?.branches ?? [])].filter(
    branch => branch && branch !== repoInfo?.defaultBranch
  );
  const selectedBranch = refChoice.startsWith('branch:') ? refChoice.slice('branch:'.length) : null;
  const inspecting = inspectStatus === 'loading';

  function changeRepositoryUrl(value) {
    // Invalidate an in-flight inspection so it cannot show branches from the previous URL.
    inspectRequest.current += 1;
    setRepositoryUrl(value);
    setRepoInfo(null);
    setRefChoice('');
    setManualRef('');
    setInspectStatus('idle');
    setError('');
  }

  async function inspect() {
    const requestId = ++inspectRequest.current;
    setError('');
    setRepoInfo(null);
    setRefChoice(current => (current.startsWith('branch:') ? '' : current));
    setInspectStatus('loading');

    try {
      const result = await api(`/github/inspect?${query({ url: repositoryUrl })}`);
      if (requestId !== inspectRequest.current) return;

      setRepoInfo(result);
      setInspectStatus('success');
    } catch (e) {
      if (requestId !== inspectRequest.current) return;

      setError(e.message);
      setInspectStatus('error');
    }
  }

  async function submit(event) {
    event.preventDefault();
    setError('');
    setBusy(true);

    try {
      const ref = refChoice === 'manual' ? manualRef.trim() : selectedBranch || '';
      const result =
        source === 'repository'
          ? await api('/reviews/repository', {
              method: 'POST',
              body: { repositoryUrl, ref, ruleSetId },
            })
          : await api('/reviews/paste', {
              method: 'POST',
              body: { code, fileName, language, ruleSetId },
            });
      onCreated(result);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card">
      <div className="section-heading">
        <div>
          <h2>New review</h2>
          <p>Select source code and the rules to apply.</p>
        </div>
      </div>
      <div className="segmented" role="group" aria-label="Source type">
        <button
          type="button"
          className={source === 'repository' ? 'selected' : ''}
          onClick={() => setSource('repository')}
        >
          GitHub repository
        </button>
        <button
          type="button"
          className={source === 'paste' ? 'selected' : ''}
          onClick={() => setSource('paste')}
        >
          Paste code
        </button>
      </div>
      <ErrorNotice message={error} clear={() => setError('')} />
      <form onSubmit={submit} className="form-stack">
        <p className="required-note">
          <span className="required-mark" aria-hidden="true">
            *
          </span>{' '}
          Fields marked with an asterisk are required.
        </p>
        {source === 'repository' ? (
          <>
            <label>
              <span>
                Public repository URL{' '}
                <span className="required-mark" aria-hidden="true">
                  *
                </span>
              </span>
              <input
                type="url"
                placeholder="https://github.com/owner/repo"
                value={repositoryUrl}
                onChange={e => changeRepositoryUrl(e.target.value)}
                required
              />
            </label>
            <div className="inline">
              <button
                type="button"
                onClick={inspect}
                disabled={busy || inspecting || !repositoryUrl}
              >
                {inspecting ? 'Inspecting…' : 'Inspect repository'}
              </button>
              {repoInfo && (
                <span className="muted ref-name">Default branch: {repoInfo.defaultBranch}</span>
              )}
            </div>
            <div className="ref-field">
              <label htmlFor="review-ref">
                <span>
                  Branch, tag, or commit <span className="optional">(optional)</span>
                </span>
              </label>
              <select
                id="review-ref"
                className="ref-select"
                value={refChoice}
                onChange={e => setRefChoice(e.target.value)}
                aria-describedby="review-ref-help"
              >
                <option value="">
                  Use default branch{repoInfo?.defaultBranch ? ` (${repoInfo.defaultBranch})` : ''}
                </option>
                {branches.map(branch => (
                  <option key={branch} value={`branch:${branch}`}>
                    {branch}
                  </option>
                ))}
                <option value="manual">Enter another branch, tag, or commit…</option>
              </select>
              {refChoice === 'manual' && (
                <label>
                  <span>
                    Custom branch, tag, or commit{' '}
                    <span className="required-mark" aria-hidden="true">
                      *
                    </span>
                  </span>
                  <input
                    value={manualRef}
                    onChange={e => setManualRef(e.target.value)}
                    maxLength={200}
                    pattern="[A-Za-z0-9._/-]+"
                    title="Use letters, numbers, periods, underscores, hyphens, or slashes"
                    required
                    placeholder="Branch, tag, or 40-character commit SHA"
                  />
                </label>
              )}
              <p id="review-ref-help" className="hint ref-help" role="status">
                {inspecting && 'Loading branches…'}
                {inspectStatus === 'idle' &&
                  'Inspect the repository to list branches, or enter a ref manually.'}
                {inspectStatus === 'error' &&
                  'Repository inspection failed. Correct the URL and retry, or enter a ref manually.'}
                {inspectStatus === 'success' &&
                  branches.length === 0 &&
                  'No additional branches were returned. You can use the default branch or enter a ref manually.'}
                {inspectStatus === 'success' && branches.length > 0 && selectedBranch && (
                  <>
                    Selected branch: <span className="ref-name">{selectedBranch}</span>
                  </>
                )}
                {inspectStatus === 'success' &&
                  branches.length > 0 &&
                  refChoice === 'manual' &&
                  'Enter the branch, tag, or commit to review.'}
                {inspectStatus === 'success' &&
                  branches.length > 0 &&
                  refChoice === '' &&
                  'Choose a branch above, use the default branch, or enter another ref.'}
              </p>
            </div>
          </>
        ) : (
          <>
            <label>
              <span>
                Code{' '}
                <span className="required-mark" aria-hidden="true">
                  *
                </span>
              </span>
              <textarea
                className="code-input"
                value={code}
                onChange={e => setCode(e.target.value)}
                required
                maxLength={100000}
                rows={12}
                placeholder="Paste source code here"
              />
            </label>
            <div className="grid-two">
              <label>
                <span>
                  File name
                  {!language && (
                    <span className="required-mark" aria-hidden="true">
                      {' '}*
                    </span>
                  )}{' '}
                  <span className="optional">(required when no language is selected)</span>
                </span>
                <input
                  value={fileName}
                  onChange={e => setFileName(e.target.value)}
                  placeholder="Example.java"
                  aria-required={!language}
                />
              </label>
              <label>
                <span>
                  Language
                  {!fileName && (
                    <span className="required-mark" aria-hidden="true">
                      {' '}*
                    </span>
                  )}{' '}
                  <span className="optional">(required when no file name is provided)</span>
                </span>
                <select
                  value={language}
                  onChange={e => setLanguage(e.target.value)}
                  aria-required={!fileName}
                >
                  <option value="">Detect from file name</option>
                  {SOURCE_LANGUAGES.map(item => (
                    <option key={item}>{item}</option>
                  ))}
                </select>
              </label>
            </div>
          </>
        )}
        <label>
          <span>
            Rule set{' '}
            <span className="required-mark" aria-hidden="true">
              *
            </span>
          </span>
          <select value={ruleSetId} onChange={e => setRuleSetId(e.target.value)} required>
            <option value="">Select a rule set</option>
            {enabledSets.map(set => (
              <option key={set.id} value={set.id}>
                {set.name}
              </option>
            ))}
          </select>
        </label>
        {enabledSets.length === 0 && (
          <p className="muted">Create and enable a rule set before starting a review.</p>
        )}
        <p className="hint">
          Code is processed by the server. Rules without a literal match use the configured AI
          provider.
        </p>
        <button className="primary" disabled={busy || inspecting || !ruleSetId}>
          {busy ? 'Submitting…' : 'Start review'}
        </button>
      </form>
    </section>
  );
}
