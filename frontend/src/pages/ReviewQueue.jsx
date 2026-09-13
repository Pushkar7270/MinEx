import { useEffect, useRef, useState } from "react";
import { api } from "../api";

export default function ReviewQueue({ onToast }) {
  const [me, setMe] = useState(null);
  const role = me?.role;
  const canReview = !!me?.canReview;
  const canPublish = !!me?.canPublish;
  const [docs, setDocs] = useState([]);
  const [docId, setDocId] = useState("");
  const [tab, setTab] = useState("pending_review");
  const [rows, setRows] = useState([]);
  const [error, setError] = useState("");
  const [file, setFile] = useState(null);
  const [edit, setEdit] = useState({});
  const [lastRejected, setLastRejected] = useState(null);
  const undoTimer = useRef(null);

  useEffect(() => {
    api.me().then(setMe).catch(() => {});
  }, []);

  const loadDocs = () =>
    api.documents(0, 50).then((d) => {
      setDocs(d.content || []);
      if (!docId && d.content?.length) setDocId(d.content[0].id);
    });

  useEffect(() => {
    loadDocs().catch((e) => setError(e.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const loadRows = () => {
    if (!docId) {
      setRows([]);
      return Promise.resolve();
    }
    return api
      .reviewQueue(docId, 0, 50, tab)
      .then((r) => setRows(r.content || []))
      .catch((e) => setError(e.message));
  };

  useEffect(() => {
    loadRows();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [docId, tab]);

  const refresh = () => {
    loadDocs().catch(() => {});
    loadRows().catch(() => {});
  };

  const doUpload = async () => {
    if (!file) return;
    setError("");
    try {
      await api.upload(file);
      onToast("Uploaded — extraction runs in the background");
      setFile(null);
      setTimeout(refresh, 4000);
    } catch (e) {
      setError(e.message);
    }
  };

  const act = async (fn, label) => {
    setError("");
    try {
      const res = await fn();
      if (label) onToast(label);
      refresh();
      return res;
    } catch (e) {
      setError(e.message);
    }
  };

  const clearUndo = () => {
    if (undoTimer.current) {
      clearTimeout(undoTimer.current);
      undoTimer.current = null;
    }
  };

  /** Reject, then offer a 30s Ctrl+Z / Undo for the row we just rejected. */
  const rejectRow = async (r) => {
    setError("");
    try {
      await api.reject(r.id);
      onToast(`Rejected "${r.fieldName}" — press Ctrl+Z to undo`);
      clearUndo();
      setLastRejected({ id: r.id, name: r.fieldName });
      undoTimer.current = setTimeout(() => setLastRejected(null), 30000);
      refresh();
    } catch (e) {
      setError(e.message);
    }
  };

  const undoReject = async () => {
    if (!lastRejected) return;
    setError("");
    try {
      await api.reopen(lastRejected.id);
      onToast(`Restored "${lastRejected.name}"`);
      clearUndo();
      setLastRejected(null);
      refresh();
    } catch (e) {
      setError(e.message);
    }
  };

  const restoreRow = async (r) => {
    setError("");
    try {
      await api.reopen(r.id);
      onToast(`Restored "${r.fieldName}"`);
      refresh();
    } catch (e) {
      setError(e.message);
    }
  };

  useEffect(() => () => clearUndo(), []);

  // Ctrl+Z while the undo is available (ignored when typing in a field so it
  // doesn't fight the browser's native text undo).
  useEffect(() => {
    if (!lastRejected) return;
    const onKey = (e) => {
      const t = e.target;
      const typing = t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA" || t.isContentEditable);
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "z" && !typing) {
        e.preventDefault();
        undoReject();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lastRejected]);

  const submitCorrection = (row) => {
    const v = edit[row.id];
    if (!v || (v.value === undefined && v.text === undefined && v.category === undefined)) return;
    act(async () => {
      const res = await api.correct(row.id, v.value === "" || v.value === undefined ? null : Number(v.value), v.text || null, v.category || null);
      onToast(`Saved as version ${res.version} — old version kept for audit`);
    }, null);
  };

  const myEmail = (me?.email || "").toLowerCase();
  const canRestore = (r) => canReview && !!r.reviewedBy && r.reviewedBy.toLowerCase() === myEmail;

  return (
    <>
      <div className="card">
        <h3>Upload report</h3>
        <p className="sub">PDF, Excel, Word, CSV or images — stored and queued for extraction.</p>
        <div className="doc-bar">
          <div>
            <input type="file" onChange={(e) => setFile(e.target.files[0])} />
          </div>
          <button className="btn" disabled={!file} onClick={doUpload}>
            Upload
          </button>
        </div>
        {error && <div className="error">{error}</div>}
      </div>
      <div className="card">
        <h3>Documents</h3>
        <p className="sub">All uploaded reports and their processing status — review is team work.</p>
        {docs.length === 0 && (
          <p className="muted">
            Nothing here yet. Upload a report above — once it is processed,
            its figures appear here for the whole team to review.
          </p>
        )}
        <table className="data">
          <thead>
            <tr><th>File</th><th>Type</th><th>Status</th><th></th></tr>
          </thead>
          <tbody>
            {docs.map((d) => (
              <tr key={d.id} style={d.id === docId ? { background: "rgba(192,132,184,.08)" } : {}}>
                <td>{d.originalFilename}</td>
                <td className="muted">{d.mimeType}</td>
                <td><span className="pill accent">{d.status}</span></td>
                <td><button className="btn small ghost" onClick={() => setDocId(d.id)}>Review</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="card">
        <h3>Review queue {role && !canReview ? "(corrections create new versions)" : ""}</h3>
        <p className="sub">
          Figures awaiting a decision — correct, then approve/publish. Signed in as <b>{role || "…"}</b>.
          {!canReview && " Your role can submit corrections; approval needs a reviewer."}
        </p>
        <div className="tabs" style={{ marginBottom: 8 }}>
          <button
            className={"tab" + (tab === "pending_review" ? " active" : "")}
            onClick={() => setTab("pending_review")}
          >
            Pending
          </button>
          <button
            className={"tab" + (tab === "rejected" ? " active" : "")}
            onClick={() => setTab("rejected")}
          >
            Rejected
          </button>
        </div>
        {lastRejected && (
          <div className="undo-bar">
            Rejected <b>“{lastRejected.name}”</b>
            <button className="link-btn" onClick={undoReject}>Undo (Ctrl+Z)</button>
          </div>
        )}
        {tab === "pending_review" && (
          <div className="row-btns" style={{ margin: "2px 0 12px" }}>
            <span className="pill prio-pill critical">Red · human verification required</span>
            <span className="pill prio-pill review">Yellow · check if unsure</span>
            <span className="pill prio-pill ok">Green · low risk</span>
          </div>
        )}
        <table className="data">
          <thead>
            <tr><th>Field</th><th>Value</th><th>Category</th><th>Period</th><th>Conf.</th><th>Status</th><th>Correct</th><th>Decision</th></tr>
          </thead>
          <tbody>
            {rows.map((r) => {
              const pending = r.status === "pending_review";
              const approved = r.status === "approved";
              return (
              <tr key={r.id} className={"prio-" + (r.priority || "review")}>
                <td>{r.fieldName}</td>
                <td><b>{r.fieldValue ?? r.fieldText}</b> <span className="muted">{r.unit}</span></td>
                <td className="muted">{r.category || "—"}</td>
                <td className="muted">{r.period || "—"}</td>
                <td>
                  <span className={"prio-dot " + (r.priority || "review")} />
                  <span className="muted">{Math.round(r.confidenceScore * 100)}%</span>
                </td>
                <td><span className="pill">{r.status} · v{r.version}</span></td>
                <td>
                  <div className="inline-edit">
                    <input
                      placeholder="value"
                      onChange={(e) => setEdit({ ...edit, [r.id]: { ...edit[r.id], value: e.target.value } })}
                    />
                    <input
                      placeholder="category?"
                      style={{ width: 110 }}
                      onChange={(e) => setEdit({ ...edit, [r.id]: { ...edit[r.id], category: e.target.value } })}
                    />
                    <button className="btn small ghost" onClick={() => submitCorrection(r)}>Save</button>
                  </div>
                </td>
                <td>
                  {tab === "rejected" ? (
                    <div className="row-btns">
                      <button
                        className="btn small"
                        disabled={!canRestore(r)}
                        title={canRestore(r)
                          ? "Restore to pending"
                          : "Only the reviewer who rejected this figure can restore it"}
                        onClick={() => restoreRow(r)}
                      >Restore</button>
                    </div>
                  ) : (
                    <div className="row-btns">
                      <button
                        className="btn small"
                        disabled={!canReview || !pending}
                        title={!pending ? "Only pending items can be approved" : "Approve"}
                        onClick={() => act(() => api.approve(r.id), "Approved")}
                      >Approve</button>
                      <button
                        className="btn small danger"
                        disabled={!canReview || !pending}
                        title={!pending ? "Only pending items can be rejected" : "Reject"}
                        onClick={() => rejectRow(r)}
                      >Reject</button>
                      <button
                        className="btn small ghost"
                        disabled={!canPublish || !approved}
                        title={!approved ? "Approve first, then publish" : "Publish to dashboard"}
                        onClick={() => act(() => api.publish(r.id), "Published to dashboard")}
                      >Publish</button>
                    </div>
                  )}
                </td>
              </tr>
              );
            })}
          </tbody>
        </table>
        {rows.length === 0 && (
          <p className="muted">
            {tab === "rejected" ? "No rejected figures for this document." : "Nothing awaiting review for this document."}
          </p>
        )}
      </div>
    </>
  );
}
