import { useState } from 'react';
import { api } from '../../api.js';
import ErrorNotice from '../../components/ErrorNotice.jsx';
import { EMPTY_RULE_SET } from '../../constants.js';

export default function SetManager({ sets, rules, reload }) {
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY_RULE_SET);
  const [error, setError] = useState('');

  async function save(event) {
    event.preventDefault();
    setError('');
    try {
      await api(`/rule-sets${editing ? `/${editing}` : ''}`, {
        method: editing ? 'PUT' : 'POST',
        body: form,
      });
      setEditing(null);
      setForm(EMPTY_RULE_SET);
      await reload();
    } catch (e) {
      setError(e.message);
    }
  }

  async function remove(id) {
    if (!window.confirm('Delete this rule set?')) return;
    try {
      await api(`/rule-sets/${id}`, { method: 'DELETE' });
      await reload();
    } catch (e) {
      setError(e.message);
    }
  }

  return (
    <div className="stack">
      <section className="card">
        <h2>{editing ? 'Edit rule set' : 'Create rule set'}</h2>
        <ErrorNotice message={error} clear={() => setError('')} />
        <form onSubmit={save} className="form-stack">
          <label>
            Name
            <input
              value={form.name}
              onChange={e => setForm({ ...form, name: e.target.value })}
              required
              maxLength={120}
            />
          </label>
          <label>
            Description
            <input
              value={form.description || ''}
              onChange={e => setForm({ ...form, description: e.target.value })}
            />
          </label>
          <fieldset>
            <legend>Rules</legend>
            {rules.length === 0 && <p>Create a rule first.</p>}
            {rules.map(rule => (
              <label className="check" key={rule.id}>
                <input
                  type="checkbox"
                  checked={form.ruleIds.includes(rule.id)}
                  onChange={e =>
                    setForm({
                      ...form,
                      ruleIds: e.target.checked
                        ? [...form.ruleIds, rule.id]
                        : form.ruleIds.filter(id => id !== rule.id),
                    })
                  }
                />{' '}
                {rule.name}{' '}
                <span className="muted">
                  ({rule.severity}
                  {rule.enabled ? '' : ', disabled'})
                </span>
              </label>
            ))}
          </fieldset>
          <label className="check">
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={e => setForm({ ...form, enabled: e.target.checked })}
            />{' '}
            Enabled
          </label>
          <div className="inline">
            <button className="primary" disabled={form.ruleIds.length === 0}>
              {editing ? 'Save rule set' : 'Create rule set'}
            </button>
            {editing && (
              <button
                type="button"
                onClick={() => {
                  setEditing(null);
                  setForm(EMPTY_RULE_SET);
                }}
              >
                Cancel
              </button>
            )}
          </div>
        </form>
      </section>
      <section className="card">
        <h2>Your rule sets</h2>
        {sets.map(set => (
          <div className="list-row" key={set.id}>
            <div>
              <b>{set.name}</b>
              <span className="muted">
                {set.ruleIds.length} rules · {set.enabled ? 'Enabled' : 'Disabled'}
              </span>
            </div>
            <div className="inline">
              <button
                onClick={() => {
                  setEditing(set.id);
                  setForm({
                    name: set.name,
                    description: set.description || '',
                    ruleIds: set.ruleIds,
                    enabled: set.enabled,
                  });
                  window.scrollTo(0, 0);
                }}
              >
                Edit
              </button>
              <button onClick={() => remove(set.id)}>Delete</button>
            </div>
          </div>
        ))}
      </section>
    </div>
  );
}
