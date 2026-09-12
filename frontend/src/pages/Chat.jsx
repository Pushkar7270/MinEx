import { useState } from "react";
import { api } from "../api";

const SUGGESTIONS = [
  "What was coal production in 2024-25?",
  "What was the budget outlay?",
  "What is the revenue expenditure?",
  "How many safety accidents were reported?",
];

export default function Chat() {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [sessionId, setSessionId] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [info, setInfo] = useState("");

  const send = async (text) => {
    const q = (text ?? input).trim();
    if (!q || busy) return;
    setError("");
    setInput("");
    setMessages((m) => [...m, { role: "user", content: q }]);
    setBusy(true);
    try {
      const res = await api.chatQuery(q, sessionId);
      setSessionId(res.session_id);
      setMessages((m) => [
        ...m,
        { role: "assistant", content: res.answer, sources: res.sources || [] },
      ]);
    } catch (e) {
      setError(e.message);
      setMessages((m) => [
        ...m,
        { role: "assistant", content: "Sorry — the chat service is unavailable right now.", sources: [] },
      ]);
    } finally {
      setBusy(false);
    }
  };

  const refreshIndex = async () => {
    setError("");
    setInfo("");
    try {
      const r = await api.chatReindex();
      setInfo(`Re-indexed ${r.indexed} published figures.`);
    } catch (e) {
      setInfo(e.message);
    }
  };

  const reset = () => {
    setMessages([]);
    setSessionId(null);
    setError("");
    setInfo("");
  };

  return (
    <div className="card chat-card">
      <div className="chat-head">
        <div>
          <h3>Ask MinEx AI</h3>
          <p className="sub">Answers only from approved/published figures, with citations.</p>
        </div>
        <div className="row-btns">
          <button className="btn small ghost" onClick={refreshIndex} title="Rebuild the index from published data (admin)">
            Refresh data
          </button>
          {messages.length > 0 && (
            <button className="btn small ghost" onClick={reset}>New chat</button>
          )}
        </div>
      </div>

      <div className="chat-log">
        {messages.length === 0 && (
          <div className="chat-empty">
            <p className="muted">Ask a factual question about verified mining data.</p>
            <div className="row-btns">
              {SUGGESTIONS.map((s) => (
                <button key={s} className="tab" onClick={() => send(s)}>{s}</button>
              ))}
            </div>
          </div>
        )}
        {messages.map((m, i) => (
          <div key={i} className={"chat-msg " + m.role}>
            <div className="chat-bubble">{m.content}</div>
            {m.role === "assistant" && m.sources?.length > 0 && (
              <div className="chat-sources">
                <div className="muted" style={{ fontSize: 11 }}>Sources</div>
                <div className="row-btns">
                  {m.sources.map((s, j) => (
                    <span key={j} className="pill">
                      {s.document || "document"}
                      {s.period ? ` · ${s.period}` : ""}
                      {s.category ? ` · ${s.category}` : ""}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        ))}
        {busy && <div className="muted">Thinking…</div>}
      </div>

      {info && <div className="muted" style={{ fontSize: 12 }}>{info}</div>}
      {error && <div className="error">{error}</div>}

      <form className="chat-input" onSubmit={(e) => { e.preventDefault(); send(); }}>
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Ask about verified figures…"
        />
        <button className="btn" type="submit" disabled={busy || !input.trim()}>Send</button>
      </form>
    </div>
  );
}
