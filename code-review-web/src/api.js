const BASE = '/api/v1';

// Spring Security issues the CSRF cookie through the same-origin session before mutations.
async function csrfToken() {
  const response = await fetch(`${BASE}/auth/csrf`, { credentials: 'same-origin' });
  if (!response.ok) throw new Error('Could not initialize the session');
  return (await response.json()).token;
}

export async function api(path, { method = 'GET', body } = {}) {
  const headers = {};
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = await csrfToken();
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(`${BASE}${path}`, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (response.status === 204) return null;
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload.message || `Request failed (${response.status})`);
  return payload;
}

export function query(params) {
  // Omit empty filters so the server can distinguish an unset filter from a real value.
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, value);
  });
  return search.toString();
}
