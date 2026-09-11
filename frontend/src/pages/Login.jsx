import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, apiBase, setToken } from "../api";

// Placeholder accounts — one per seeded role (see Backend V4__placeholder_users.sql).
// Local/dev demo logins: swap these out when the real role taxonomy lands.
const DEMO_ACCOUNTS = [
  { role: "Data Corrector", email: "corrector@minex.local", password: "Corrector123!" },
  { role: "Sub Supervisor", email: "subsupervisor@minex.local", password: "SubSupervisor123!" },
  { role: "Supervisor", email: "supervisor@minex.local", password: "Supervisor123!" },
  { role: "Manager", email: "manager@minex.local", password: "Manager123!" },
  { role: "Admin", email: "admin@minex.local", password: "Admin123!" },
];

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
        <div style={{ marginTop: 14 }}>
          <label>Placeholder sign-ins (one per role — click to fill)</label>
          <div className="demo-logins">
            {DEMO_ACCOUNTS.map((a) => (
              <button
                key={a.email}
                type="button"
                className="demo-login"
                title={`Sign in as ${a.role}`}
                onClick={() => {
                  setEmail(a.email);
                  setPassword(a.password);
                }}
              >
                <span className="demo-role">{a.role}</span>
                <span className="demo-email">{a.email}</span>
                <span className="demo-pass">{a.password}</span>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
