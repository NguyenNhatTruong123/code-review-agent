import { useCallback, useEffect, useState } from 'react';
import { api, query } from './api.js';
import AppShell from './components/AppShell.jsx';
import ErrorNotice from './components/ErrorNotice.jsx';
import { PAGE_SIZE } from './constants.js';
import Auth from './features/auth/Auth.jsx';
import ReviewDetail from './features/reviews/ReviewDetail.jsx';
import ReviewForm from './features/reviews/ReviewForm.jsx';
import ReviewHistory from './features/reviews/ReviewHistory.jsx';
import RuleManager from './features/rules/RuleManager.jsx';
import SetManager from './features/rules/SetManager.jsx';

const PAGE_TITLES = {
  new: 'New review',
  reviews: 'Review history',
  rules: 'Coding rules',
  sets: 'Rule sets',
};

export default function App() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState('new');
  const [rules, setRules] = useState([]);
  const [sets, setSets] = useState([]);
  const [reviews, setReviews] = useState([]);
  const [reviewPage, setReviewPage] = useState({ total: 0, page: 0, size: PAGE_SIZE });
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState('');

  // Keep all shared list data in one refresh operation after mutations or navigation.
  const reload = useCallback(async () => {
    try {
      const [nextRules, nextSets, nextReviews] = await Promise.all([
        api('/rules'),
        api('/rule-sets'),
        api(`/reviews?page=0&size=${PAGE_SIZE}`),
      ]);
      setRules(nextRules);
      setSets(nextSets);
      setReviews(nextReviews.items);
      setReviewPage(nextReviews);
    } catch (e) {
      setError(e.message);
    }
  }, []);

  async function loadReviewPage(page) {
    try {
      const result = await api(`/reviews?${query({ page, size: PAGE_SIZE })}`);
      setReviews(result.items);
      setReviewPage(result);
    } catch (e) {
      setError(e.message);
    }
  }

  async function deleteReview(review) {
    const reviewName = review.repositoryUrl || review.fileName || review.id;
    const confirmation = `Delete review for ${reviewName}? Its findings and feedback will also be permanently removed.`;
    if (!window.confirm(confirmation)) return false;

    try {
      await api(`/reviews/${review.id}`, { method: 'DELETE' });
      const nextPage =
        reviews.length === 1 && reviewPage.page > 0 ? reviewPage.page - 1 : reviewPage.page;
      setSelected(current => (current?.id === review.id ? null : current));
      setTab('reviews');
      await loadReviewPage(nextPage);
      return true;
    } catch (e) {
      setError(e.message);
      return false;
    }
  }

  // Probe the existing session once; authenticated data is loaded through the same shared refresh.
  useEffect(() => {
    api('/auth/me')
      .then(me => {
        setUser(me);
        return reload();
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [reload]);

  const refreshSelected = useCallback(async () => {
    if (!selected) return;
    try {
      setSelected(await api(`/reviews/${selected.id}`));
    } catch (e) {
      setError(e.message);
    }
  }, [selected?.id]);

  async function logout() {
    try {
      await api('/auth/logout', { method: 'POST' });
      setUser(null);
      setSelected(null);
    } catch (e) {
      setError(e.message);
    }
  }

  function changeTab(nextTab) {
    setTab(nextTab);
    setSelected(null);
    if (nextTab === 'reviews') reload();
  }

  if (loading) return <main className="loading">Loading…</main>;
  if (!user)
    return (
      <Auth
        onLogin={async me => {
          setUser(me);
          await reload();
        }}
      />
    );

  const title = selected ? 'Review details' : PAGE_TITLES[tab];
  return (
    <AppShell user={user} tab={tab} onTabChange={changeTab} onLogout={logout} title={title}>
      <ErrorNotice message={error} clear={() => setError('')} />
      {selected ? (
        <ReviewDetail
          review={selected}
          rules={rules}
          sets={sets}
          onRefresh={refreshSelected}
          onDelete={deleteReview}
          onRerun={async review => {
            setSelected(review);
            setTab('reviews');
            await reload();
          }}
          onBack={() => {
            setSelected(null);
            setTab('reviews');
            reload();
          }}
        />
      ) : (
        <>
          {tab === 'new' && (
            <ReviewForm
              sets={sets}
              onCreated={async review => {
                setSelected(review);
                setTab('reviews');
                await reload();
              }}
            />
          )}
          {tab === 'reviews' && (
            <ReviewHistory
              reviews={reviews}
              reviewPage={reviewPage}
              loadReviewPage={loadReviewPage}
              onDelete={deleteReview}
              onSelect={setSelected}
            />
          )}
          {tab === 'rules' && <RuleManager rules={rules} reload={reload} />}
          {tab === 'sets' && <SetManager sets={sets} rules={rules} reload={reload} />}
        </>
      )}
    </AppShell>
  );
}
