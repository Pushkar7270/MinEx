import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { api, clearToken, emailFromToken, roleFromToken } from "../api";
import { useEffect, useState } from "react";
import AssignRoles from "./AssignRoles";

export default function Layout({ onToast }) {
  const navigate = useNavigate();
  const [profile, setProfile] = useState({ email: emailFromToken(), role: roleFromToken(), roleColor: "#8b6cc1" });
  const [hierarchy, setHierarchy] = useState([]);
  const [showProfile, setShowProfile] = useState(false);
  const [showRoles, setShowRoles] = useState(false);
  const [editingName, setEditingName] = useState(false);
  const [draftName, setDraftName] = useState("");

  const reloadProfile = () => {
    api.me().then(setProfile).catch(() => {});
    api.roleList().then(setHierarchy).catch(() => {});
  };
  const nameWords = (profile.fullName || "").trim().split(/\s+/).filter(Boolean);
  const initials = (nameWords.length > 1
    ? nameWords[0][0] + nameWords[1][0]
    : (nameWords[0]?.[0] || profile.email || "?").slice(0, 2)).toUpperCase();

  useEffect(() => {
    // Discord-style profile: role color + full hierarchy from the server.
    reloadProfile();
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
          <span>✦</span> AI Chat
        </NavLink>
        <NavLink to="/drafts" className={({ isActive }) => "nav-item" + (isActive ? " active" : "")}>
          <span>📄</span> Drafts <span className="soon-badge">Soon</span>
        </NavLink>
        {hierarchy.length > 0 && (
          <div className="roles-block">
            <div className="roles-title">ROLES</div>
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
          {profile.canManageUsers && (
            <button className="nav-item" onClick={() => setShowRoles(true)} style={{ width: "100%" }}>
              <span>🛡</span> Assign Roles
            </button>
          )}
          <div className="role-chip">
            Signed in as<b>{profile.fullName || profile.email}</b>
            <span className="email-small">{profile.email}</span>
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
            <h1>Welcome{profile.fullName ? `, ${profile.fullName.split(" ")[0]}` : ""}</h1>
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
              <b>{profile.fullName || profile.email}</b>
              <span className="muted" style={{ display: "block", fontSize: 11 }}>{profile.email}</span>
              <span className="role-pill" style={{ borderColor: profile.roleColor, color: profile.roleColor }}>
                {profile.role?.replace(/_/g, " ")}
              </span>
            </div>
            <button className="avatar-btn" onClick={() => setShowProfile(true)} title="View profile">
              <div className="avatar">{initials}</div>
            </button>
          </div>
        </div>
        <Outlet />
        {showRoles && (
          <AssignRoles onClose={() => { setShowRoles(false); reloadProfile(); }} onToast={onToast} />
        )}
        {showProfile && (
          <div className="modal-overlay" onClick={() => setShowProfile(false)}>
            <div className="profile-card" onClick={(e) => e.stopPropagation()}>
              <div className="profile-banner" />
              <button className="modal-x" onClick={() => setShowProfile(false)}>✕</button>
              <div className="profile-head">
                <div className="avatar big">{initials}</div>
                <div>
                  {editingName ? (
                    <div className="inline-edit">
                      <input
                        value={draftName}
                        maxLength={80}
                        onChange={(e) => setDraftName(e.target.value)}
                      />
                      <button
                        className="btn small"
                        onClick={async () => {
                          try {
                            await api.updateMe(draftName.strip());
                            setEditingName(false);
                            reloadProfile();
                            onToast("Display name updated");
                          } catch (e) {
                            onToast(e.message);
                          }
                        }}
                      >Save</button>
                    </div>
                  ) : (
                    <div className="profile-name">
                      {profile.fullName || profile.email}{" "}
                      <button
                        className="link-btn"
                        onClick={() => {
                          setDraftName(profile.fullName || "");
                          setEditingName(true);
                        }}
                      >✎</button>
                    </div>
                  )}
                  <div className="muted">{profile.email}</div>
                  <span className="pill">{profile.provider === "google" ? "Google account" : "Local account"}</span>
                </div>
              </div>
              <div className="roles-title" style={{ marginTop: 14 }}>ROLES</div>
              {hierarchy.map((r) => (
                <div key={r.name} className={"role-row" + (r.mine ? " mine" : "")}>
                  <span className="role-dot" style={{ background: r.color }} />
                  <span className="role-name">{r.name.replace(/_/g, " ")}</span>
                  {r.mine && <span className="soon-badge">you</span>}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
