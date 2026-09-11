import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { api, clearToken, emailFromToken, roleFromToken } from "../api";
import { useEffect, useState } from "react";

export default function Layout({ onToast }) {
  const navigate = useNavigate();
  const [profile, setProfile] = useState({ email: emailFromToken(), role: roleFromToken(), roleColor: "#8b6cc1" });
  const [hierarchy, setHierarchy] = useState([]);
  const initials = (profile.email || "?").slice(0, 2).toUpperCase();

  useEffect(() => {
    // Discord-style profile: role color + full hierarchy from the server.
    api.me().then(setProfile).catch(() => {});
    api.roleList().then(setHierarchy).catch(() => {});
  }, []);

  const logout = () => {
    clearToken();
    navigate("/login");
  };

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">M</span> MinEx IntelliReport
        </div>
        <NavLink to="/" end className={({ isActive }) => "nav-item" + (isActive ? " active" : "")}>
          <span>◉</span> Dashboard
        </NavLink>
        <NavLink to="/review" className={({ isActive }) => "nav-item" + (isActive ? " active" : "")}>
          <span>▤</span> Review Queue
        </NavLink>
        <NavLink to="/chat" className={({ isActive }) => "nav-item" + (isActive ? " active" : "")}>
          <span>✦</span> AI Chat <span className="soon-badge">Soon</span>
        </NavLink>
        <NavLink to="/drafts" className={({ isActive }) => "nav-item" + (isActive ? " active" : "")}>
          <span>📄</span> Drafts <span className="soon-badge">Soon</span>
        </NavLink>
        {hierarchy.length > 0 && (
          <div className="roles-block">
            <div className="roles-title">ROLES — HIGH TO LOW</div>
            {hierarchy.map((r) => (
              <div key={r.name} className={"role-row" + (r.mine ? " mine" : "")}>
                <span className="role-dot" style={{ background: r.color }} />
                <span className="role-name">{r.name.replace(/_/g, " ")}</span>
                {r.mine && <span className="soon-badge">you</span>}
              </div>
            ))}
          </div>
        )}
        <div className="sidebar-foot">
          <div className="role-chip">
            Signed in as<b>{profile.email}</b>
            <span className="role-pill" style={{ borderColor: profile.roleColor, color: profile.roleColor }}>
              {profile.role?.replace(/_/g, " ")}
            </span>
          </div>
          <button className="link-btn" onClick={logout}>
            ⎋ Log out
          </button>
        </div>
      </aside>
      <div className="main">
        <div className="topbar">
          <div>
            <h1>Welcome{profile.email ? `, ${profile.email.split("@")[0]}` : ""}</h1>
            <p>Here&apos;s your mining intelligence overview</p>
          </div>
          <div className="topbar-right">
            <button className="ai-search" onClick={() => navigate("/chat")}>
              ✦ Ask MinEx AI anything
            </button>
            <button className="icon-btn" onClick={() => onToast("Notifications coming soon")}>
              🔔
            </button>
            <div className="user-meta">
              <b>{profile.email}</b>
              <span className="role-pill" style={{ borderColor: profile.roleColor, color: profile.roleColor }}>
                {profile.role?.replace(/_/g, " ")}
              </span>
            </div>
            <div className="avatar">{initials}</div>
          </div>
        </div>
        <Outlet />
      </div>
    </div>
  );
}
