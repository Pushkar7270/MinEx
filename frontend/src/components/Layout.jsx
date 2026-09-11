import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { clearToken, emailFromToken, roleFromToken } from "../api";
import { useState } from "react";

export default function Layout({ onToast }) {
  const navigate = useNavigate();
  const [email] = useState(emailFromToken());
  const [role] = useState(roleFromToken());
  const initials = (email || "?").slice(0, 2).toUpperCase();

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
        <div className="sidebar-foot">
          <div className="role-chip">
            Signed in as<b>{email}</b>
            {role}
          </div>
          <button className="link-btn" onClick={logout}>
            ⎋ Log out
          </button>
        </div>
      </aside>
      <div className="main">
        <div className="topbar">
          <div>
            <h1>Welcome{email ? `, ${email.split("@")[0]}` : ""}</h1>
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
              <b>{email}</b>
              {role}
            </div>
            <div className="avatar">{initials}</div>
          </div>
        </div>
        <Outlet />
      </div>
    </div>
  );
}
