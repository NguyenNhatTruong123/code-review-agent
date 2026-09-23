import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, query } from './api.js';

afterEach(() => vi.unstubAllGlobals());

describe('API client', () => {
  it('encodes query values', () => {
    expect(query({ url: 'https://github.com/a/b', empty: '', page: 2 })).toBe('url=https%3A%2F%2Fgithub.com%2Fa%2Fb&page=2');
  });

  it('sends JSON mutations with a CSRF token', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ token: 'csrf-value' }) })
      .mockResolvedValueOnce({ ok: true, status: 201, json: async () => ({ id: 'rule' }) });
    vi.stubGlobal('fetch', fetchMock);
    expect(await api('/rules', { method: 'POST', body: { name: 'Rule' } })).toEqual({ id: 'rule' });
    expect(fetchMock.mock.calls[1][1].headers['X-XSRF-TOKEN']).toBe('csrf-value');
    expect(fetchMock.mock.calls[1][1].body).toBe('{"name":"Rule"}');
  });

  it('returns null for 204 and reports server errors', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ token: 'csrf' }) })
      .mockResolvedValueOnce({ ok: true, status: 204 })
      .mockResolvedValueOnce({ ok: false, status: 400, json: async () => ({ message: 'Bad URL' }) }));
    expect(await api('/rules/id', { method: 'DELETE' })).toBeNull();
    await expect(api('/github/inspect')).rejects.toThrow('Bad URL');
  });
});
