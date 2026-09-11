import { useEffect, useState } from "react";
import { api, roleFromToken } from "../api";

const REVIEWER_ROLES = ["SUB_SUPERVISOR", "SUPERVISOR", "MANAGER", "ADMIN"];
const PUBLISH_ROLES = ["SUPERVISOR", "MANAGER", "ADMIN"];

export default function ReviewQueue({ onToast }) {
  const role = roleFromToken();
  const [docs, setDocs] = useState([]);
  const [docId, setDocId] = useState("");
  const [rows, setRows] = useState([]);
  const [error, setError] = useState("");
  const [file, setFile] = useState(null);
  const [edit, setEdit] = useState({});

  const loadDocs = () =>
    api.documents(0, 50).then((d) => {
      setDocs(d.content || []);
      if (!docId && d.content?.length) setDocId(d.content[0].id);
    });

  useEffect(() => {
    loadDocs().catch((e) => setError(e.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!docId) return;
    api
      .reviewQueue(docId, 0, 50)
      .then((r) => setRows(r.content || []))
      .catch((e) => setError(e.message));
  }, [docId]);

  const refresh = () => {
    loadDocs().catch(() => {});
    if (docId) api.reviewQueue(docId, 0, 50).then((r) => setRows(r.content || [])).catch(() => {});
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

  const submitCorrection = (row) => {
    const v = edit[row.id];
    if (!v || (v.value === undefined && v.text === undefined)) return;
    act(async () => {
      const res = await api.correct(row.id, v.value === "" || v.value === undefined ? null : Number(v.value), v.text || null);
      onToast(`Saved as version ${res.version} — old version kept for audit`);
    }, null);
  };

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
        <p className="sub">Your uploads and their processing status.</p>
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
        <h3>Review queue {role === "DATA_CORRECTOR" ? "(corrections create new versions)" : ""}</h3>
        <p className="sub">
          Low-confidence or flagged figures. Signed in as <b>{role}</b>.
          {!REVIEWER_ROLES.includes(role) && " Your role can submit corrections; approval needs a reviewer."}
        </p>
        <table className="data">
          <thead>
            <tr><th>Field</th><th>Value</th><th>Category</th><th>Period</th><th>Conf.</th><th>Status</th><th>Correct</th><th>Decision</th></tr>
          </thead>
          <tbody>
            {rows.map((r) => {
              const pending = r.status === "pending_review";
              const approved = r.status === "approved";
              return (
              <tr key={r.id}>
                <td>{r.fieldName}</td>
                <td><b>{r.fieldValue ?? r.fieldText}</b> <span className="muted">{r.unit}</span></td>
                <td className="muted">{r.category || "—"}</td>
                <td className="muted">{r.period || "—"}</td>
                <td className="muted">{Math.round(r.confidenceScore * 100)}%</td>
                <td><span className="pill">{r.status} · v{r.version}</span></td>
                <td>
                  <div className="inline-edit">
                    <input
                      placeholder="value"
                      onChange={(e) => setEdit({ ...edit, [r.id]: { ...edit[r.id], value: e.target.value } })}
                    />
                    <button className="btn small ghost" onClick={() => submitCorrection(r)}>Save</button>
                  </div>
                </td>
                <td>
                  <div className="row-btns">
                    <button
                      className="btn small"
                      disabled={!REVIEWER_ROLES.includes(role) || !pending}
                      title={!pending ? "Only pending items can be approved" : "Approve"}
                      onClick={() => act(() => api.approve(r.id), "Approved")}
                    >Approve</button>
                    <button
                      className="btn small danger"
                      disabled={!REVIEWER_ROLES.includes(role) || !pending}
                      title={!pending ? "Only pending items can be rejected" : "Reject"}
                      onClick={() => act(() => api.reject(r.id), "Rejected")}
                    >Reject</button>
                    <button
                      className="btn small ghost"
                      disabled={!PUBLISH_ROLES.includes(role) || !approved}
                      title={!approved ? "Approve first, then publish" : "Publish to dashboard"}
                      onClick={() => act(() => api.publish(r.id), "Published to dashboard")}
                    >Publish</button>
                  </div>
                </td>
              </tr>
              );
            })}
          </tbody>
        </table>
        {rows.length === 0 && <p className="muted">Nothing awaiting review for this document.</p>}
      </div>
    </>
  );
}
