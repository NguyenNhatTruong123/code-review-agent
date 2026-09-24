import { PAGE_SIZE, REVIEW_STATUSES } from '../../constants.js';

export default function ReviewHistory({ reviews, reviewPage, loadReviewPage, onDelete, onSelect }) {
  return <section className="card"><h2>Recent reviews</h2>{reviews.length === 0 && <p className="empty">No reviews yet. Start one from a public repository or paste code.</p>}{reviews.map(review => {
    const isActive = REVIEW_STATUSES.includes(review.status);
    return <div className="review-row" key={review.id}>
      <button type="button" className="review-open" onClick={() => onSelect(review)}>
        <div><b>{review.repositoryUrl || review.fileName}</b><span className="muted">{review.ruleSetName} · {new Date(review.createdAt).toLocaleString()}{review.commitSha ? ` · ${review.commitSha.slice(0, 12)}` : ''}</span></div>
        <span className={`status ${review.status.toLowerCase()}`}>{review.status.replaceAll('_', ' ')}</span>
      </button>
      <div className="review-actions">
        {isActive && <span className="muted review-action-note">Cancel before deleting</span>}
        <button type="button" className="danger" disabled={isActive} title={isActive ? 'Cancel the review before deleting it' : 'Delete review'} onClick={() => onDelete(review)}>Delete</button>
      </div>
    </div>;
  })}{reviewPage.total > PAGE_SIZE && <div className="inline pagination"><button disabled={reviewPage.page === 0} onClick={() => loadReviewPage(reviewPage.page - 1)}>Previous</button><span>Page {reviewPage.page + 1}</span><button disabled={(reviewPage.page + 1) * PAGE_SIZE >= reviewPage.total} onClick={() => loadReviewPage(reviewPage.page + 1)}>Next</button></div>}</section>;
}
