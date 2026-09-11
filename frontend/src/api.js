const BASE = import.meta.env.VITE_API_URL || "http://localhost:8080";

export const getToken = () => localStorage.getItem("minex_token");
export const setToken = (t) => localStorage.setItem("minex_token", t);
export const clearToken = () => localStorage.removeItem("minex_token");

export function roleFromToken() {
  try {
    const payload = JSON.parse(atob(getToken().split(".")[1]));
    return payload.role || null;
  } catch {
    return null;
  }
}

export function emailFromToken() {
  try {
    return JSON.parse(atob(getToken().split(".")[1])).sub || "";
  } catch {
    return "";
  }
}

async function req(path, opts = {}) {
  const res = await fetch(BASE + path, {
    ...opts,
    headers: {
      "Content-Type": "application/json",
      ...(getToken() ? { Authorization: `Bearer ${getToken()}` } : {}),
      ...(opts.headers || {}),
    },
  });
  if (res.status === 401 || res.status === 403) {
    const err = new Error("Not authorized for your role");
    err.status = res.status;
    throw err;
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.message || `Request failed (${res.status})`);
  return data;
}

async function uploadDoc(file) {
  const form = new FormData();
  form.append("file", file);
  const res = await fetch(BASE + "/api/v1/documents", {
    method: "POST",
    headers: getToken() ? { Authorization: `Bearer ${getToken()}` } : {},
    body: form,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.message || `Upload failed (${res.status})`);
  return data;
}

export const apiBase = BASE;

export const api = {
  login: (email, password) =>
    req("/api/v1/auth/login", { method: "POST", body: JSON.stringify({ email, password }) }),
  providers: () => req("/api/v1/auth/providers"),
  me: () => req("/api/v1/auth/me"),
  updateMe: (fullName) =>
    req("/api/v1/auth/me", { method: "PATCH", body: JSON.stringify({ fullName }) }),
  roleList: () => req("/api/v1/auth/roles"),
  summary: () => req("/api/v1/dashboard/summary"),
  timeseries: (id, from, to) => {
    const q = new URLSearchParams();
    if (from) q.set("from", from);
    if (to) q.set("to", to);
    const s = q.toString();
    return req(`/api/v1/dashboard/categories/${id}/timeseries${s ? `?${s}` : ""}`);
  },
  documents: (page = 0, size = 20) => req(`/api/v1/documents?page=${page}&size=${size}`),
  upload: uploadDoc,
  reviewQueue: (docId, page = 0, size = 50) =>
    req(`/api/v1/documents/${docId}/review-queue?page=${page}&size=${size}`),
  correct: (id, fieldValue, fieldText) =>
    req(`/api/v1/fields/${id}`, {
      method: "PATCH",
      body: JSON.stringify({ fieldValue, fieldText }),
    }),
  approve: (id) => req(`/api/v1/fields/${id}/approve`, { method: "POST" }),
  reject: (id) => req(`/api/v1/fields/${id}/reject`, { method: "POST" }),
  publish: (id) => req(`/api/v1/fields/${id}/publish`, { method: "POST" }),
};
