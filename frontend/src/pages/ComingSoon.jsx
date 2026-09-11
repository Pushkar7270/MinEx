export function ComingSoon({ phase, title, blurb, onToast }) {
  return (
    <div className="card soon-hero">
      <div className="big-emoji">🚧</div>
      <h2>{title}</h2>
      <p className="muted" style={{ maxWidth: 520, margin: "0 auto 18px" }}>{blurb}</p>
      <span className="pill accent">{phase} · Coming soon</span>
      <div style={{ marginTop: 22, display: "flex", gap: 10, justifyContent: "center" }}>
        <input
          placeholder={
            phase === "Phase 2"
              ? "Ask about yields, budgets, expenditures…"
              : "Parliamentary question topic…"
          }
          disabled
          style={{ maxWidth: 340 }}
        />
        <button className="btn" disabled onClick={() => onToast("Coming soon")}>
          {phase === "Phase 2" ? "Ask" : "Generate draft"}
        </button>
      </div>
      <p className="muted" style={{ marginTop: 14 }}>
        This demo build covers Phase 1 (ingestion → review → dashboard) only.
      </p>
    </div>
  );
}

export function ChatSoon(props) {
  return (
    <ComingSoon
      phase="Phase 2"
      title="AI Chat over verified data"
      blurb="Ask factual questions about approved figures with source citations. The chatbot answers only from published data — and refuses rather than hallucinates."
      {...props}
    />
  );
}

export function DraftsSoon(props) {
  return (
    <ComingSoon
      phase="Phase 3"
      title="Parliamentary draft auto-responder"
      blurb="Auto-fill official Ministry/Parliamentary templates from the same approved dataset. PDF + editable Word export, Manager approval before dispatch."
      {...props}
    />
  );
}
