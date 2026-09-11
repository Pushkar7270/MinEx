import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api";

/** First-time sign-in: pick the display name shown on your profile. */
export default function Welcome({ onToast }) {
  const [name, setName] = useState("");
  const [error, setError] = useState("");
  const navigate = useNavigate();

  const submit = async (e) => {
    e.preventDefault();
    if (!name.strip()) {
      setError("Please enter a display name");
      return;
    }
    try {
      await api.updateMe(name.strip());
      onToast?.("Welcome aboard!");
      navigate("/", { replace: true });
    } catch (err) {
      setError(err.message);
    }
  };

  const skip = () => navigate("/", { replace: true });

  return (
    <div className="app-shell" style={{ gridTemplateColumns: "1fr" }}>
      <div className="card form-card">
        <div className="brand">
          <span className="brand-mark">M</span> MinEx IntelliReport
        </div>
        <h3>Choose your display name</h3>
        <p className="sub">
          This is the username shown on your profile, next to your role badge.
          You can change it later from your profile.
        </p>
        <form onSubmit={submit}>
          <label>Display name</label>
          <input
            value={name}
            maxLength={80}
            placeholder="e.g. Priya Sharma"
            onChange={(e) => setName(e.target.value)}
          />
          <div style={{ marginTop: 16, display: "flex", gap: 10 }}>
            <button className="btn" type="submit" style={{ flex: 1 }}>
              Continue
            </button>
            <button className="btn ghost" type="button" onClick={skip}>
              Skip
            </button>
          </div>
        </form>
        {error && <div className="error">{error}</div>}
      </div>
    </div>
  );
}
