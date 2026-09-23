import { useState } from 'react';
import { api } from '../../api.js';
import ErrorNotice from '../../components/ErrorNotice.jsx';
import { EMPTY_RULE, SEVERITIES } from '../../constants.js';

export default function RuleManager({ rules, reload }) {
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY_RULE);
  const [error, setError] = useState('');

  async function save(event) {
    event.preventDefault(); setError('');
    try { await api(`/rules${editing ? `/${editing}` : ''}`, { method: editing ? 'PUT' : 'POST', body: form }); setEditing(null); setForm(EMPTY_RULE); await reload(); }
    catch (e) { setError(e.message); }
  }

  async function remove(id) {
    if (!window.confirm('Delete this rule?')) return;
    try { await api(`/rules/${id}`, { method: 'DELETE' }); await reload(); }
    catch (e) { setError(e.message); }
  }

  return <div className="stack"><section className="card"><h2>{editing ? 'Edit rule' : 'Create rule'}</h2><p className="muted">A literal match runs without AI. Leave it empty for semantic AI review.</p>
    <ErrorNotice message={error} clear={() => setError('')} />
    <form onSubmit={save} className="form-stack"><div className="grid-two"><label>Name<input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} required maxLength={120} /></label><label>Category<input value={form.category || ''} onChange={e => setForm({ ...form, category: e.target.value })} /></label></div>
      <label>Description<input value={form.description || ''} onChange={e => setForm({ ...form, description: e.target.value })} /></label>
      <div className="grid-two"><label>Severity<select value={form.severity} onChange={e => setForm({ ...form, severity: e.target.value })}>{SEVERITIES.map(item => <option key={item}>{item}</option>)}</select></label><label>Languages <span className="optional">(comma separated or ALL)</span><input value={form.languages} onChange={e => setForm({ ...form, languages: e.target.value })} required /></label></div>
      <label>Instruction<textarea value={form.instruction} onChange={e => setForm({ ...form, instruction: e.target.value })} required rows={3} maxLength={4000} /></label>
      <label>Literal match <span className="optional">(optional, case sensitive)</span><input value={form.matchText || ''} onChange={e => setForm({ ...form, matchText: e.target.value })} maxLength={500} /></label>
      <label>Suggested fix<input value={form.suggestedFix || ''} onChange={e => setForm({ ...form, suggestedFix: e.target.value })} /></label>
      <label className="check"><input type="checkbox" checked={form.enabled} onChange={e => setForm({ ...form, enabled: e.target.checked })} /> Enabled</label>
      <div className="inline"><button className="primary">{editing ? 'Save rule' : 'Create rule'}</button>{editing && <button type="button" onClick={() => { setEditing(null); setForm(EMPTY_RULE); }}>Cancel</button>}</div>
    </form></section>
    <section className="card"><h2>Your rules</h2>{rules.length === 0 && <p className="empty">No rules yet.</p>}{rules.map(rule => <div className="list-row" key={rule.id}><div><b>{rule.name}</b><span className="muted">{rule.severity} · {rule.languages} · v{rule.version} · {rule.enabled ? 'Enabled' : 'Disabled'}</span></div><div className="inline"><button onClick={() => { setEditing(rule.id); setForm({ name: rule.name, description: rule.description || '', category: rule.category || '', severity: rule.severity, languages: rule.languages, instruction: rule.instruction, matchText: rule.matchText || '', suggestedFix: rule.suggestedFix || '', enabled: rule.enabled }); window.scrollTo(0, 0); }}>Edit</button><button onClick={() => remove(rule.id)}>Delete</button></div></div>)}</section>
  </div>;
}