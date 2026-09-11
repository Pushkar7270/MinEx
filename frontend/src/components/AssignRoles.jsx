import { useEffect, useState } from "react";
import { api } from "../api";

/** Admin-only: list every user and assign roles. Opens as a modal dialog. */
export default function AssignRoles({ onClose, onToast }) {
  const [users, setUsers] = useState([]);
  const [roles, setRoles] = useState([]);
  const [error, setError] = useState("");
  const [draft, setDraft] = useState({});

  const load = () => {
    api
      .listUsers()
      .then((p) => setUsers(p.content || []))
      .catch((e) => setError(e.message));
    api.roleList().then(setRoles).catch(() => {});
  };

  useEffect(load, []);

  const save = async (u) => {
    const role = draft[u.id];
    if (!role || role === u.role) return;
    try {
      await api.changeRole(u.id, role);
      onToast(`${u.email} is now ${role.replace(/_/g, " ")}`);
      load();
    } catch (e) {
      setError(e.message);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="profile-card" style={{ width: 560 }} onClick={(e) => e.stopPropagation()}>
        <button className="modal-x" onClick={onClose}>✕</button>
        <h3 style={{ marginTop: 18 }}>Assign roles</h3>
        <p className="sub">Admin only. Every change is audit-logged. You cannot change your own role.</p>
        {error && <div className="error">{error}</div>}
        <table className="data">
          <thead>
            <tr><th>User</th><th>Current role</th><th>New role</th><th></th></tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td>
                  <b>{u.fullName || u.email}</b>
                  <div className="muted" style={{ fontSize: 11 }}>{u.email} · {u.provider}</div>
                </td>
                <td>
                  <span className="role-pill" style={{ borderColor: u.roleColor, color: u.roleColor }}>
                    {u.role.replace(/_/g, " ")}
                  </span>
                </td>
                <td>
                  <select
                    value={draft[u.id] ?? u.role}
                    onChange={(e) => setDraft({ ...draft, [u.id]: e.target.value })}
                  >
                    {roles.map((r) => (
                      <option key={r.name} value={r.name}>{r.name.replace(/_/g, " ")}</option>
                    ))}
                  </select>
                </td>
                <td>
                  <button
                    className="btn small"
                    disabled={!draft[u.id] || draft[u.id] === u.role}
                    onClick={() => save(u)}
                  >Save</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
