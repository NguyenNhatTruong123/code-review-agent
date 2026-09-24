import { useState } from 'react';
import { api } from '../../api.js';
import ErrorNotice from '../../components/ErrorNotice.jsx';

export default function Auth({ onLogin }) {
  const [mode, setMode] = useState('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      // Registration intentionally continues into login so the new account gets a session immediately.
      if (mode === 'register')
        await api('/auth/register', { method: 'POST', body: { username, password } });
      await api('/auth/login', { method: 'POST', body: { username, password } });
      onLogin(await api('/auth/me'));
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-shell">
      <div className="brand-mark">{'{ }'}</div>
      <h1>Code Review Agent</h1>
      <p>Review public GitHub repositories or pasted code against your rules.</p>
      <form className="card auth-card" onSubmit={submit}>
        <h2>{mode === 'login' ? 'Sign in' : 'Create account'}</h2>
        <ErrorNotice message={error} clear={() => setError('')} />
        <label>
          Username
          <input
            value={username}
            onChange={e => setUsername(e.target.value)}
            autoComplete="username"
            required
            minLength={3}
            maxLength={80}
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            required
            minLength={12}
          />
        </label>
        <button disabled={busy} className="primary">
          {busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
        </button>
        <button
          type="button"
          className="text-button"
          onClick={() => {
            setMode(mode === 'login' ? 'register' : 'login');
            setError('');
          }}
        >
          {mode === 'login' ? 'Need an account? Register' : 'Already have an account? Sign in'}
        </button>
      </form>
    </main>
  );
}
