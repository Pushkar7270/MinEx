import { useState } from "react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import Layout from "./components/Layout";
import Dashboard from "./pages/Dashboard";
import ReviewQueue from "./pages/ReviewQueue";
import AuditLog from "./pages/AuditLog";
import Login from "./pages/Login";
import OAuthCallback from "./pages/OAuthCallback";
import Welcome from "./pages/Welcome";
import Chat from "./pages/Chat";
import { DraftsSoon } from "./pages/ComingSoon";
import { getToken } from "./api";

function Protected({ children }) {
  return getToken() ? children : <Navigate to="/login" replace />;
}

export default function App() {
  const [toast, setToast] = useState("");
  const showToast = (msg) => {
    setToast(msg);
    setTimeout(() => setToast(""), 2600);
  };

  return (
    <BrowserRouter>
      {toast && <div className="toast">{toast}</div>}
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/oauth/callback" element={<OAuthCallback />} />
        <Route
          path="/welcome"
          element={
            <Protected>
              <Welcome onToast={showToast} />
            </Protected>
          }
        />
        <Route
          element={
            <Protected>
              <Layout onToast={showToast} />
            </Protected>
          }
        >
          <Route path="/" element={<Dashboard />} />
          <Route path="/review" element={<ReviewQueue onToast={showToast} />} />
          <Route path="/audit" element={<AuditLog />} />
          <Route path="/chat" element={<Chat />} />
          <Route path="/drafts" element={<DraftsSoon onToast={showToast} />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
