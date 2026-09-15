import { useEffect, useState } from "react";
import { api } from "../api";

const ACTION_META = {
  FIELD_APPROVED: { label: "Approved", cls: "good" },
  FIELD_REJECTED: { label: "Rejected", cls: "bad" },
  FIELD_CORRECTED: { label: "Corrected", cls: "accent" },
  FIELD_PUBLISHED: { label: "Published", cls: "accent" },
  FIELD_REOPENED: { label: "Reopened", cls: "" },
  DOCUMENT_UPLOADED: { label: "Uploaded", cls: "" },
  ROLE_CHANGED: { label: "Role changed", cls: "" },
  USER_PROFILE_UPDATED: { label: "Profile updated", cls: "" },
};

const label = (action) => ACTION_META[action]?.label || action;
const actionCls = (action) => ACTION_META[action]?.cls || "";

function parseJson(s) {
  try {
    return s ? JSON.parse(s) : null;
  } catch {
    return null;
  }
}

function describe(v) {
  if (v == null) return null;
  if (typeof v === "object") {
    return Object.entries(v)
      .map(([k, val]) => `${k.replace(/_/g, " ")}: ${val ?? "—"}`)
      .join(" · ");
  }
  return String(v);
}

function change(oldValue, newValue) {
  const from = describe(parseJson(oldValue));
  const to = describe(parseJson(newValue));
  if (from && to && from !== to) return { from, to };
  const only = to || from;
  return only ? { to: only } : null;
}

const fmtWhen = (s) => {
  if (!s) return "—";
  const d = new Date(s);
  return Number.isNaN(d.getTime()) ? s : d.toLocaleString();
};

export default function AuditLog() {
  const [rows, setRows] = useState([]);
  const [actions, setActions] = useState([]);
  const [action, setAction] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState("");

  useEffect(() => {
    api.auditActions().then(setActions).catch(() => {});
  }, []);

  useEffect(() => {
    let active = true;
    api
      .auditLog(page, 50, { action })
      .then((p) => {
        if (!active) return;
        setError("");
        setRows(p.content || []);
        setTotalPages(p.totalPages || 0);
        setTotal(p.totalElements || 0);
      })
      .catch((e) => active && setError(e.message));
    return () => {
      active = false;
    };
  }, [page, action]);

  return (
    <div className="card">
      <h3>Audit log</h3>
      <p className="sub">
        Every upload, correction, approval, rejection and publication — with who did it and when.
        Read-only; restricted to managers and admins.
      </p>
      <div className="tabs" style={{ marginBottom: 8, alignItems: "center" }}>
        <select
          value={action}
          onChange={(e) => {
            setPage(0);
            setAction(e.target.value);
          }}
          style={{ width: "auto", minWidth: 220, padding: "7px 12px", borderRadius: 20 }}
        >
          <option value="">All actions</option>
          {actions.map((a) => (
            <option key={a} value={a}>{label(a)}</option>
          ))}
        </select>
        <span className="muted">{total} entries</span>
      </div>
      {error && <div className="error">{error}</div>}
      <table className="data">
        <thead>
          <tr>
            <th>When</th>
            <th>Who</th>
            <th>Action</th>
            <th>Entity</th>
            <th>Change</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => {
            const c = change(r.oldValue, r.newValue);
            return (
              <tr key={r.id}>
                <td className="muted" style={{ whiteSpace: "nowrap" }}>{fmtWhen(r.createdAt)}</td>
                <td>
                  <b>{r.userName || r.userEmail || "system"}</b>
                  {r.userEmail && (
                    <div className="muted" style={{ fontSize: 11 }}>{r.userEmail}</div>
                  )}
                  {r.userRole && <span className="pill">{r.userRole.replace(/_/g, " ")}</span>}
                </td>
                <td><span className={"pill " + actionCls(r.action)}>{label(r.action)}</span></td>
                <td className="muted">
                  {r.entityType || "—"}
                  {r.entityId && (
                    <div style={{ fontSize: 11 }} title={r.entityId}>{r.entityId.slice(0, 8)}…</div>
                  )}
                </td>
                <td>
                  {c ? (
                    c.from ? (
                      <span className="muted">{c.from} → <b>{c.to}</b></span>
                    ) : (
                      <span className="muted">{c.to}</span>
                    )
                  ) : (
                    <span className="muted">—</span>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {rows.length === 0 && !error && (
        <p className="muted">No audit entries{action ? " for this action" : ""} yet.</p>
      )}
      {totalPages > 1 && (
        <div className="row-btns" style={{ marginTop: 12, alignItems: "center" }}>
          <button className="btn small ghost" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            ← Newer
          </button>
          <span className="muted">Page {page + 1} of {totalPages}</span>
          <button
            className="btn small ghost"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Older →
          </button>
        </div>
      )}
    </div>
  );
}
