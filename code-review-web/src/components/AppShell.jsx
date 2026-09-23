import { NAVIGATION_ITEMS } from '../constants.js';

export default function AppShell({ user, tab, onTabChange, onLogout, title, children }) {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark">{'{ }'}</span><div>Code Review<br /><strong>Agent</strong></div></div>
        <nav aria-label="Main navigation">
          {NAVIGATION_ITEMS.map(([id, label]) => (
            <button key={id} className={tab === id ? 'active' : ''} onClick={() => onTabChange(id)}>{label}</button>
          ))}
        </nav>
        <div className="account"><span>{user.username}</span><button onClick={onLogout}>Sign out</button></div>
      </aside>
      <main className="content">
        <header className="page-header"><div><h1>{title}</h1><p>Inspect source code with rules you control.</p></div></header>
        {children}
      </main>
    </div>
  );
}