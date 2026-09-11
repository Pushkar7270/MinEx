import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, apiBase, setToken } from "../api";

export default function Login() {
  const [email, setEmail] = useState("corrector@minex.local");
  const [password, setPassword] = useState("Corrector123!");
  const [error, setError] = useState("");
  const [google, setGoogle] = useState({ google: false, googleUrl: "" });
  const navigate = useNavigate();

  useEffect(() => {
    api.providers().then(setGoogle).catch(() => {});
  }, []);

  const submit = async (e) => {
    e.preventDefault();
    setError("");
    try {
      const res = await api.login(email, password);
      setToken(res.accessToken);
      navigate("/");
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="app-shell" style={{ gridTemplateColumns: "1fr" }}>
      <div className="card form-card">
        <div className="brand">
          <span className="brand-mark">M</span> MinEx IntelliReport
        </div>
        <h3>Sign in</h3>
        <p className="sub">Role-based access — every account has exactly one role.</p>
        {google.google ? (
          <button
            className="btn ghost"
            style={{ width: "100%", marginBottom: 12 }}
            onClick={() => (window.location.href = apiBase + google.googleUrl)}
          >
            <span style={{ fontWeight: 800 }}>G</span>&nbsp;&nbsp;Continue with Google
          </button>
        ) : (
          <p className="muted">Google Sign-In not configured on this deployment.</p>
        )}
        <div className="sub" style={{ textAlign: "center" }}>— or with email —</div>
        <form onSubmit={submit}>
          <label>Email</label>
          <input value={email} onChange={(e) => setEmail(e.target.value)} />
          <label>Password</label>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
          <div style={{ marginTop: 16 }}>
            <button className="btn" type="submit" style={{ width: "100%" }}>
              Sign in
            </button>
          </div>
        </form>
        {error && <div className="error">{error}</div>}
        <p className="muted" style={{ marginTop: 14 }}>
          Demo: corrector@minex.local / Corrector123! · supervisor@minex.local / Supervisor123! ·
          admin@minex.local / Admin123!
        </p>
      </div>
    </div>
  );
}
