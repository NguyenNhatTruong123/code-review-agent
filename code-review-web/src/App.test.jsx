import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from './App.jsx';

const starterSet = { id: 'set-1', name: 'Starter rules', ruleIds: ['rule-1'], enabled: true };
const starterRule = { id: 'rule-1', name: 'TODO marker', severity: 'INFO', languages: 'ALL', instruction: 'Flag TODO', matchText: 'TODO', suggestedFix: 'Fix it', enabled: true, version: 1 };
const review = { id: 'review-1', inputType: 'PASTE', fileName: 'X.java', ruleSetName: 'Starter rules', status: 'COMPLETED', createdAt: '2026-01-01T00:00:00Z', scannedFiles: 1, skippedFiles: 0, summary: { INFO: 1 } };

function response(data, status = 200) { return { ok: status < 400, status, json: async () => data }; }
function mockApi(custom = {}) {
  const calls = [];
  const fetchMock = vi.fn(async (url, options = {}) => {
    calls.push([url, options]);
    if (url.endsWith('/auth/csrf')) return response({ token: 'csrf' });
    if (custom[url]) return custom[url](options);
    if (url.endsWith('/auth/me')) return response({ username: 'alice' });
    if (url.endsWith('/rules')) return response([starterRule]);
    if (url.endsWith('/rule-sets')) return response([starterSet]);
    if (url.endsWith('/reviews?page=0&size=50')) return response({ items: [review], total: 1, page: 0, size: 50 });
    if (url.includes('/findings')) return response({ items: [], total: 0, page: 0, size: 50 });
    if (url.endsWith('/reviews/repository') || url.endsWith('/reviews/paste')) return response(review, 202);
    return response({}, 200);
  });
  vi.stubGlobal('fetch', fetchMock);
  return calls;
}

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

it('shows sign-in when there is no session and registers before logging in', async () => {
  let authenticated = false;
  const calls = mockApi({
    '/api/v1/auth/me': () => authenticated ? response({ username: 'alice' }) : response({ message: 'Unauthorized' }, 401),
    '/api/v1/auth/register': () => response({ username: 'alice' }, 201),
    '/api/v1/auth/login': () => { authenticated = true; return response({ username: 'alice' }); },
  });
  render(<App />);
  await screen.findByRole('heading', { name: 'Sign in' });
  fireEvent.click(screen.getByRole('button', { name: /Need an account/ }));
  fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'alice' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'long-password' } });
  fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
  await screen.findByRole('heading', { level: 1, name: 'New review' });
  expect(calls.some(([url]) => url.endsWith('/auth/register'))).toBe(true);
  expect(calls.some(([url]) => url.endsWith('/auth/login'))).toBe(true);
});

it('inspects a GitHub repository and submits a review', async () => {
  const calls = mockApi({
    '/api/v1/github/inspect?url=https%3A%2F%2Fgithub.com%2Facme%2Fsample': () => response({ repositoryUrl: 'https://github.com/acme/sample', defaultBranch: 'main', branches: ['main'] }),
  });
  render(<App />);
  await screen.findByRole('heading', { level: 1, name: 'New review' });
  fireEvent.change(screen.getByLabelText('Public repository URL'), { target: { value: 'https://github.com/acme/sample' } });
  fireEvent.click(screen.getByRole('button', { name: 'Inspect repository' }));
  await screen.findByText('Default branch: main');
  fireEvent.change(screen.getByLabelText('Rule set'), { target: { value: 'set-1' } });
  fireEvent.click(screen.getByRole('button', { name: 'Start review' }));
  await screen.findByRole('heading', { name: 'Review details' });
  expect(calls.some(([url]) => url.endsWith('/reviews/repository'))).toBe(true);
});

it('submits pasted code and opens a saved finding', async () => {
  const finding = { id: 'finding-1', ruleId: 'rule-1', ruleVersion: 1, severity: 'INFO', title: 'TODO marker', explanation: 'Unfinished', evidence: 'TODO', suggestedFix: 'Fix it', filePath: 'X.java', lineStart: 1, lineEnd: 1, source: 'STATIC' };
  const calls = mockApi({ '/api/v1/reviews/review-1/findings?page=0&size=50': () => response({ items: [finding], total: 1, page: 0, size: 50 }) });
  render(<App />);
  await screen.findByRole('heading', { level: 1, name: 'New review' });
  fireEvent.click(screen.getByRole('button', { name: 'Paste code' }));
  fireEvent.change(screen.getByLabelText('Code'), { target: { value: '// TODO' } });
  fireEvent.change(screen.getByLabelText('File name (optional)'), { target: { value: 'X.java' } });
  fireEvent.change(screen.getByLabelText('Rule set'), { target: { value: 'set-1' } });
  fireEvent.click(screen.getByRole('button', { name: 'Start review' }));
  await screen.findByText('Unfinished');
  expect(calls.some(([url]) => url.endsWith('/reviews/paste'))).toBe(true);
});

it('loads review history and opens details', async () => {
  mockApi();
  render(<App />);
  await screen.findByRole('heading', { level: 1, name: 'New review' });
  fireEvent.click(screen.getByRole('button', { name: 'Reviews' }));
  await screen.findByText('Recent reviews');
  fireEvent.click(screen.getByRole('button', { name: /X.java/ }));
  await screen.findByRole('heading', { name: 'Review details' });
});

it('creates a literal rule and a rule set', async () => {
  const calls = mockApi();
  render(<App />);
  await screen.findByRole('heading', { level: 1, name: 'New review' });
  fireEvent.click(screen.getByRole('button', { name: 'Rules' }));
  fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'No debug' } });
  fireEvent.change(screen.getByLabelText('Instruction'), { target: { value: 'Flag debug output' } });
  fireEvent.change(screen.getByLabelText(/Literal match/), { target: { value: 'console.log(' } });
  fireEvent.click(screen.getByRole('button', { name: 'Create rule' }));
  await waitFor(() => expect(calls.some(([url, options]) => url.endsWith('/rules') && options.method === 'POST')).toBe(true));
  fireEvent.click(screen.getByRole('button', { name: 'Rule sets' }));
  fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Custom set' } });
  fireEvent.click(screen.getByLabelText(/TODO marker/));
  fireEvent.click(screen.getByRole('button', { name: 'Create rule set' }));
  await waitFor(() => expect(calls.some(([url, options]) => url.endsWith('/rule-sets') && options.method === 'POST')).toBe(true));
});

it('edits and deletes an existing rule', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  const calls = mockApi({
    '/api/v1/rules/rule-1': options => response(options.method === 'DELETE' ? null : starterRule, options.method === 'DELETE' ? 204 : 200),
  });
  render(<App />);
  await screen.findByRole('heading', { level: 1, name: 'New review' });
  fireEvent.click(screen.getByRole('button', { name: 'Rules' }));
  fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
  fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Updated rule' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save rule' }));
  await waitFor(() => expect(calls.some(([url, options]) => url.endsWith('/rules/rule-1') && options.method === 'PUT')).toBe(true));
  fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
  await waitFor(() => expect(calls.some(([url, options]) => url.endsWith('/rules/rule-1') && options.method === 'DELETE')).toBe(true));
  vi.restoreAllMocks();
});

it('edits and deletes an existing rule set', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  const calls = mockApi({
    '/api/v1/rule-sets/set-1': options => response(options.method === 'DELETE' ? null : starterSet, options.method === 'DELETE' ? 204 : 200),
  });
  render(<App />);
  await screen.findByRole('heading', { level: 1, name: 'New review' });
  fireEvent.click(screen.getByRole('button', { name: 'Rule sets' }));
  fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
  fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Updated set' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save rule set' }));
  await waitFor(() => expect(calls.some(([url, options]) => url.endsWith('/rule-sets/set-1') && options.method === 'PUT')).toBe(true));
  fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
  await waitFor(() => expect(calls.some(([url, options]) => url.endsWith('/rule-sets/set-1') && options.method === 'DELETE')).toBe(true));
  vi.restoreAllMocks();
});

it('records finding feedback and filters findings', async () => {
  const finding = { id: 'finding-1', ruleId: 'rule-1', ruleVersion: 1, severity: 'INFO', title: 'TODO marker', explanation: 'Unfinished', evidence: 'TODO', suggestedFix: 'Fix it', filePath: 'X.java', lineStart: 1, lineEnd: 1, source: 'STATIC' };
  const calls = mockApi({
    '/api/v1/reviews/review-1/findings?page=0&size=50': () => response({ items: [finding], total: 1, page: 0, size: 50 }),
    '/api/v1/reviews/review-1/findings?severity=INFO&page=0&size=50': () => response({ items: [finding], total: 1, page: 0, size: 50 }),
    '/api/v1/reviews/review-1/findings/finding-1/feedback': () => response({ ...finding, feedback: 'HELPFUL' }),
  });
  render(<App />);
  await screen.findByRole('heading', { level: 1, name: 'New review' });
  fireEvent.click(screen.getByRole('button', { name: 'Reviews' }));
  fireEvent.click(screen.getByRole('button', { name: /X.java/ }));
  await screen.findByText('Unfinished');
  fireEvent.change(screen.getByLabelText('Feedback'), { target: { value: 'HELPFUL' } });
  await waitFor(() => expect(calls.some(([url]) => url.endsWith('/feedback'))).toBe(true));
  fireEvent.change(screen.getByLabelText('Severity'), { target: { value: 'INFO' } });
  await waitFor(() => expect(calls.some(([url]) => url.includes('severity=INFO'))).toBe(true));
});
