import { useCallback, useEffect, useState } from 'react';
import { api, query } from '../../api.js';
import ErrorNotice from '../../components/ErrorNotice.jsx';
import { PAGE_SIZE, REVIEW_STATUSES, SEVERITIES } from '../../constants.js';

export default function ReviewDetail({
  review,
  rules,
  sets,
  onRefresh,
  onDelete,
  onRerun,
  onBack,
}) {
  const [findings, setFindings] = useState({ items: [], total: 0, page: 0, size: PAGE_SIZE });
  const [severity, setSeverity] = useState('');
  const [ruleId, setRuleId] = useState('');
  const [file, setFile] = useState('');
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const [rerunOpen, setRerunOpen] = useState(false);
  const [rerunMode, setRerunMode] = useState('ORIGINAL');
  const [rerunRuleSetId, setRerunRuleSetId] = useState('');
  const [rerunError, setRerunError] = useState('');
  const [rerunning, setRerunning] = useState(false);
  const enabledSets = sets.filter(set => set.enabled);

  const load = useCallback(async () => {
    // Filters are part of the request identity, so changing one resets the server query via page state.
    try {
      setFindings(
        await api(
          `/reviews/${review.id}/findings?${query({ severity, ruleId, file, page, size: PAGE_SIZE })}`
        )
      );
    } catch (e) {
      setError(e.message);
    }
  }, [review.id, severity, ruleId, file, page]);

  useEffect(() => {
    load();
  }, [load, review.status]);

  useEffect(() => {
    setRerunOpen(false);
    setRerunMode('ORIGINAL');
    setRerunRuleSetId('');
    setRerunError('');
  }, [review.id]);

  // Poll only while the server is processing the review, then release the timer.
  useEffect(() => {
    if (!REVIEW_STATUSES.includes(review.status)) return undefined;
    const timer = setInterval(onRefresh, 2500);
    return () => clearInterval(timer);
  }, [review.status, onRefresh]);

  async function feedback(findingId, value) {
    try {
      await api(`/reviews/${review.id}/findings/${findingId}/feedback`, {
        method: 'POST',
        body: { feedback: value },
      });
      await load();
    } catch (e) {
      setError(e.message);
    }
  }

  async function cancel() {
    try {
      await api(`/reviews/${review.id}/cancel`, { method: 'POST' });
      await onRefresh();
    } catch (e) {
      setError(e.message);
    }
  }

  async function rerun(event) {
    event.preventDefault();
    setRerunError('');
    setRerunning(true);

    try {
      const nextReview = await api(`/reviews/${review.id}/rerun`, {
        method: 'POST',
        body: { ruleMode: rerunMode, ruleSetId: rerunMode === 'CURRENT' ? rerunRuleSetId : null },
      });
      await onRerun(nextReview);
    } catch (e) {
      setRerunError(e.message);
    } finally {
      setRerunning(false);
    }
  }

  return (
    <div className="stack">
      <button type="button" className="back-button" onClick={onBack}>
        ← All reviews
      </button>
      <section className="card">
        <div className="section-heading">
          <div>
            <h2>{review.repositoryUrl || review.fileName || 'Review'}</h2>
            <p>Created {new Date(review.createdAt).toLocaleString()}</p>
          </div>
          <span className={`status ${review.status.toLowerCase()}`}>
            {review.status.replaceAll('_', ' ')}
          </span>
        </div>
        <div className="meta-grid">
          <div>
            <b>Rule set</b>
            <span>{review.ruleSetName}</span>
          </div>
          <div>
            <b>Scanned files</b>
            <span>{review.scannedFiles}</span>
          </div>
          <div>
            <b>Skipped files</b>
            <span>{review.skippedFiles}</span>
          </div>
          <div>
            <b>AI evaluated files</b>
            <span>{review.aiEvaluatedFiles || 0}</span>
          </div>
          {review.commitSha && (
            <div>
              <b>Commit</b>
              <code title={review.commitSha}>{review.commitSha.slice(0, 12)}</code>
            </div>
          )}
        </div>
        {review.warning && <div className="notice warning">{review.warning}</div>}
        {review.error && <div className="notice error">{review.error}</div>}
        {REVIEW_STATUSES.includes(review.status) && (
          <div className="inline">
            <span className="muted">Review in progress…</span>
            <button type="button" onClick={cancel}>
              Cancel
            </button>
          </div>
        )}
        <div className="inline">
          <button type="button" className="primary" onClick={() => setRerunOpen(open => !open)}>
            Rerun review
          </button>
          {!REVIEW_STATUSES.includes(review.status) && (
            <button type="button" className="danger" onClick={() => onDelete(review)}>
              Delete review
            </button>
          )}
        </div>
        {rerunOpen && (
          <div className="rerun-panel">
            <h3>Rerun review</h3>
            <p className="muted">
              A rerun creates a new review. This review and its findings stay unchanged.
            </p>
            <div className="rerun-source">
              <b>Source</b>
              {review.inputType === 'GITHUB' ? (
                <span>
                  <span className="ref-name">{review.repositoryUrl}</span>
                  {review.requestedRef && (
                    <>
                      {' '}
                      · requested ref <code>{review.requestedRef}</code>
                    </>
                  )}
                  {review.commitSha && (
                    <>
                      {' '}
                      · pinned commit <code title={review.commitSha}>{review.commitSha}</code>
                    </>
                  )}
                </span>
              ) : (
                <span>
                  {review.fileName || 'Pasted source file'}
                  {review.language && <> · {review.language}</>}
                </span>
              )}
            </div>
            <ErrorNotice message={rerunError} clear={() => setRerunError('')} />
            <form className="form-stack" onSubmit={rerun}>
              <fieldset>
                <legend>Rules for the new review</legend>
                <label className="check">
                  <input
                    type="radio"
                    name="rerun-rules"
                    value="ORIGINAL"
                    checked={rerunMode === 'ORIGINAL'}
                    onChange={() => setRerunMode('ORIGINAL')}
                  />{' '}
                  Use the original rule snapshot
                </label>
                <p className="hint">
                  Uses the exact rules saved with this review, even if they have changed or been
                  deleted.
                </p>
                <label className="check">
                  <input
                    type="radio"
                    name="rerun-rules"
                    value="CURRENT"
                    checked={rerunMode === 'CURRENT'}
                    onChange={() => setRerunMode('CURRENT')}
                  />{' '}
                  Choose an enabled current rule set
                </label>
                {rerunMode === 'CURRENT' && (
                  <label>
                    Rule set
                    <select
                      value={rerunRuleSetId}
                      onChange={e => setRerunRuleSetId(e.target.value)}
                      required
                    >
                      <option value="">Select a rule set</option>
                      {enabledSets.map(set => (
                        <option key={set.id} value={set.id}>
                          {set.name}
                        </option>
                      ))}
                    </select>
                  </label>
                )}
                {rerunMode === 'CURRENT' && enabledSets.length === 0 && (
                  <p className="muted">
                    No enabled rule sets are available. Create or enable a rule set first.
                  </p>
                )}
              </fieldset>
              <div className="inline">
                <button
                  className="primary"
                  disabled={rerunning || (rerunMode === 'CURRENT' && !rerunRuleSetId)}
                >
                  {rerunning ? 'Starting rerun…' : 'Start rerun'}
                </button>
                <button type="button" onClick={() => setRerunOpen(false)} disabled={rerunning}>
                  Cancel
                </button>
              </div>
            </form>
          </div>
        )}
        <div className="summary">
          {SEVERITIES.map(item => (
            <span key={item} className={`severity ${item.toLowerCase()}`}>
              {item}: {review.summary?.[item] || 0}
            </span>
          ))}
        </div>
      </section>
      <section className="card">
        <div className="section-heading">
          <div>
            <h2>Findings</h2>
            <p>{findings.total} matching findings</p>
          </div>
        </div>
        <ErrorNotice message={error} clear={() => setError('')} />
        <div className="filter-row">
          <label>
            Severity
            <select
              value={severity}
              onChange={e => {
                setSeverity(e.target.value);
                setPage(0);
              }}
            >
              <option value="">All</option>
              {SEVERITIES.map(item => (
                <option key={item}>{item}</option>
              ))}
            </select>
          </label>
          <label>
            Rule
            <select
              value={ruleId}
              onChange={e => {
                setRuleId(e.target.value);
                setPage(0);
              }}
            >
              <option value="">All</option>
              {rules.map(rule => (
                <option key={rule.id} value={rule.id}>
                  {rule.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            File
            <input
              value={file}
              onChange={e => {
                setFile(e.target.value);
                setPage(0);
              }}
              placeholder="Exact file path"
            />
          </label>
        </div>
        {findings.items.length === 0 ? (
          <p className="empty">No findings for these filters.</p>
        ) : (
          findings.items.map(finding => (
            <article className="finding" key={finding.id}>
              <div className="finding-head">
                <span className={`severity ${finding.severity.toLowerCase()}`}>
                  {finding.severity}
                </span>
                <h3>{finding.title}</h3>
                <span className="muted">{finding.source}</span>
              </div>
              <p className="location">
                {review.commitSha && finding.lineStart ? (
                  <a
                    href={`${review.repositoryUrl}/blob/${review.commitSha}/${finding.filePath.split('/').map(encodeURIComponent).join('/')}#L${finding.lineStart}${finding.lineEnd !== finding.lineStart ? `-L${finding.lineEnd}` : ''}`}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {finding.filePath}:{finding.lineStart}
                    {finding.lineEnd !== finding.lineStart ? `–${finding.lineEnd}` : ''} ↗
                  </a>
                ) : (
                  <>
                    {finding.filePath}
                    {finding.lineStart
                      ? `:${finding.lineStart}${finding.lineEnd !== finding.lineStart ? `–${finding.lineEnd}` : ''}`
                      : ''}
                  </>
                )}
              </p>
              <p>{finding.explanation}</p>
              <pre className="evidence">{finding.evidence}</pre>
              <p>
                <b>Suggested fix:</b> {finding.suggestedFix}
              </p>
              <div className="finding-foot">
                <span className="muted">
                  Rule: {rules.find(rule => rule.id === finding.ruleId)?.name || finding.ruleId} · v
                  {finding.ruleVersion}
                </span>
                <label>
                  Feedback
                  <select
                    value={finding.feedback || ''}
                    onChange={e => feedback(finding.id, e.target.value)}
                  >
                    <option value="">No feedback</option>
                    <option value="HELPFUL">Helpful</option>
                    <option value="IRRELEVANT">Irrelevant</option>
                    <option value="FALSE_POSITIVE">False positive</option>
                  </select>
                </label>
              </div>
            </article>
          ))
        )}
        {findings.total > PAGE_SIZE && (
          <div className="inline pagination">
            <button disabled={page === 0} onClick={() => setPage(page - 1)}>
              Previous
            </button>
            <span>Page {page + 1}</span>
            <button
              disabled={(page + 1) * PAGE_SIZE >= findings.total}
              onClick={() => setPage(page + 1)}
            >
              Next
            </button>
          </div>
        )}
      </section>
    </div>
  );
}
