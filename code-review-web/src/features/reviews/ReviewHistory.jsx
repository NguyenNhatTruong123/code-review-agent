import { useState } from 'react';
import { api } from '../../api.js';
import ErrorNotice from '../../components/ErrorNotice.jsx';
import { PAGE_SIZE, REVIEW_STATUSES } from '../../constants.js';

function sourceName(review) {
  return review.repositoryUrl || review.fileName || 'Review source';
}

function reviewMetadata(review, prefix) {
  return `${prefix} · ${review.ruleSetName} · ${new Date(review.createdAt).toLocaleString()}${review.commitSha ? ` · ${review.commitSha.slice(0, 12)}` : ''}`;
}

function DeleteAction({ review, onDelete }) {
  const isActive = REVIEW_STATUSES.includes(review.status);

  return <>
    {isActive && <span className="muted review-action-note">Cancel before deleting</span>}
    <button type="button" className="danger" disabled={isActive} title={isActive ? 'Cancel the review before deleting it' : 'Delete review'} onClick={() => onDelete(review)}>Delete</button>
  </>;
}

export default function ReviewHistory({ reviews, reviewPage, loadReviewPage, onDelete, onSelect }) {
  const [expandedId, setExpandedId] = useState(null);
  const [rerunsByOriginalId, setRerunsByOriginalId] = useState({});
  const [loadingRerunsId, setLoadingRerunsId] = useState(null);
  const [error, setError] = useState('');

  async function toggleReruns(review) {
    if (expandedId === review.id) {
      setExpandedId(null);
      return;
    }

    setExpandedId(review.id);
    setError('');
    setLoadingRerunsId(review.id);

    try {
      const reruns = await api(`/reviews/${review.id}/reruns`);
      setRerunsByOriginalId(current => ({ ...current, [review.id]: reruns }));
    } catch (e) {
      setError(e.message);
    } finally {
      setLoadingRerunsId(null);
    }
  }

  async function deleteRerun(review, originalId) {
    if (!await onDelete(review)) return;

    try {
      const reruns = await api(`/reviews/${originalId}/reruns`);
      setRerunsByOriginalId(current => ({ ...current, [originalId]: reruns }));
    } catch (e) {
      setError(e.message);
    }
  }

  return <section className="card">
    <h2>Recent reviews</h2>
    <ErrorNotice message={error} clear={() => setError('')} />
    {reviews.length === 0 && <p className="empty">No reviews yet. Start one from a public repository or paste code.</p>}
    {reviews.map(review => {
      const isExpanded = expandedId === review.id;
      const reruns = rerunsByOriginalId[review.id] || [];
      const hasReruns = review.rerunCount > 0;

      return <div className="review-group" key={review.id}>
        <div className="review-row">
          <button type="button" className="review-open" onClick={() => hasReruns ? toggleReruns(review) : onSelect(review)} aria-expanded={hasReruns ? isExpanded : undefined} aria-controls={hasReruns ? `reruns-${review.id}` : undefined}>
            <div><b>{sourceName(review)}</b><span className="muted">{reviewMetadata(review, 'Original review')}</span></div>
            <span className={`status ${review.status.toLowerCase()}`}>{review.status.replaceAll('_', ' ')}</span>
          </button>
          <div className="review-actions">
            {hasReruns && <span className="rerun-count">{loadingRerunsId === review.id ? 'Loading reruns…' : `${review.rerunCount} rerun${review.rerunCount === 1 ? '' : 's'} ${isExpanded ? '▴' : '▾'}`}</span>}
            <button type="button" className="text-button review-view" onClick={() => onSelect(review)}>View details</button>
            <DeleteAction review={review} onDelete={onDelete} />
          </div>
        </div>
        {isExpanded && <div className="rerun-list" id={`reruns-${review.id}`}>
          <p className="rerun-list-title">Reruns for this review</p>
          {loadingRerunsId === review.id && <p className="muted">Loading reruns…</p>}
          {!loadingRerunsId && reruns.map((rerun, index) => <div className="review-row rerun-row" key={rerun.id}>
            <button type="button" className="review-open" onClick={() => onSelect(rerun)}>
              <div><b>Rerun {index + 1} · {sourceName(rerun)}</b><span className="muted">{reviewMetadata(rerun, `Rerun ${index + 1}`)}</span></div>
              <span className={`status ${rerun.status.toLowerCase()}`}>{rerun.status.replaceAll('_', ' ')}</span>
            </button>
            <div className="review-actions"><DeleteAction review={rerun} onDelete={() => deleteRerun(rerun, review.id)} /></div>
          </div>)}
          {!loadingRerunsId && reruns.length === 0 && <p className="muted">No reruns are available.</p>}
        </div>}
      </div>;
    })}
    {reviewPage.total > PAGE_SIZE && <div className="inline pagination"><button disabled={reviewPage.page === 0} onClick={() => loadReviewPage(reviewPage.page - 1)}>Previous</button><span>Page {reviewPage.page + 1}</span><button disabled={(reviewPage.page + 1) * PAGE_SIZE >= reviewPage.total} onClick={() => loadReviewPage(reviewPage.page + 1)}>Next</button></div>}
  </section>;
}
