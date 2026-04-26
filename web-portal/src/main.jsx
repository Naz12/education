import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import { I18nProvider, useI18n } from "./i18n/I18nProvider.jsx";
import {
  buildEditorStateFromChecklistRender,
  DEFAULT_CHECKLIST_SECTION_ID,
  groupChecklistItemsInApiOrder,
  newChecklistItemRowId,
  normalizeGroupKeyForDisplay,
  orderItemsToMatchSectionOrder
} from "./checklistHelpers.js";

/** Use same-origin /api by default so production works behind nginx without extra env wiring. */
const API_BASE =
  (import.meta.env.VITE_API_BASE && String(import.meta.env.VITE_API_BASE).trim()) ||
  "/api";

/** Canonical grade codes (must match backend GradeCodes.ORDERED). */
const CANONICAL_GRADE_CODES = ["KG1", "KG2", "KG3", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"];

/** Checklist targets that support auto-assign on publish (per matching school). */
const CHECKLIST_AUTO_ASSIGN_TARGETS = new Set(["SCHOOL", "DIRECTOR", "TEACHER"]);

/** Assignment targets that require a school for geo and routing. */
const ASSIGNMENT_SCHOOL_TARGETS = new Set(["SCHOOL", "DIRECTOR", "SCHOOL_STAFF"]);

function formatTargetWithGrade(targetType, targetGradeCode) {
  if (!targetType) return "—";
  if (targetType === "TEACHER" && targetGradeCode) {
    return `${targetType} · ${targetGradeCode}`;
  }
  return targetType;
}

function GradeCodeCheckboxes({ label, value, onChange, disabled, codes }) {
  const list = Array.isArray(codes) && codes.length > 0 ? codes : CANONICAL_GRADE_CODES;
  const set = new Set(value || []);
  const toggle = (code) => {
    if (disabled) return;
    const next = new Set(set);
    if (next.has(code)) next.delete(code);
    else next.add(code);
    onChange(list.filter((c) => next.has(c)));
  };
  return (
    <div>
      {label ? <Label>{label}</Label> : null}
      <div style={{ display: "flex", flexWrap: "wrap", gap: 10, marginTop: label ? 8 : 0 }}>
        {list.map((code) => (
          <label
            key={code}
            style={{
              display: "flex",
              alignItems: "center",
              gap: 6,
              fontSize: 13,
              color: t.text,
              cursor: disabled ? "default" : "pointer"
            }}
          >
            <input type="checkbox" checked={set.has(code)} onChange={() => toggle(code)} disabled={disabled} />
            {code.startsWith("KG") ? code.replace(/^KG/, "KG ") : code}
          </label>
        ))}
      </div>
    </div>
  );
}

function jsonHeaders(base) {
  return { ...base, "Content-Type": "application/json" };
}

async function downloadXlsx(url, headers, fallbackName) {
  const res = await fetch(url, { headers });
  if (!res.ok) {
    let details = null;
    try { details = await res.json(); } catch (_) {}
    throw new Error(details?.message || "Download failed.");
  }
  const blob = await res.blob();
  const cd = res.headers.get("content-disposition") || "";
  const match = cd.match(/filename="([^"]+)"/i);
  const filename = match?.[1] || fallbackName;
  const objectUrl = window.URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = objectUrl;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(objectUrl);
}

async function uploadXlsx(url, headers, file) {
  const formData = new FormData();
  formData.append("file", file);
  const reqHeaders = { ...headers };
  delete reqHeaders["Content-Type"];
  const res = await fetch(url, { method: "POST", headers: reqHeaders, body: formData });
  const payload = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(payload?.message || "Import failed.");
  return payload;
}

const t = {
  bg: "#f4f4f5",
  card: "#ffffff",
  line: "#e4e4e7",
  text: "#18181b",
  muted: "#71717a",
  accent: "#18181b",
  accentSoft: "#fafafa",
  radius: 10,
  font: '"DM Sans", "Noto Sans Ethiopic", system-ui, sans-serif',
  shadow: "0 1px 2px rgba(0,0,0,0.04)"
};

const shell = { fontFamily: t.font, background: t.bg, color: t.text, minHeight: "100vh" };
const maxW = { maxWidth: 1120, margin: "0 auto", padding: "24px 20px 48px" };

function Modal({ open, title, onClose, children, wide }) {
  if (!open) return null;
  return (
    <div
      role="presentation"
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0,0,0,0.35)",
        zIndex: 1000,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: 16
      }}
      onClick={onClose}
      onKeyDown={(e) => e.key === "Escape" && onClose()}
    >
      <div
        role="dialog"
        style={{
          background: t.card,
          borderRadius: t.radius + 4,
          width: "100%",
          maxWidth: wide ? 720 : 440,
          maxHeight: "90vh",
          overflow: "auto",
          boxShadow: "0 20px 50px rgba(0,0,0,0.12)"
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            padding: "16px 20px",
            borderBottom: `1px solid ${t.line}`
          }}
        >
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>{title}</h3>
          <GhostButton onClick={onClose} aria-label="Close">×</GhostButton>
        </div>
        <div style={{ padding: 20 }}>{children}</div>
      </div>
    </div>
  );
}

function Card({ children, style }) {
  return (
    <div
      style={{
        background: t.card,
        borderRadius: t.radius,
        border: `1px solid ${t.line}`,
        boxShadow: t.shadow,
        ...style
      }}
    >
      {children}
    </div>
  );
}

function PageHeader({ title, subtitle, action }) {
  return (
    <div style={{ display: "flex", flexWrap: "wrap", alignItems: "flex-start", justifyContent: "space-between", gap: 12, marginBottom: 20 }}>
      <div>
        <h1 style={{ margin: 0, fontSize: 22, fontWeight: 600, letterSpacing: "-0.02em" }}>{title}</h1>
        {subtitle && <p style={{ margin: "6px 0 0", color: t.muted, fontSize: 14, maxWidth: 560 }}>{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}

function PrimaryButton({ children, type, onClick, disabled }) {
  return (
    <button
      type={type || "button"}
      disabled={disabled}
      onClick={onClick}
      style={{
        fontFamily: t.font,
        background: t.accent,
        color: "#fff",
        border: "none",
        borderRadius: t.radius,
        padding: "10px 16px",
        fontSize: 14,
        fontWeight: 500,
        cursor: disabled ? "not-allowed" : "pointer",
        opacity: disabled ? 0.55 : 1
      }}
    >
      {children}
    </button>
  );
}

function GhostButton({ children, onClick, type, ariaLabel, disabled }) {
  return (
    <button
      type={type || "button"}
      onClick={onClick}
      aria-label={ariaLabel}
      disabled={disabled}
      style={{
        fontFamily: t.font,
        background: "transparent",
        color: t.text,
        border: `1px solid ${t.line}`,
        borderRadius: t.radius,
        padding: "8px 14px",
        fontSize: 14,
        cursor: disabled ? "not-allowed" : "pointer",
        opacity: disabled ? 0.55 : 1
      }}
    >
      {children}
    </button>
  );
}

function Input(props) {
  return (
    <input
      {...props}
      style={{
        fontFamily: `"DM Sans", "Noto Sans Ethiopic", system-ui, sans-serif`,
        width: "100%",
        padding: "10px 12px",
        borderRadius: t.radius,
        border: `1px solid ${t.line}`,
        fontSize: 14,
        background: "#fff",
        ...props.style
      }}
    />
  );
}

function Label({ children, htmlFor, style, ...rest }) {
  return (
    <label
      htmlFor={htmlFor}
      style={{ display: "block", fontSize: 12, fontWeight: 500, color: t.muted, marginBottom: 6, ...style }}
      {...rest}
    >
      {children}
    </label>
  );
}

function Alert({ type, children }) {
  const bg = type === "error" ? "#fef2f2" : type === "ok" ? "#f0fdf4" : "#fafafa";
  const c = type === "error" ? "#b91c1c" : type === "ok" ? "#166534" : t.muted;
  return (
    <div style={{ padding: "10px 12px", borderRadius: t.radius, background: bg, color: c, fontSize: 14, marginBottom: 12 }}>
      {children}
    </div>
  );
}

function DataTable({ columns, rows, renderCell, empty }) {
  if (!rows.length) {
    return <p style={{ color: t.muted, fontSize: 14, padding: 16 }}>{empty || "No data"}</p>;
  }
  return (
    <div style={{ overflowX: "auto", border: `1px solid ${t.line}`, borderRadius: t.radius, background: t.card }}>
      <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 14 }}>
        <thead>
          <tr style={{ background: t.accentSoft }}>
            {columns.map((col) => (
              <th
                key={col.key}
                style={{
                  textAlign: "left",
                  padding: "12px 16px",
                  fontWeight: 600,
                  fontSize: 11,
                  textTransform: "uppercase",
                  letterSpacing: "0.06em",
                  color: t.muted,
                  borderBottom: `1px solid ${t.line}`
                }}
              >
                {col.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, ri) => (
            <tr key={row.id || ri} style={{ borderBottom: `1px solid ${t.line}` }}>
              {columns.map((col) => (
                <td key={col.key} style={{ padding: "12px 16px", verticalAlign: "middle" }}>
                  {renderCell ? renderCell(col.key, row) : row[col.key]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function App() {
  const { str, locale, setLocale } = useI18n();
  const [token, setToken] = useState(localStorage.getItem("portal_token") || "");
  const [me, setMe] = useState(null);
  const [activeTab, setActiveTab] = useState("home");
  const [editingChecklistId, setEditingChecklistId] = useState("");
  const [profileMenuOpen, setProfileMenuOpen] = useState(false);
  const [error, setError] = useState("");
  const supervisorLandingRef = useRef(true);

  // Bearer only for GETs — adding Content-Type: application/json on GET triggers non-simple CORS preflight
  // against http://localhost:8080 and can break authenticated list loads in the browser.
  const authHeaders = useMemo(() => (token ? { Authorization: `Bearer ${token}` } : {}), [token]);

  const refreshMe = useCallback(() => {
    if (!token) return;
    fetch(`${API_BASE}/users/me`, { headers: authHeaders })
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error("Session invalid"))))
      .then(setMe)
      .catch(() => {
        setToken("");
        localStorage.removeItem("portal_token");
      });
  }, [token, authHeaders]);

  useEffect(() => {
    refreshMe();
  }, [refreshMe]);

  const isSuperAdmin = me?.roles?.includes("SUPER_ADMIN") || false;
  const isCoordinator = me?.roles?.includes("CLUSTER_COORDINATOR") || false;
  const canAdmin = isSuperAdmin || isCoordinator;
  const isSupervisorPortal = (me?.roles?.includes("SUPERVISOR") || false) && !canAdmin;

  useEffect(() => {
    if (!isSupervisorPortal || !supervisorLandingRef.current) return;
    supervisorLandingRef.current = false;
    setActiveTab("my-assignments");
  }, [isSupervisorPortal]);

  const onLogin = (newToken) => {
    setToken(newToken);
    localStorage.setItem("portal_token", newToken);
  };

  const onLogout = () => {
    setToken("");
    setMe(null);
    localStorage.removeItem("portal_token");
  };

  if (!token) {
    return <LoginPage onLogin={onLogin} error={error} setError={setError} />;
  }
  if (!me) {
    return (
      <div style={{ ...shell, display: "flex", alignItems: "center", justifyContent: "center" }}>
        <Card style={{ padding: 28 }}>
          <p style={{ margin: 0, color: t.muted, fontSize: 14 }}>{str.loadingSession}</p>
        </Card>
      </div>
    );
  }

  const tabs = [
    { id: "home", label: str.tabHome },
    ...(isSupervisorPortal
      ? [
          { id: "my-assignments", label: str.tabMyAssignments },
          { id: "reports", label: str.tabReports }
        ]
      : []),
    ...(canAdmin
      ? [
          { id: "users", label: str.tabUsers },
          ...(isSuperAdmin ? [{ id: "locations", label: str.tabLocations }] : []),
          { id: "checklists", label: str.tabChecklists },
          { id: "checklist-items", label: str.tabChecklistItems },
          { id: "assignments", label: str.tabAssignments },
          { id: "schools", label: str.tabSchools },
          { id: "school-stuff", label: str.tabSchoolStuff },
          { id: "supervision", label: str.tabActivity },
          { id: "reports", label: str.tabReports }
        ]
      : [])
  ];

  return (
    <div style={shell}>
      <header
        style={{
          background: t.card,
          borderBottom: `1px solid ${t.line}`,
          position: "sticky",
          top: 0,
          zIndex: 50
        }}
      >
        <div style={{ ...maxW, paddingTop: 16, paddingBottom: 16, display: "flex", alignItems: "center", justifyContent: "space-between", gap: 16, position: "relative" }}>
          <div>
            <div style={{ fontSize: 13, fontWeight: 600, letterSpacing: "-0.02em" }}>{str.appBrand}</div>
            <div style={{ fontSize: 12, color: t.muted, marginTop: 2 }}>{me.fullName} · {me.roles.join(", ")}</div>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: t.muted }}>
              <span>{str.language}</span>
              <select
                value={locale}
                onChange={(e) => setLocale(e.target.value)}
                style={{
                  fontFamily: `"DM Sans", "Noto Sans Ethiopic", system-ui, sans-serif`,
                  padding: "6px 10px",
                  borderRadius: t.radius,
                  border: `1px solid ${t.line}`,
                  fontSize: 13,
                  background: t.card,
                  color: t.text
                }}
              >
                <option value="en">{str.english}</option>
                <option value="am">{str.amharic}</option>
              </select>
            </label>
            <button
              type="button"
              onClick={() => setProfileMenuOpen((v) => !v)}
              style={{
                fontFamily: t.font,
                background: "transparent",
                color: t.text,
                border: `1px solid ${t.line}`,
                borderRadius: 999,
                padding: "8px 14px",
                fontSize: 13,
                fontWeight: 500,
                cursor: "pointer"
              }}
            >
              {str.profileMenu}
            </button>
            {profileMenuOpen && (
              <div
                role="menu"
                style={{
                  position: "absolute",
                  right: 20,
                  top: 68,
                  background: t.card,
                  border: `1px solid ${t.line}`,
                  borderRadius: t.radius,
                  boxShadow: "0 20px 50px rgba(0,0,0,0.12)",
                  padding: 8,
                  zIndex: 1001,
                  minWidth: 180
                }}
              >
                <button
                  type="button"
                  onClick={() => {
                    setActiveTab("profile");
                    setProfileMenuOpen(false);
                  }}
                  style={{
                    width: "100%",
                    textAlign: "left",
                    padding: "10px 12px",
                    border: "none",
                    background: "transparent",
                    fontFamily: t.font,
                    cursor: "pointer",
                    color: t.text,
                    borderRadius: 8
                  }}
                >
                  {str.profile}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setProfileMenuOpen(false);
                    onLogout();
                  }}
                  style={{
                    width: "100%",
                    textAlign: "left",
                    padding: "10px 12px",
                    border: "none",
                    background: "transparent",
                    fontFamily: t.font,
                    cursor: "pointer",
                    color: t.text,
                    borderRadius: 8
                  }}
                >
                  {str.signOut}
                </button>
              </div>
            )}
          </div>
        </div>
        <nav style={{ ...maxW, paddingTop: 0, paddingBottom: 12, display: "flex", gap: 6, flexWrap: "wrap" }}>
          {tabs.map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveTab(tab.id)}
              style={{
                fontFamily: t.font,
                border: "none",
                borderRadius: 999,
                padding: "8px 14px",
                fontSize: 13,
                fontWeight: 500,
                cursor: "pointer",
                background: activeTab === tab.id ? t.accent : "transparent",
                color: activeTab === tab.id ? "#fff" : t.text
              }}
            >
              {tab.label}
            </button>
          ))}
        </nav>
      </header>

      <main style={maxW}>
        {activeTab === "home" && <HomePage canAdmin={canAdmin} isSupervisorPortal={isSupervisorPortal} />}
        {activeTab === "my-assignments" && isSupervisorPortal && <SupervisorMyAssignmentsPage headers={authHeaders} />}
        {activeTab === "profile" && <ProfilePage headers={authHeaders} me={me} onProfileUpdated={refreshMe} />}
        {activeTab === "users" && canAdmin && <UsersPage headers={authHeaders} isSuperAdmin={isSuperAdmin} />}
        {activeTab === "locations" && isSuperAdmin && <GeographyLocationsPage headers={authHeaders} />}
        {activeTab === "checklists" && canAdmin && (
          <ChecklistsPage
            headers={authHeaders}
            isSuperAdmin={isSuperAdmin}
            onOpenChecklistItems={(checklistId) => {
              setEditingChecklistId(checklistId || "");
              setActiveTab("checklist-items");
            }}
          />
        )}
        {activeTab === "checklist-items" && canAdmin && (
          <ChecklistItemsPage headers={authHeaders} initialChecklistId={editingChecklistId} />
        )}
        {activeTab === "assignments" && canAdmin && <AssignmentsPage headers={authHeaders} />}
        {activeTab === "schools" && canAdmin && <SchoolsPage headers={authHeaders} />}
        {activeTab === "school-stuff" && canAdmin && <SchoolStuffPage headers={authHeaders} isSuperAdmin={isSuperAdmin} />}
        {activeTab === "supervision" && canAdmin && <SupervisionActivityPage headers={authHeaders} />}
        {activeTab === "reports" && (canAdmin || isSupervisorPortal) && <ReportsPage headers={authHeaders} />}
      </main>
    </div>
  );
}

function LoginPage({ onLogin, error, setError }) {
  const { str, locale, setLocale } = useI18n();
  const [username, setUsername] = useState("superadmin");
  const [password, setPassword] = useState("Admin@12345");
  const [loading, setLoading] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError("");
    try {
      const res = await fetch(`${API_BASE}/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password })
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        const msg = data.message || data.error || res.statusText;
        throw new Error(
          typeof msg === "string" && msg.trim() ? msg : "Invalid username or password"
        );
      }
      onLogin(data.accessToken);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ ...shell, display: "flex", alignItems: "center", justifyContent: "center", padding: 20 }}>
      <Card style={{ width: "100%", maxWidth: 400, padding: 28 }}>
        <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 8 }}>
          <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 12, color: t.muted }}>
            <span>{str.language}</span>
            <select
              value={locale}
              onChange={(e) => setLocale(e.target.value)}
              style={{
                fontFamily: t.font,
                padding: "6px 10px",
                borderRadius: t.radius,
                border: `1px solid ${t.line}`,
                fontSize: 13,
                background: t.card
              }}
            >
              <option value="en">{str.english}</option>
              <option value="am">{str.amharic}</option>
            </select>
          </label>
        </div>
        <h1 style={{ margin: "0 0 4px", fontSize: 22, fontWeight: 600 }}>{str.loginTitle}</h1>
        <p style={{ margin: "0 0 24px", color: t.muted, fontSize: 14 }}>{str.loginSubtitle}</p>
        <form onSubmit={submit}>
          <Label>{str.username}</Label>
          <Input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" style={{ marginBottom: 14 }} />
          <Label>{str.password}</Label>
          <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" style={{ marginBottom: 20 }} />
          <PrimaryButton type="submit" disabled={loading} style={{ width: "100%" }}>
            {loading ? str.signingIn : str.continue}
          </PrimaryButton>
        </form>
        {error && <Alert type="error">{error}</Alert>}
        <p style={{ fontSize: 12, color: t.muted, marginTop: 16 }}>{str.demoAccounts}</p>
      </Card>
    </div>
  );
}

function HomePage({ canAdmin, isSupervisorPortal }) {
  const { str } = useI18n();
  return (
    <>
      <PageHeader title={str.homeTitle} subtitle={str.homeSubtitleAdmin} />
      <Card style={{ padding: 24 }}>
        <p style={{ margin: 0, fontSize: 15, lineHeight: 1.6, color: t.muted }}>
          {canAdmin
            ? str.homeBodyAdmin
            : isSupervisorPortal
              ? str.homeBodySupervisor
              : str.homeBodyUser}
        </p>
      </Card>
    </>
  );
}

function ProfilePage({ headers, me, onProfileUpdated }) {
  const { str } = useI18n();
  const [status, setStatus] = useState(null);
  const [form, setForm] = useState({
    fullName: me.fullName || "",
    email: me.email || "",
    city: me.city || "",
    subCity: me.subCity || "",
    wereda: me.wereda || ""
  });
  const [pw, setPw] = useState({ current: "", next: "", next2: "" });
  const [msg, setMsg] = useState(null);

  const loadStatus = useCallback(() => {
    fetch(`${API_BASE}/users/me/status`, { headers })
      .then((r) => (r.ok ? r.json() : null))
      .then(setStatus);
  }, [headers]);

  useEffect(() => {
    setForm({
      fullName: me.fullName || "",
      email: me.email || "",
      city: me.city || "",
      subCity: me.subCity || "",
      wereda: me.wereda || ""
    });
  }, [me]);

  useEffect(() => {
    loadStatus();
  }, [loadStatus]);

  const saveProfile = async (e) => {
    e.preventDefault();
    setMsg(null);
    const res = await fetch(`${API_BASE}/users/me`, {
      method: "PATCH",
      headers: jsonHeaders(headers),
      body: JSON.stringify(form)
    });
    if (!res.ok) {
      setMsg({ type: "error", text: "Could not save profile." });
      return;
    }
    setMsg({ type: "ok", text: "Profile saved." });
    onProfileUpdated();
  };

  const savePassword = async (e) => {
    e.preventDefault();
    setMsg(null);
    if (pw.next !== pw.next2) {
      setMsg({ type: "error", text: "New passwords do not match." });
      return;
    }
    const res = await fetch(`${API_BASE}/users/me/change-password`, {
      method: "POST",
      headers: jsonHeaders(headers),
      body: JSON.stringify({ currentPassword: pw.current, newPassword: pw.next })
    });
    if (!res.ok) {
      const j = await res.json().catch(() => ({}));
      setMsg({ type: "error", text: j.message || "Password change failed." });
      return;
    }
    setMsg({ type: "ok", text: "Password updated." });
    setPw({ current: "", next: "", next2: "" });
  };

  return (
    <>
      <PageHeader
        title={str.profileTitle}
        subtitle={str.profileSubtitle}
        action={<GhostButton onClick={loadStatus}>{str.refreshStatus}</GhostButton>}
      />
      {msg && <Alert type={msg.type}>{msg.text}</Alert>}

      <div style={{ display: "grid", gap: 20, gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))" }}>
        <Card style={{ padding: 20 }}>
          <h2 style={{ margin: "0 0 16px", fontSize: 15, fontWeight: 600 }}>{str.account}</h2>
          <p style={{ margin: "0 0 8px", fontSize: 13, color: t.muted }}>{str.usernameLabel}</p>
          <p style={{ margin: "0 0 16px", fontWeight: 500 }}>{me.username}</p>
          <form onSubmit={saveProfile}>
            <Label>{str.fullName}</Label>
            <Input value={form.fullName} onChange={(e) => setForm((p) => ({ ...p, fullName: e.target.value }))} required style={{ marginBottom: 12 }} />
            <Label>{str.email}</Label>
            <Input value={form.email} onChange={(e) => setForm((p) => ({ ...p, email: e.target.value }))} type="email" style={{ marginBottom: 12 }} />
            <Label>{str.city}</Label>
            <Input value={form.city} onChange={(e) => setForm((p) => ({ ...p, city: e.target.value }))} style={{ marginBottom: 12 }} />
            <Label>{str.subCity}</Label>
            <Input value={form.subCity} onChange={(e) => setForm((p) => ({ ...p, subCity: e.target.value }))} style={{ marginBottom: 12 }} />
            <Label>{str.wereda}</Label>
            <Input value={form.wereda} onChange={(e) => setForm((p) => ({ ...p, wereda: e.target.value }))} style={{ marginBottom: 16 }} />
            <PrimaryButton type="submit">{str.saveProfile}</PrimaryButton>
          </form>
        </Card>

        <Card style={{ padding: 20 }}>
          <h2 style={{ margin: "0 0 16px", fontSize: 15, fontWeight: 600 }}>{str.passwordSection}</h2>
          <form onSubmit={savePassword}>
            <Label>{str.currentPassword}</Label>
            <Input type="password" value={pw.current} onChange={(e) => setPw((p) => ({ ...p, current: e.target.value }))} required style={{ marginBottom: 12 }} />
            <Label>{str.newPassword}</Label>
            <Input type="password" value={pw.next} onChange={(e) => setPw((p) => ({ ...p, next: e.target.value }))} required minLength={8} style={{ marginBottom: 12 }} />
            <Label>{str.confirmPassword}</Label>
            <Input type="password" value={pw.next2} onChange={(e) => setPw((p) => ({ ...p, next2: e.target.value }))} required style={{ marginBottom: 16 }} />
            <PrimaryButton type="submit">{str.updatePassword}</PrimaryButton>
          </form>
        </Card>

        <Card style={{ padding: 20, gridColumn: "1 / -1" }}>
          <h2 style={{ margin: "0 0 16px", fontSize: 15, fontWeight: 600 }}>{str.yourStatus}</h2>
          {!status && <p style={{ color: t.muted, fontSize: 14 }}>{str.loadingEllipsis}</p>}
          {status?.supervisorWorkload && (
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(140px, 1fr))", gap: 12 }}>
              {[
                ["Assignments", status.supervisorWorkload.totalAssignments],
                ["Completed", status.supervisorWorkload.completedAssignments],
                ["Pending", status.supervisorWorkload.pendingAssignments],
                ["In progress", status.supervisorWorkload.inProgressAssignments],
                ["Overdue", status.supervisorWorkload.overdueAssignments],
                ["Visits done", status.supervisorWorkload.visitsCompleted]
              ].map(([k, v]) => (
                <div key={k} style={{ padding: 14, background: t.accentSoft, borderRadius: t.radius, border: `1px solid ${t.line}` }}>
                  <div style={{ fontSize: 11, color: t.muted, textTransform: "uppercase", letterSpacing: "0.05em" }}>{k}</div>
                  <div style={{ fontSize: 22, fontWeight: 600, marginTop: 4 }}>{v}</div>
                </div>
              ))}
            </div>
          )}
          {status?.coordinatorScope && (
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(140px, 1fr))", gap: 12 }}>
              {[
                ["Supervisors", status.coordinatorScope.supervisorsCount],
                ["Schools", status.coordinatorScope.schoolsCount],
                ["Teachers", status.coordinatorScope.teachersCount],
                ["Active assignments", status.coordinatorScope.activeAssignmentsCount]
              ].map(([k, v]) => (
                <div key={k} style={{ padding: 14, background: t.accentSoft, borderRadius: t.radius, border: `1px solid ${t.line}` }}>
                  <div style={{ fontSize: 11, color: t.muted, textTransform: "uppercase", letterSpacing: "0.05em" }}>{k}</div>
                  <div style={{ fontSize: 22, fontWeight: 600, marginTop: 4 }}>{v}</div>
                </div>
              ))}
            </div>
          )}
          {status?.adminScope && (
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(140px, 1fr))", gap: 12 }}>
              {[
                ["Users", status.adminScope.usersCount],
                ["Schools", status.adminScope.schoolsCount],
                ["Supervisors", status.adminScope.supervisorsCount],
                ["Coordinators", status.adminScope.coordinatorsCount]
              ].map(([k, v]) => (
                <div key={k} style={{ padding: 14, background: t.accentSoft, borderRadius: t.radius, border: `1px solid ${t.line}` }}>
                  <div style={{ fontSize: 11, color: t.muted, textTransform: "uppercase", letterSpacing: "0.05em" }}>{k}</div>
                  <div style={{ fontSize: 22, fontWeight: 600, marginTop: 4 }}>{v}</div>
                </div>
              ))}
            </div>
          )}
          {status && !status.supervisorWorkload && !status.coordinatorScope && !status.adminScope && (
            <p style={{ color: t.muted }}>No extended metrics for your role.</p>
          )}
        </Card>
      </div>
    </>
  );
}

function LocationWeredaPicker({ headers, cityId, subcityId, weredaId, onChange, disabled }) {
  const { str } = useI18n();
  const [cities, setCities] = useState([]);
  const [subcities, setSubcities] = useState([]);
  const [weredas, setWeredas] = useState([]);
  useEffect(() => {
    fetch(`${API_BASE}/admin/geography/cities`, { headers })
      .then((r) => (r.ok ? r.json() : []))
      .then(setCities)
      .catch(() => setCities([]));
  }, [headers]);
  useEffect(() => {
    if (!cityId) {
      setSubcities([]);
      return;
    }
    fetch(`${API_BASE}/admin/geography/subcities?cityId=${encodeURIComponent(cityId)}`, { headers })
      .then((r) => (r.ok ? r.json() : []))
      .then(setSubcities)
      .catch(() => setSubcities([]));
  }, [headers, cityId]);
  useEffect(() => {
    if (!subcityId) {
      setWeredas([]);
      return;
    }
    fetch(`${API_BASE}/admin/geography/weredas?subcityId=${encodeURIComponent(subcityId)}`, { headers })
      .then((r) => (r.ok ? r.json() : []))
      .then(setWeredas)
      .catch(() => setWeredas([]));
  }, [headers, subcityId]);
  const selStyle = { fontFamily: t.font, width: "100%", padding: "10px 12px", borderRadius: t.radius, border: `1px solid ${t.line}` };
  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8 }}>
      <div>
        <Label>{str.city}</Label>
        <select
          value={cityId}
          disabled={disabled}
          onChange={(e) => onChange({ cityId: e.target.value, subcityId: "", weredaId: "" })}
          style={selStyle}
        >
          <option value="">{str.selectEllipsis}</option>
          {cities.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>
      </div>
      <div>
        <Label>{str.subCity}</Label>
        <select
          value={subcityId}
          disabled={disabled || !cityId}
          onChange={(e) => onChange({ cityId, subcityId: e.target.value, weredaId: "" })}
          style={selStyle}
        >
          <option value="">{str.selectEllipsis}</option>
          {subcities.map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </select>
      </div>
      <div>
        <Label>{str.wereda}</Label>
        <select
          value={weredaId}
          disabled={disabled || !subcityId}
          onChange={(e) => onChange({ cityId, subcityId, weredaId: e.target.value })}
          style={selStyle}
        >
          <option value="">{str.selectEllipsis}</option>
          {weredas.map((w) => (
            <option key={w.id} value={w.id}>{w.name}</option>
          ))}
        </select>
      </div>
    </div>
  );
}

function GeographyLocationsPage({ headers }) {
  const { str } = useI18n();
  const [cities, setCities] = useState([]);
  const [subcities, setSubcities] = useState([]);
  const [weredas, setWeredas] = useState([]);
  const [cityPick, setCityPick] = useState("");
  const [subPick, setSubPick] = useState("");
  const [message, setMessage] = useState(null);
  const [busy, setBusy] = useState(false);
  const [nameModal, setNameModal] = useState({ open: false, kind: "", title: "", id: "", name: "", extraId: "" });

  const loadCities = () =>
    fetch(`${API_BASE}/admin/geography/cities`, { headers }).then((r) => r.json()).then(setCities);
  const loadSubcities = (cid) =>
    fetch(`${API_BASE}/admin/geography/subcities?cityId=${encodeURIComponent(cid)}`, { headers }).then((r) => r.json()).then(setSubcities);
  const loadWeredas = (sid) =>
    fetch(`${API_BASE}/admin/geography/weredas?subcityId=${encodeURIComponent(sid)}`, { headers }).then((r) => r.json()).then(setWeredas);

  useEffect(() => {
    loadCities().catch(() => {});
  }, [headers]);
  useEffect(() => {
    if (!cityPick) {
      setSubcities([]);
      setSubPick("");
      return;
    }
    loadSubcities(cityPick).catch(() => {});
  }, [headers, cityPick]);
  useEffect(() => {
    if (!subPick) {
      setWeredas([]);
      return;
    }
    loadWeredas(subPick).catch(() => {});
  }, [headers, subPick]);

  const openAdd = (kind) => {
    if (kind === "subcity" && !cityPick) {
      setMessage({ type: "error", text: "Select a city first." });
      return;
    }
    if (kind === "wereda" && !subPick) {
      setMessage({ type: "error", text: "Select a sub city first." });
      return;
    }
    setNameModal({ open: true, kind, title: `Add ${kind === "city" ? "city" : kind === "subcity" ? "sub city" : "wereda"}`, id: "", name: "", extraId: "" });
  };
  const openEdit = (kind, row) => {
    setNameModal({
      open: true,
      kind,
      title: `Edit ${kind === "city" ? "city" : kind === "subcity" ? "sub city" : "wereda"}`,
      id: row.id,
      name: row.name || "",
      extraId: ""
    });
  };

  const saveNameModal = async (e) => {
    e.preventDefault();
    const { kind, id, name } = nameModal;
    const trimmed = name.trim();
    if (!trimmed) return;
    setBusy(true);
    setMessage(null);
    try {
      if (kind === "city") {
        if (id) {
          const res = await fetch(`${API_BASE}/admin/geography/cities/${id}`, {
            method: "PATCH",
            headers: jsonHeaders(headers),
            body: JSON.stringify({ name: trimmed })
          });
          if (!res.ok) throw new Error((await res.json().catch(() => ({}))).message || "Update failed");
        } else {
          const res = await fetch(`${API_BASE}/admin/geography/cities`, {
            method: "POST",
            headers: jsonHeaders(headers),
            body: JSON.stringify({ name: trimmed })
          });
          if (!res.ok) throw new Error((await res.json().catch(() => ({}))).message || "Create failed");
        }
      } else if (kind === "subcity") {
        if (id) {
          const res = await fetch(`${API_BASE}/admin/geography/subcities/${id}`, {
            method: "PATCH",
            headers: jsonHeaders(headers),
            body: JSON.stringify({ name: trimmed })
          });
          if (!res.ok) throw new Error((await res.json().catch(() => ({}))).message || "Update failed");
        } else {
          const res = await fetch(`${API_BASE}/admin/geography/subcities`, {
            method: "POST",
            headers: jsonHeaders(headers),
            body: JSON.stringify({ cityId: cityPick, name: trimmed })
          });
          if (!res.ok) throw new Error((await res.json().catch(() => ({}))).message || "Create failed");
        }
        await loadSubcities(cityPick);
      } else {
        if (id) {
          const res = await fetch(`${API_BASE}/admin/geography/weredas/${id}`, {
            method: "PATCH",
            headers: jsonHeaders(headers),
            body: JSON.stringify({ name: trimmed })
          });
          if (!res.ok) throw new Error((await res.json().catch(() => ({}))).message || "Update failed");
        } else {
          const res = await fetch(`${API_BASE}/admin/geography/weredas`, {
            method: "POST",
            headers: jsonHeaders(headers),
            body: JSON.stringify({ subcityId: subPick, name: trimmed })
          });
          if (!res.ok) throw new Error((await res.json().catch(() => ({}))).message || "Create failed");
        }
        await loadWeredas(subPick);
      }
      setNameModal((m) => ({ ...m, open: false }));
      setMessage({ type: "ok", text: "Saved." });
      await loadCities();
      if (cityPick) await loadSubcities(cityPick);
      if (subPick) await loadWeredas(subPick);
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setBusy(false);
    }
  };

  const confirmDelete = async (kind, id) => {
    if (!window.confirm("Delete this record? Child areas or linked users/clusters may block deletion.")) return;
    setBusy(true);
    setMessage(null);
    try {
      const path =
        kind === "city"
          ? `${API_BASE}/admin/geography/cities/${id}`
          : kind === "subcity"
            ? `${API_BASE}/admin/geography/subcities/${id}`
            : `${API_BASE}/admin/geography/weredas/${id}`;
      const res = await fetch(path, { method: "DELETE", headers });
      if (!res.ok) throw new Error((await res.json().catch(() => ({}))).message || "Delete failed");
      setMessage({ type: "ok", text: "Deleted." });
      await loadCities();
      if (cityPick) await loadSubcities(cityPick);
      if (subPick) await loadWeredas(subPick);
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        title={str.locationsTitle}
        subtitle={str.locationsSubtitle}
      />
      {message && <Alert type={message.type}>{message.text}</Alert>}

      <section style={{ marginBottom: 28 }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 12 }}>
          <h2 style={{ fontSize: 14, fontWeight: 600, margin: 0, color: t.muted }}>{str.cities}</h2>
          <GhostButton onClick={() => openAdd("city")} disabled={busy}>{str.addCity}</GhostButton>
        </div>
        <DataTable
          columns={[
            { key: "name", label: str.name },
            { key: "actions", label: str.actions }
          ]}
          rows={cities}
          empty={str.noCities}
          renderCell={(key, row) => {
            if (key === "actions") {
              return (
                <div style={{ display: "flex", gap: 8 }}>
                  <GhostButton type="button" disabled={busy} onClick={() => openEdit("city", row)}>{str.edit}</GhostButton>
                  <GhostButton type="button" disabled={busy} onClick={() => confirmDelete("city", row.id)}>{str.delete}</GhostButton>
                </div>
              );
            }
            return row[key];
          }}
        />
      </section>

      <section style={{ marginBottom: 28 }}>
        <h2 style={{ fontSize: 14, fontWeight: 600, margin: "0 0 8px", color: t.muted }}>{str.subCities}</h2>
        <div style={{ marginBottom: 12 }}>
          <Label>{str.city}</Label>
          <select
            value={cityPick}
            onChange={(e) => { setCityPick(e.target.value); setSubPick(""); }}
            style={{ fontFamily: t.font, minWidth: 280, padding: "10px 12px", borderRadius: t.radius, border: `1px solid ${t.line}` }}
          >
            <option value="">{str.selectCity}</option>
            {cities.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
          <GhostButton onClick={() => openAdd("subcity")} disabled={busy || !cityPick} style={{ marginLeft: 8 }}>{str.addSubCity}</GhostButton>
        </div>
        <DataTable
          columns={[
            { key: "name", label: str.name },
            { key: "actions", label: str.actions }
          ]}
          rows={subcities}
          empty={cityPick ? str.noSubcitiesInCity : str.selectACity}
          renderCell={(key, row) => {
            if (key === "actions") {
              return (
                <div style={{ display: "flex", gap: 8 }}>
                  <GhostButton type="button" disabled={busy} onClick={() => openEdit("subcity", row)}>{str.edit}</GhostButton>
                  <GhostButton type="button" disabled={busy} onClick={() => confirmDelete("subcity", row.id)}>{str.delete}</GhostButton>
                </div>
              );
            }
            return row[key];
          }}
        />
      </section>

      <section>
        <h2 style={{ fontSize: 14, fontWeight: 600, margin: "0 0 8px", color: t.muted }}>{str.weredas}</h2>
        <div style={{ marginBottom: 12 }}>
          <Label>{str.subCity}</Label>
          <select
            value={subPick}
            onChange={(e) => setSubPick(e.target.value)}
            disabled={!cityPick}
            style={{ fontFamily: t.font, minWidth: 280, padding: "10px 12px", borderRadius: t.radius, border: `1px solid ${t.line}` }}
          >
            <option value="">{str.selectSubCity}</option>
            {subcities.map((s) => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>
          <GhostButton onClick={() => openAdd("wereda")} disabled={busy || !subPick} style={{ marginLeft: 8 }}>{str.addWereda}</GhostButton>
        </div>
        <DataTable
          columns={[
            { key: "name", label: str.name },
            { key: "actions", label: str.actions }
          ]}
          rows={weredas}
          empty={subPick ? str.noWeredas : str.selectASubCity}
          renderCell={(key, row) => {
            if (key === "actions") {
              return (
                <div style={{ display: "flex", gap: 8 }}>
                  <GhostButton type="button" disabled={busy} onClick={() => openEdit("wereda", row)}>{str.edit}</GhostButton>
                  <GhostButton type="button" disabled={busy} onClick={() => confirmDelete("wereda", row.id)}>{str.delete}</GhostButton>
                </div>
              );
            }
            return row[key];
          }}
        />
      </section>

      <Modal open={nameModal.open} onClose={() => (!busy ? setNameModal((m) => ({ ...m, open: false })) : null)} title={nameModal.title}>
        <form onSubmit={saveNameModal} style={{ display: "grid", gap: 12 }}>
          <div>
            <Label>{str.name}</Label>
            <Input value={nameModal.name} onChange={(e) => setNameModal((m) => ({ ...m, name: e.target.value }))} required />
          </div>
          <PrimaryButton type="submit" disabled={busy}>{busy ? "Saving…" : "Save"}</PrimaryButton>
        </form>
      </Modal>
    </>
  );
}

function UsersPage({ headers, isSuperAdmin }) {
  const { str } = useI18n();
  const [coordinators, setCoordinators] = useState([]);
  const [supervisors, setSupervisors] = useState([]);
  const [coordOpen, setCoordOpen] = useState(false);
  const [supOpen, setSupOpen] = useState(false);
  const [coordForm, setCoordForm] = useState({
    fullName: "",
    username: "",
    password: "",
    email: "",
    phone: "",
    cityId: "",
    subcityId: "",
    weredaId: ""
  });
  const [supervisorForm, setSupervisorForm] = useState({
    fullName: "",
    username: "",
    password: "",
    email: "",
    phone: "",
    cityId: "",
    subcityId: "",
    weredaId: "",
    supervisedGradeCodes: []
  });
  const [editUserOpen, setEditUserOpen] = useState(false);
  const [editUserBusy, setEditUserBusy] = useState(false);
  const [editUserForm, setEditUserForm] = useState({
    id: "",
    kind: "supervisor",
    fullName: "",
    email: "",
    phone: "",
    cityId: "",
    subcityId: "",
    weredaId: "",
    supervisedGradeCodes: []
  });
  const [deleteUserOpen, setDeleteUserOpen] = useState(false);
  const [deleteUserBusy, setDeleteUserBusy] = useState(false);
  const [deleteUserForm, setDeleteUserForm] = useState({ id: "", kind: "supervisor" });
  const [message, setMessage] = useState(null);
  const importRef = useRef(null);

  const load = () => {
    if (isSuperAdmin) {
      fetch(`${API_BASE}/users/cluster-coordinators`, { headers }).then((r) => r.json()).then(setCoordinators);
    }
    fetch(`${API_BASE}/users/supervisors`, { headers }).then((r) => r.json()).then(setSupervisors);
  };
  useEffect(() => {
    load();
  }, [isSuperAdmin, headers]);

  const createCoordinator = async (e) => {
    e.preventDefault();
    if (!coordForm.weredaId) {
      setMessage({ type: "error", text: "Select city, sub city, and wereda." });
      return;
    }
    const body = {
      fullName: coordForm.fullName,
      username: coordForm.username,
      password: coordForm.password,
      email: coordForm.email || null,
      phone: coordForm.phone || null,
      weredaId: coordForm.weredaId
    };
    const res = await fetch(`${API_BASE}/users/cluster-coordinators`, { method: "POST", headers: jsonHeaders(headers), body: JSON.stringify(body) });
    if (!res.ok) {
      let details = null;
      try {
        details = await res.json();
      } catch (_) {}
      setMessage({ type: "error", text: details?.message || "Failed to create coordinator." });
      return;
    }
    setMessage({ type: "ok", text: "Coordinator created." });
    setCoordForm({
      fullName: "",
      username: "",
      password: "",
      email: "",
      phone: "",
      cityId: "",
      subcityId: "",
      weredaId: ""
    });
    setCoordOpen(false);
    load();
  };

  const createSupervisor = async (e) => {
    e.preventDefault();
    if (!supervisorForm.supervisedGradeCodes || supervisorForm.supervisedGradeCodes.length === 0) {
      setMessage({ type: "error", text: "Select at least one grade this supervisor can supervise." });
      return;
    }
    if (isSuperAdmin && !supervisorForm.weredaId) {
      setMessage({ type: "error", text: "Select city, sub city, and wereda for the supervisor." });
      return;
    }
    const base = {
      fullName: supervisorForm.fullName,
      username: supervisorForm.username,
      password: supervisorForm.password,
      email: supervisorForm.email || null,
      phone: supervisorForm.phone || null,
      supervisedGradeCodes: supervisorForm.supervisedGradeCodes
    };
    const body = isSuperAdmin ? { ...base, weredaId: supervisorForm.weredaId } : base;
    const res = await fetch(`${API_BASE}/users/supervisors`, { method: "POST", headers: jsonHeaders(headers), body: JSON.stringify(body) });
    if (!res.ok) {
      let details = null;
      try {
        details = await res.json();
      } catch (_) {}
      setMessage({ type: "error", text: details?.message || "Failed to create supervisor." });
      return;
    }
    setMessage({ type: "ok", text: "Supervisor created." });
    setSupervisorForm({
      fullName: "",
      username: "",
      password: "",
      email: "",
      phone: "",
      cityId: "",
      subcityId: "",
      weredaId: "",
      supervisedGradeCodes: []
    });
    setSupOpen(false);
    load();
  };

  const openEditUser = (row, kind) => {
    const supGrades =
      kind === "supervisor"
        ? Array.isArray(row.supervisedGradeCodes) && row.supervisedGradeCodes.length > 0
          ? row.supervisedGradeCodes
          : [...CANONICAL_GRADE_CODES]
        : [];
    setEditUserForm({
      id: row.id || "",
      kind,
      fullName: row.fullName || "",
      email: row.email || "",
      phone: row.phone || "",
      cityId: row.cityId || "",
      subcityId: row.subcityId || "",
      weredaId: row.weredaId || "",
      supervisedGradeCodes: supGrades
    });
    setEditUserOpen(true);
  };

  const saveEditUser = async (e) => {
    e.preventDefault();
    if (!editUserForm.id) return;
    if (editUserForm.kind === "supervisor" && (!editUserForm.supervisedGradeCodes || editUserForm.supervisedGradeCodes.length === 0)) {
      setMessage({ type: "error", text: "Select at least one supervised grade." });
      return;
    }
    setEditUserBusy(true);
    try {
      const endpoint = editUserForm.kind === "coordinator"
        ? `${API_BASE}/users/cluster-coordinators/${editUserForm.id}`
        : `${API_BASE}/users/supervisors/${editUserForm.id}`;
      const body = {
        fullName: editUserForm.fullName,
        email: editUserForm.email || null,
        phone: editUserForm.phone || null,
        ...(isSuperAdmin && editUserForm.weredaId
          ? { weredaId: editUserForm.weredaId }
          : {}),
        ...(editUserForm.kind === "supervisor"
          ? { supervisedGradeCodes: editUserForm.supervisedGradeCodes }
          : {})
      };
      const res = await fetch(endpoint, { method: "PATCH", headers: jsonHeaders(headers), body: JSON.stringify(body) });
      if (!res.ok) {
        let details = null;
        try { details = await res.json(); } catch (_) {}
        throw new Error(details?.message || "Could not update user.");
      }
      setEditUserOpen(false);
      setMessage({ type: "ok", text: "User updated." });
      await load();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setEditUserBusy(false);
    }
  };

  const openDeleteUser = (id, kind) => {
    setDeleteUserForm({ id, kind });
    setDeleteUserOpen(true);
  };

  const confirmDeleteUser = async (e) => {
    e.preventDefault();
    if (!deleteUserForm.id) return;
    setDeleteUserBusy(true);
    try {
      const endpoint = deleteUserForm.kind === "coordinator"
        ? `${API_BASE}/users/cluster-coordinators/${deleteUserForm.id}`
        : `${API_BASE}/users/supervisors/${deleteUserForm.id}`;
      const res = await fetch(endpoint, { method: "DELETE", headers });
      if (!res.ok) {
        let details = null;
        try { details = await res.json(); } catch (_) {}
        throw new Error(details?.message || "Could not delete user.");
      }
      setDeleteUserOpen(false);
      setMessage({ type: "ok", text: "User deleted." });
      await load();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setDeleteUserBusy(false);
      setDeleteUserForm({ id: "", kind: "supervisor" });
    }
  };

  const downloadUsersTemplate = async () => {
    try {
      await downloadXlsx(`${API_BASE}/users/template`, headers, "users-template.xlsx");
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    }
  };

  const exportUsers = async () => {
    try {
      await downloadXlsx(`${API_BASE}/users/export`, headers, "users-export.xlsx");
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    }
  };

  const importUsersFile = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const result = await uploadXlsx(`${API_BASE}/users/import`, headers, file);
      const failed = Number(result?.failed || 0);
      const created = Number(result?.created || 0);
      setMessage({ type: failed > 0 ? "error" : "ok", text: `Import finished. Created: ${created}, Failed: ${failed}.` });
      await load();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      e.target.value = "";
    }
  };

  return (
    <>
      <PageHeader
        title={str.usersTitle}
        subtitle={str.usersSubtitle}
        action={
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            {isSuperAdmin && <PrimaryButton onClick={() => setCoordOpen(true)}>{str.addCoordinator}</PrimaryButton>}
            <PrimaryButton
              onClick={() => {
                setSupervisorForm({
                  fullName: "",
                  username: "",
                  password: "",
                  email: "",
                  phone: "",
                  cityId: "",
                  subcityId: "",
                  weredaId: "",
                  supervisedGradeCodes: [...CANONICAL_GRADE_CODES]
                });
                setSupOpen(true);
              }}
            >
              {str.addSupervisor}
            </PrimaryButton>
            <GhostButton onClick={downloadUsersTemplate}>Download template</GhostButton>
            <GhostButton onClick={() => importRef.current?.click()}>Import</GhostButton>
            <GhostButton onClick={exportUsers}>Export</GhostButton>
          </div>
        }
      />
      <input
        ref={importRef}
        type="file"
        accept=".xlsx"
        style={{ display: "none" }}
        onChange={importUsersFile}
      />
      {message && <Alert type={message.type}>{message.text}</Alert>}

      {isSuperAdmin && (
        <section style={{ marginBottom: 28 }}>
          <h2 style={{ fontSize: 14, fontWeight: 600, margin: "0 0 12px", color: t.muted }}>{str.clusterCoordinators}</h2>
          <DataTable
            columns={[
              { key: "fullName", label: "Name" },
              { key: "username", label: "Username" },
              { key: "email", label: "Email" },
              { key: "loc", label: "Location" },
              { key: "actions", label: "Actions" }
            ]}
            rows={coordinators}
            empty="No coordinators yet."
            renderCell={(key, row) => {
              if (key === "loc") return [row.city, row.subCity, row.wereda].filter(Boolean).join(" · ") || "—";
              if (key === "actions") {
                return (
                  <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                    <GhostButton onClick={() => openEditUser(row, "coordinator")} disabled={editUserBusy}>Edit</GhostButton>
                    <GhostButton onClick={() => openDeleteUser(row.id, "coordinator")} disabled={deleteUserBusy}>Delete</GhostButton>
                  </div>
                );
              }
              return row[key] || "—";
            }}
          />
        </section>
      )}

      <section>
        <h2 style={{ fontSize: 14, fontWeight: 600, margin: "0 0 12px", color: t.muted }}>Supervisors</h2>
        <DataTable
          columns={[
            { key: "fullName", label: "Name" },
            { key: "username", label: "Username" },
            { key: "email", label: "Email" },
            { key: "grades", label: "Supervised grades" },
            { key: "loc", label: "Location" },
            { key: "actions", label: "Actions" }
          ]}
          rows={supervisors}
          empty="No supervisors yet."
          renderCell={(key, row) => {
            if (key === "grades") {
              if (Array.isArray(row.supervisedGradeCodes) && row.supervisedGradeCodes.length > 0) {
                return row.supervisedGradeCodes.join(", ");
              }
              return "All grades (legacy)";
            }
            if (key === "loc") return [row.city, row.subCity, row.wereda].filter(Boolean).join(" · ") || "—";
            if (key === "actions") {
              return (
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                  <GhostButton onClick={() => openEditUser(row, "supervisor")} disabled={editUserBusy}>Edit</GhostButton>
                  <GhostButton onClick={() => openDeleteUser(row.id, "supervisor")} disabled={deleteUserBusy}>Delete</GhostButton>
                </div>
              );
            }
            return row[key] || "—";
          }}
        />
      </section>

      <Modal open={coordOpen} onClose={() => setCoordOpen(false)} title="New cluster coordinator">
        <form onSubmit={createCoordinator} style={{ display: "grid", gap: 12 }}>
          <div><Label>Full name</Label><Input value={coordForm.fullName} onChange={(e) => setCoordForm((p) => ({ ...p, fullName: e.target.value }))} required /></div>
          <div><Label>Username</Label><Input value={coordForm.username} onChange={(e) => setCoordForm((p) => ({ ...p, username: e.target.value }))} required /></div>
          <div><Label>Password</Label><Input type="password" value={coordForm.password} onChange={(e) => setCoordForm((p) => ({ ...p, password: e.target.value }))} required /></div>
          <div><Label>Email</Label><Input type="email" value={coordForm.email} onChange={(e) => setCoordForm((p) => ({ ...p, email: e.target.value }))} /></div>
          <div><Label>Phone</Label><Input value={coordForm.phone} onChange={(e) => setCoordForm((p) => ({ ...p, phone: e.target.value }))} placeholder="Optional" /></div>
          <p style={{ fontSize: 12, color: t.muted, margin: 0 }}>Assign a wereda from the location tree (configure under Locations).</p>
          <LocationWeredaPicker
            headers={headers}
            cityId={coordForm.cityId}
            subcityId={coordForm.subcityId}
            weredaId={coordForm.weredaId}
            onChange={(loc) => setCoordForm((p) => ({ ...p, ...loc }))}
          />
          <PrimaryButton type="submit">Create</PrimaryButton>
        </form>
      </Modal>

      <Modal open={supOpen} onClose={() => setSupOpen(false)} title="New supervisor">
        {!isSuperAdmin && (
          <p style={{ fontSize: 13, color: t.muted, marginTop: 0 }}>Location is taken from your coordinator profile.</p>
        )}
        <form onSubmit={createSupervisor} style={{ display: "grid", gap: 12 }}>
          <div><Label>Full name</Label><Input value={supervisorForm.fullName} onChange={(e) => setSupervisorForm((p) => ({ ...p, fullName: e.target.value }))} required /></div>
          <div><Label>Username</Label><Input value={supervisorForm.username} onChange={(e) => setSupervisorForm((p) => ({ ...p, username: e.target.value }))} required /></div>
          <div><Label>Password</Label><Input type="password" value={supervisorForm.password} onChange={(e) => setSupervisorForm((p) => ({ ...p, password: e.target.value }))} required /></div>
          <div><Label>Email</Label><Input type="email" value={supervisorForm.email} onChange={(e) => setSupervisorForm((p) => ({ ...p, email: e.target.value }))} /></div>
          <div><Label>Phone</Label><Input value={supervisorForm.phone} onChange={(e) => setSupervisorForm((p) => ({ ...p, phone: e.target.value }))} placeholder="Optional" /></div>
          {isSuperAdmin && (
            <>
              <p style={{ fontSize: 12, color: t.muted, margin: 0 }}>Select the supervisor&apos;s wereda (same tree as Locations).</p>
              <LocationWeredaPicker
                headers={headers}
                cityId={supervisorForm.cityId}
                subcityId={supervisorForm.subcityId}
                weredaId={supervisorForm.weredaId}
                onChange={(loc) => setSupervisorForm((p) => ({ ...p, ...loc }))}
              />
            </>
          )}
          <GradeCodeCheckboxes
            label="Grades this supervisor can supervise"
            value={supervisorForm.supervisedGradeCodes}
            onChange={(codes) => setSupervisorForm((p) => ({ ...p, supervisedGradeCodes: codes }))}
          />
          <PrimaryButton type="submit">Create</PrimaryButton>
        </form>
      </Modal>

      <Modal open={editUserOpen} onClose={() => setEditUserOpen(false)} title={`Edit ${editUserForm.kind}`}>
        <form onSubmit={saveEditUser} style={{ display: "grid", gap: 12 }}>
          <div><Label>Full name</Label><Input value={editUserForm.fullName} onChange={(e) => setEditUserForm((p) => ({ ...p, fullName: e.target.value }))} required /></div>
          <div><Label>Email</Label><Input type="email" value={editUserForm.email} onChange={(e) => setEditUserForm((p) => ({ ...p, email: e.target.value }))} /></div>
          <div><Label>Phone</Label><Input value={editUserForm.phone} onChange={(e) => setEditUserForm((p) => ({ ...p, phone: e.target.value }))} /></div>
          {isSuperAdmin && (
            <>
              <p style={{ fontSize: 12, color: t.muted, margin: 0 }}>Update assigned wereda (optional if unchanged).</p>
              <LocationWeredaPicker
                headers={headers}
                cityId={editUserForm.cityId}
                subcityId={editUserForm.subcityId}
                weredaId={editUserForm.weredaId}
                onChange={(loc) => setEditUserForm((p) => ({ ...p, ...loc }))}
              />
            </>
          )}
          {editUserForm.kind === "supervisor" && (
            <GradeCodeCheckboxes
              label="Grades this supervisor can supervise"
              value={editUserForm.supervisedGradeCodes}
              onChange={(codes) => setEditUserForm((p) => ({ ...p, supervisedGradeCodes: codes }))}
            />
          )}
          <PrimaryButton type="submit" disabled={editUserBusy}>{editUserBusy ? "Saving…" : "Save changes"}</PrimaryButton>
        </form>
      </Modal>

      <Modal open={deleteUserOpen} onClose={() => setDeleteUserOpen(false)} title={`Delete ${deleteUserForm.kind}`}>
        <form onSubmit={confirmDeleteUser} style={{ display: "grid", gap: 12 }}>
          <p style={{ margin: 0, color: t.muted, fontSize: 14 }}>
            Deletion is blocked when related records still exist.
          </p>
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
            <GhostButton type="button" onClick={() => setDeleteUserOpen(false)} disabled={deleteUserBusy}>Cancel</GhostButton>
            <PrimaryButton type="submit" disabled={deleteUserBusy}>{deleteUserBusy ? "Deleting…" : "Delete"}</PrimaryButton>
          </div>
        </form>
      </Modal>
    </>
  );
}

function SchoolsPage({ headers }) {
  const { str } = useI18n();
  const [schools, setSchools] = useState([]);
  const [nameFilter, setNameFilter] = useState("");
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState({
    name: "",
    latitude: "9.03",
    longitude: "38.74",
    allowedRadiusInMeters: "150",
    supportedGradeCodes: []
  });
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editBusy, setEditBusy] = useState(false);
  const [editForm, setEditForm] = useState({
    id: "",
    name: "",
    latitude: "9.03",
    longitude: "38.74",
    allowedRadiusInMeters: "150",
    supportedGradeCodes: []
  });
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteSchoolId, setDeleteSchoolId] = useState("");
  const [message, setMessage] = useState(null);
  const importRef = useRef(null);

  const load = () => {
    const q = nameFilter.trim();
    const url = q ? `${API_BASE}/schools?q=${encodeURIComponent(q)}` : `${API_BASE}/schools`;
    return fetch(url, { headers }).then((r) => r.json()).then(setSchools);
  };
  useEffect(() => {
    load();
  }, [headers]);

  const createSchool = async (e) => {
    e.preventDefault();
    const res = await fetch(`${API_BASE}/schools`, {
      method: "POST",
      headers: jsonHeaders(headers),
      body: JSON.stringify({
        name: form.name,
        latitude: Number(form.latitude),
        longitude: Number(form.longitude),
        allowedRadiusInMeters: Number(form.allowedRadiusInMeters || 150),
        supportedGradeCodes: form.supportedGradeCodes || []
      })
    });
    if (!res.ok) {
      let details = null;
      try {
        details = await res.json();
      } catch (_) {}
      setMessage({ type: "error", text: details?.message || "Could not create school." });
      return;
    }
    setMessage({ type: "ok", text: "School added." });
    setForm({ name: "", latitude: "9.03", longitude: "38.74", allowedRadiusInMeters: "150", supportedGradeCodes: [] });
    setModalOpen(false);
    load();
  };

  const openEditSchool = (row) => {
    setEditForm({
      id: row.id,
      name: row.name ?? "",
      latitude: row.latitude ?? "",
      longitude: row.longitude ?? "",
      allowedRadiusInMeters: row.allowedRadiusInMeters ?? 150,
      supportedGradeCodes: Array.isArray(row.supportedGradeCodes) ? row.supportedGradeCodes : []
    });
    setEditModalOpen(true);
  };

  const saveEditSchool = async (e) => {
    e.preventDefault();
    if (!editForm.id) return;
    setEditBusy(true);
    try {
      const res = await fetch(`${API_BASE}/schools/${editForm.id}`, {
        method: "PATCH",
        headers: jsonHeaders(headers),
        body: JSON.stringify({
          name: editForm.name,
          latitude: Number(editForm.latitude),
          longitude: Number(editForm.longitude),
          allowedRadiusInMeters: Number(editForm.allowedRadiusInMeters),
          supportedGradeCodes: editForm.supportedGradeCodes || []
        })
      });
      if (!res.ok) {
        let details = null;
        try {
          details = await res.json();
        } catch (_) {}
        throw new Error(details?.message || "Could not update school.");
      }
      setEditModalOpen(false);
      setMessage({ type: "ok", text: "School updated." });
      await load();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setEditBusy(false);
    }
  };

  const openDeleteSchool = (id) => {
    setDeleteSchoolId(id);
    setDeleteModalOpen(true);
  };

  const confirmDeleteSchool = async (e) => {
    e.preventDefault();
    if (!deleteSchoolId) return;
    setDeleteBusy(true);
    try {
      const res = await fetch(`${API_BASE}/schools/${deleteSchoolId}`, { method: "DELETE", headers });
      if (!res.ok) {
        let details = null;
        try {
          details = await res.json();
        } catch (_) {}
        throw new Error(details?.message || "Could not delete school.");
      }
      setDeleteModalOpen(false);
      setMessage({ type: "ok", text: "School deleted." });
      await load();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setDeleteBusy(false);
      setDeleteSchoolId("");
    }
  };

  const downloadTemplate = async () => {
    try {
      await downloadXlsx(`${API_BASE}/schools/template`, headers, "schools-template.xlsx");
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    }
  };

  const exportSchools = async () => {
    try {
      const q = nameFilter.trim();
      const url = q ? `${API_BASE}/schools/export?q=${encodeURIComponent(q)}` : `${API_BASE}/schools/export`;
      await downloadXlsx(url, headers, "schools-export.xlsx");
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    }
  };

  const importSchoolsFile = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const result = await uploadXlsx(`${API_BASE}/schools/import`, headers, file);
      const failed = Number(result?.failed || 0);
      const created = Number(result?.created || 0);
      setMessage({ type: failed > 0 ? "error" : "ok", text: `Import finished. Created: ${created}, Failed: ${failed}.` });
      await load();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      e.target.value = "";
    }
  };

  return (
    <>
      <PageHeader
        title={str.schoolsTitle}
        subtitle={str.schoolsSubtitle}
        action={
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <PrimaryButton onClick={() => setModalOpen(true)}>{str.addSchool}</PrimaryButton>
            <GhostButton onClick={downloadTemplate}>Download template</GhostButton>
            <GhostButton onClick={() => importRef.current?.click()}>Import</GhostButton>
            <GhostButton onClick={exportSchools}>Export</GhostButton>
          </div>
        }
      />
      <input ref={importRef} type="file" accept=".xlsx" style={{ display: "none" }} onChange={importSchoolsFile} />
      {message && <Alert type={message.type}>{message.text}</Alert>}
      <div style={{ display: "flex", gap: 8, marginBottom: 16, flexWrap: "wrap", alignItems: "center" }}>
        <Input placeholder={str.filterByName} value={nameFilter} onChange={(e) => setNameFilter(e.target.value)} style={{ maxWidth: 240 }} />
        <GhostButton onClick={load}>{str.apply}</GhostButton>
        <GhostButton
          onClick={() => {
            setNameFilter("");
            fetch(`${API_BASE}/schools`, { headers }).then((r) => r.json()).then(setSchools);
          }}
        >
          {str.clearFilter}
        </GhostButton>
      </div>
      <DataTable
        columns={[
          { key: "name", label: str.tabSchools },
          { key: "grades", label: "Grades" },
          { key: "lat", label: "Latitude" },
          { key: "lon", label: "Longitude" },
          { key: "r", label: "Radius (m)" },
          { key: "actions", label: str.actions }
        ]}
        rows={schools}
        empty="No schools match."
        renderCell={(key, row) => {
          if (key === "grades") {
            const g = row.supportedGradeCodes;
            return Array.isArray(g) && g.length ? g.join(", ") : "—";
          }
          if (key === "lat") return row.latitude;
          if (key === "lon") return row.longitude;
          if (key === "r") return row.allowedRadiusInMeters;
          if (key === "actions") {
            return (
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <GhostButton onClick={() => openEditSchool(row)} disabled={editBusy}>{str.edit}</GhostButton>
                <GhostButton onClick={() => openDeleteSchool(row.id)} disabled={deleteBusy}>{str.delete}</GhostButton>
              </div>
            );
          }
          return row[key];
        }}
      />

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="New school" wide>
        <form onSubmit={createSchool} style={{ display: "grid", gap: 12 }}>
          <div><Label>Name</Label><Input value={form.name} onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))} required /></div>
          <GradeCodeCheckboxes
            label="Grades this school serves"
            value={form.supportedGradeCodes}
            onChange={(codes) => setForm((p) => ({ ...p, supportedGradeCodes: codes }))}
          />
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8 }}>
            <div><Label>Latitude</Label><Input value={form.latitude} onChange={(e) => setForm((p) => ({ ...p, latitude: e.target.value }))} required /></div>
            <div><Label>Longitude</Label><Input value={form.longitude} onChange={(e) => setForm((p) => ({ ...p, longitude: e.target.value }))} required /></div>
            <div><Label>Radius (m)</Label><Input value={form.allowedRadiusInMeters} onChange={(e) => setForm((p) => ({ ...p, allowedRadiusInMeters: e.target.value }))} required /></div>
          </div>
          <PrimaryButton type="submit">Save</PrimaryButton>
        </form>
      </Modal>

      <Modal
        open={editModalOpen}
        onClose={() => (!editBusy ? setEditModalOpen(false) : null)}
        title="Edit school"
        wide
      >
        <form onSubmit={saveEditSchool} style={{ display: "grid", gap: 12 }}>
          <div><Label>Name</Label><Input value={editForm.name} onChange={(e) => setEditForm((p) => ({ ...p, name: e.target.value }))} required /></div>
          <GradeCodeCheckboxes
            label="Grades this school serves"
            value={editForm.supportedGradeCodes}
            onChange={(codes) => setEditForm((p) => ({ ...p, supportedGradeCodes: codes }))}
          />
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8 }}>
            <div><Label>Latitude</Label><Input value={editForm.latitude} onChange={(e) => setEditForm((p) => ({ ...p, latitude: e.target.value }))} required /></div>
            <div><Label>Longitude</Label><Input value={editForm.longitude} onChange={(e) => setEditForm((p) => ({ ...p, longitude: e.target.value }))} required /></div>
            <div><Label>Radius (m)</Label><Input value={editForm.allowedRadiusInMeters} onChange={(e) => setEditForm((p) => ({ ...p, allowedRadiusInMeters: e.target.value }))} required /></div>
          </div>
          <PrimaryButton type="submit" disabled={editBusy}>{editBusy ? "Saving…" : "Save changes"}</PrimaryButton>
        </form>
      </Modal>

      <Modal
        open={deleteModalOpen}
        onClose={() => (!deleteBusy ? setDeleteModalOpen(false) : null)}
        title="Delete school"
      >
        <form onSubmit={confirmDeleteSchool} style={{ display: "grid", gap: 12 }}>
          <p style={{ margin: 0, color: t.muted, fontSize: 14 }}>This will delete the school. Deletion is blocked if teachers or assignments exist.</p>
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
            <GhostButton type="button" onClick={() => setDeleteModalOpen(false)} disabled={deleteBusy}>Cancel</GhostButton>
            <PrimaryButton type="submit" disabled={deleteBusy}>{deleteBusy ? "Deleting…" : "Delete"}</PrimaryButton>
          </div>
        </form>
      </Modal>
    </>
  );
}

function SchoolStuffPage({ headers, isSuperAdmin }) {
  const { str } = useI18n();
  const [types, setTypes] = useState([]);
  const [subjects, setSubjects] = useState([]);
  const [items, setItems] = useState([]);
  const [schools, setSchools] = useState([]);
  const [message, setMessage] = useState(null);
  const [addOpen, setAddOpen] = useState(false);
  const [typeMgmtOpen, setTypeMgmtOpen] = useState(false);
  const [typesManageOpen, setTypesManageOpen] = useState(false);
  const [subjectsManageOpen, setSubjectsManageOpen] = useState(false);
  const [loadingBusy, setLoadingBusy] = useState(false);
  const importRef = useRef(null);

  const [selectedTypeId, setSelectedTypeId] = useState("");
  const selectedType = types.find((t) => t.id === selectedTypeId) || null;

  const [typeForm, setTypeForm] = useState({ name: "", description: "" });
  const [editTypeOpen, setEditTypeOpen] = useState(false);
  const [editTypeForm, setEditTypeForm] = useState({ id: "", name: "", description: "" });
  const [deleteTypeOpen, setDeleteTypeOpen] = useState(false);
  const [deleteTypeId, setDeleteTypeId] = useState("");
  const [subjectForm, setSubjectForm] = useState({ name: "" });
  const [editSubjectOpen, setEditSubjectOpen] = useState(false);
  const [editSubjectForm, setEditSubjectForm] = useState({ id: "", name: "" });
  const [deleteSubjectOpen, setDeleteSubjectOpen] = useState(false);
  const [deleteSubjectId, setDeleteSubjectId] = useState("");

  const [form, setForm] = useState({
    fullName: "",
    subjectId: "",
    schoolId: "",
    responsibleGradeCodes: [],
    username: "",
    password: "",
    email: "",
    phone: "",
    city: "",
    subCity: "",
    wereda: ""
  });

  const [filterSchoolId, setFilterSchoolId] = useState("");

  // Edit/delete for all staff types.
  const [editTeacherOpen, setEditTeacherOpen] = useState(false);
  const [editTeacherBusy, setEditTeacherBusy] = useState(false);
  const [editTeacherForm, setEditTeacherForm] = useState({
    id: "",
    type: "TEACHER",
    name: "",
    subjectId: "",
    schoolId: "",
    responsibleGradeCodes: [],
    email: "",
    phone: "",
    city: "",
    subCity: "",
    wereda: ""
  });
  const [deleteTeacherOpen, setDeleteTeacherOpen] = useState(false);
  const [deleteTeacherBusy, setDeleteTeacherBusy] = useState(false);
  const [deleteTeacherId, setDeleteTeacherId] = useState("");

  const addFormSchool = useMemo(() => schools.find((s) => s.id === form.schoolId), [schools, form.schoolId]);
  const addGradeChoices = useMemo(() => {
    if (!addFormSchool || !Array.isArray(addFormSchool.supportedGradeCodes)) return CANONICAL_GRADE_CODES;
    return addFormSchool.supportedGradeCodes.length > 0 ? addFormSchool.supportedGradeCodes : CANONICAL_GRADE_CODES;
  }, [addFormSchool]);

  const editFormSchool = useMemo(() => schools.find((s) => s.id === editTeacherForm.schoolId), [schools, editTeacherForm.schoolId]);
  const editGradeChoices = useMemo(() => {
    if (!editFormSchool || !Array.isArray(editFormSchool.supportedGradeCodes)) return CANONICAL_GRADE_CODES;
    return editFormSchool.supportedGradeCodes.length > 0 ? editFormSchool.supportedGradeCodes : CANONICAL_GRADE_CODES;
  }, [editFormSchool]);

  const loadSchools = () => fetch(`${API_BASE}/schools`, { headers }).then((r) => r.json()).then(setSchools);
  const loadTypes = () => fetch(`${API_BASE}/school-stuff/types`, { headers }).then((r) => r.json()).then(setTypes);
  const loadSubjects = () => fetch(`${API_BASE}/school-stuff/subjects`, { headers }).then((r) => r.json()).then(setSubjects);
  const loadItems = () => fetch(`${API_BASE}/school-stuff`, { headers }).then((r) => r.json()).then(setItems);

  useEffect(() => {
    loadSchools();
    loadSubjects().catch(() => {});
    loadTypes().then(() => {
      // Initialize type selection after types load.
      setSelectedTypeId((prev) => prev || (types[0] ? types[0].id : ""));
    }).catch(() => {});
    loadItems();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [headers]);

  useEffect(() => {
    if (!selectedTypeId && types.length) {
      setSelectedTypeId(types[0].id);
    }
  }, [types, selectedTypeId]);

  const openAddStuff = () => {
    const roleName = selectedType ? selectedType.name : "";
    setForm((p) => ({
      ...p,
      fullName: "",
      subjectId: roleName === "TEACHER" ? "" : p.subjectId,
      schoolId: filterSchoolId || "",
      responsibleGradeCodes: [],
      username: "",
      password: "",
      email: "",
      phone: "",
      city: "",
      subCity: "",
      wereda: ""
    }));
    setAddOpen(true);
    setMessage(null);
  };

  const createStuff = async (e) => {
    e.preventDefault();
    if (!selectedTypeId) {
      setMessage({ type: "error", text: "Select a type first." });
      return;
    }
    if (selectedType?.name === "TEACHER") {
      if (!form.responsibleGradeCodes || form.responsibleGradeCodes.length === 0) {
        setMessage({ type: "error", text: "Select at least one grade this teacher is responsible for." });
        return;
      }
    }
    setLoadingBusy(true);
    try {
      const payload = {
        roleId: selectedTypeId,
        fullName: form.fullName,
        username: form.username || null,
        password: form.password || null,
        email: form.email || null,
        phone: form.phone || null,
        schoolId: form.schoolId || null,
        subjectId: selectedType?.name === "TEACHER" ? (form.subjectId || null) : null,
        responsibleGradeCodes: selectedType?.name === "TEACHER" ? form.responsibleGradeCodes : null,
        city: form.city || null,
        subCity: form.subCity || null,
        wereda: form.wereda || null
      };
      const res = await fetch(`${API_BASE}/school-stuff`, { method: "POST", headers: jsonHeaders(headers), body: JSON.stringify(payload) });
      if (!res.ok) {
        let details = null;
        try { details = await res.json(); } catch (_) {}
        throw new Error(details?.message || "Failed to add school stuff.");
      }
      setAddOpen(false);
      setMessage({ type: "ok", text: "Saved." });
      setForm({
        fullName: "",
        subjectId: "",
        schoolId: "",
        responsibleGradeCodes: [],
        username: "",
        password: "",
        email: "",
        phone: "",
        city: "",
        subCity: "",
        wereda: ""
      });
      await loadItems();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setLoadingBusy(false);
    }
  };

  const createType = async (e) => {
    e.preventDefault();
    if (!typeForm.name.trim()) return;
    setLoadingBusy(true);
    try {
      const res = await fetch(`${API_BASE}/school-stuff/types`, { method: "POST", headers: jsonHeaders(headers), body: JSON.stringify(typeForm) });
      if (!res.ok) {
        let details = null;
        try { details = await res.json(); } catch (_) {}
        throw new Error(details?.message || "Failed to create type.");
      }
      setTypeMgmtOpen(false);
      setTypeForm({ name: "", description: "" });
      setMessage({ type: "ok", text: "Type created." });
      await loadTypes();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setLoadingBusy(false);
    }
  };

  const openEditTeacher = (row) => {
    setEditTeacherForm({
      id: row.id,
      type: row.type || "TEACHER",
      name: row.fullName || "",
      subjectId: row.subjectId || "",
      schoolId: row.schoolId || "",
      responsibleGradeCodes: Array.isArray(row.responsibleGradeCodes) ? row.responsibleGradeCodes : [],
      email: row.email || "",
      phone: row.phone || "",
      city: row.city || "",
      subCity: row.subCity || "",
      wereda: row.wereda || ""
    });
    setEditTeacherOpen(true);
    setMessage(null);
  };

  const saveEditTeacher = async (e) => {
    e.preventDefault();
    if (editTeacherForm.type === "TEACHER" && (!editTeacherForm.responsibleGradeCodes || editTeacherForm.responsibleGradeCodes.length === 0)) {
      setMessage({ type: "error", text: "Select at least one grade this teacher is responsible for." });
      return;
    }
    setEditTeacherBusy(true);
    try {
      const res = editTeacherForm.type === "TEACHER"
        ? await fetch(`${API_BASE}/teachers/${editTeacherForm.id}`, {
            method: "PATCH",
            headers: jsonHeaders(headers),
            body: JSON.stringify({
              name: editTeacherForm.name,
              subjectId: editTeacherForm.subjectId || null,
              schoolId: editTeacherForm.schoolId,
              responsibleGradeCodes: editTeacherForm.responsibleGradeCodes
            })
          })
        : await fetch(`${API_BASE}/school-stuff/${editTeacherForm.id}`, {
            method: "PATCH",
            headers: jsonHeaders(headers),
            body: JSON.stringify({
              type: editTeacherForm.type,
              fullName: editTeacherForm.name,
              subjectId: editTeacherForm.type === "TEACHER" ? (editTeacherForm.subjectId || null) : null,
              schoolId: editTeacherForm.schoolId || null,
              responsibleGradeCodes: editTeacherForm.type === "TEACHER" ? editTeacherForm.responsibleGradeCodes : null,
              email: editTeacherForm.email || null,
              phone: editTeacherForm.phone || null,
              city: editTeacherForm.city || null,
              subCity: editTeacherForm.subCity || null,
              wereda: editTeacherForm.wereda || null
            })
          });
      if (!res.ok) {
        let details = null;
        try { details = await res.json(); } catch (_) {}
        throw new Error(details?.message || "Staff update failed.");
      }
      setEditTeacherOpen(false);
      setMessage({ type: "ok", text: "Staff updated." });
      await loadItems();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setEditTeacherBusy(false);
    }
  };

  const openDeleteTeacher = (id, type = "TEACHER") => {
    setEditTeacherForm((p) => ({ ...p, type }));
    setDeleteTeacherId(id);
    setDeleteTeacherOpen(true);
    setMessage(null);
  };

  const confirmDeleteTeacher = async (e) => {
    e.preventDefault();
    setDeleteTeacherBusy(true);
    try {
      const res = editTeacherForm.type === "TEACHER"
        ? await fetch(`${API_BASE}/teachers/${deleteTeacherId}`, { method: "DELETE", headers })
        : await fetch(`${API_BASE}/school-stuff/${deleteTeacherId}?type=${encodeURIComponent(editTeacherForm.type)}`, { method: "DELETE", headers });
      if (!res.ok) {
        let details = null;
        try { details = await res.json(); } catch (_) {}
        throw new Error(details?.message || "Staff delete failed.");
      }
      setDeleteTeacherOpen(false);
      setMessage({ type: "ok", text: "Staff deleted." });
      await loadItems();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setDeleteTeacherBusy(false);
      setDeleteTeacherId("");
    }
  };

  const saveEditType = async (e) => {
    e.preventDefault();
    setLoadingBusy(true);
    try {
      const res = await fetch(`${API_BASE}/school-stuff/types/${editTypeForm.id}`, {
        method: "PATCH",
        headers: jsonHeaders(headers),
        body: JSON.stringify({
          name: editTypeForm.name.trim().toUpperCase(),
          description: editTypeForm.description.trim()
        })
      });
      if (!res.ok) {
        let details = null;
        try { details = await res.json(); } catch (_) {}
        throw new Error(details?.message || "Failed to update type.");
      }
      setEditTypeOpen(false);
      setMessage({ type: "ok", text: "Type updated." });
      await loadTypes();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setLoadingBusy(false);
    }
  };

  const confirmDeleteType = async (e) => {
    e.preventDefault();
    setLoadingBusy(true);
    try {
      const res = await fetch(`${API_BASE}/school-stuff/types/${deleteTypeId}`, { method: "DELETE", headers });
      if (!res.ok) {
        let details = null;
        try { details = await res.json(); } catch (_) {}
        throw new Error(details?.message || "Failed to delete type.");
      }
      setDeleteTypeOpen(false);
      setDeleteTypeId("");
      setMessage({ type: "ok", text: "Type deleted." });
      await loadTypes();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setLoadingBusy(false);
    }
  };

  const createSubjectFromMgmt = async (e) => {
    e.preventDefault();
    if (!subjectForm.name.trim()) return;
    setLoadingBusy(true);
    try {
      const res = await fetch(`${API_BASE}/school-stuff/subjects`, {
        method: "POST",
        headers: jsonHeaders(headers),
        body: JSON.stringify({ name: subjectForm.name.trim() })
      });
      if (!res.ok) {
        let details = null;
        try { details = await res.json(); } catch (_) {}
        throw new Error(details?.message || "Failed to add subject.");
      }
      setSubjectForm({ name: "" });
      setMessage({ type: "ok", text: "Subject added." });
      await loadSubjects();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setLoadingBusy(false);
    }
  };

  const saveEditSubject = async (e) => {
    e.preventDefault();
    setLoadingBusy(true);
    try {
      const res = await fetch(`${API_BASE}/school-stuff/subjects/${editSubjectForm.id}`, {
        method: "PATCH",
        headers: jsonHeaders(headers),
        body: JSON.stringify({ name: editSubjectForm.name.trim() })
      });
      if (!res.ok) {
        let details = null;
        try { details = await res.json(); } catch (_) {}
        throw new Error(details?.message || "Failed to update subject.");
      }
      setEditSubjectOpen(false);
      setMessage({ type: "ok", text: "Subject updated." });
      await loadSubjects();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setLoadingBusy(false);
    }
  };

  const confirmDeleteSubject = async (e) => {
    e.preventDefault();
    setLoadingBusy(true);
    try {
      const res = await fetch(`${API_BASE}/school-stuff/subjects/${deleteSubjectId}`, { method: "DELETE", headers });
      if (!res.ok) {
        let details = null;
        try { details = await res.json(); } catch (_) {}
        throw new Error(details?.message || "Failed to delete subject.");
      }
      setDeleteSubjectOpen(false);
      setDeleteSubjectId("");
      setMessage({ type: "ok", text: "Subject deleted." });
      await loadSubjects();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setLoadingBusy(false);
    }
  };

  const visibleItems = items.filter((it) => {
    if (!filterSchoolId) return true;
    if (!it.schoolId) return false;
    return it.schoolId === filterSchoolId;
  });

  const downloadTemplate = async () => {
    try {
      await downloadXlsx(`${API_BASE}/school-stuff/template`, headers, "school-stuff-template.xlsx");
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    }
  };

  const exportData = async () => {
    try {
      await downloadXlsx(`${API_BASE}/school-stuff/export`, headers, "school-stuff-export.xlsx");
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    }
  };

  const importDataFile = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const result = await uploadXlsx(`${API_BASE}/school-stuff/import`, headers, file);
      const failed = Number(result?.failed || 0);
      const created = Number(result?.created || 0);
      setMessage({ type: failed > 0 ? "error" : "ok", text: `Import finished. Created: ${created}, Failed: ${failed}.` });
      await loadItems();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      e.target.value = "";
    }
  };

  return (
    <>
      <PageHeader
        title={str.schoolStuffTitle}
        subtitle={str.schoolStuffSubtitle}
        action={
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <PrimaryButton onClick={openAddStuff}>{str.addStuff}</PrimaryButton>
            <GhostButton onClick={() => setTypeMgmtOpen(true)}>{str.addType}</GhostButton>
            <GhostButton onClick={() => { setTypesManageOpen(true); loadTypes(); }}>{str.manageTypes}</GhostButton>
            <GhostButton onClick={() => { setSubjectsManageOpen(true); loadSubjects(); }}>{str.manageSubjects}</GhostButton>
            <GhostButton onClick={downloadTemplate}>Download template</GhostButton>
            <GhostButton onClick={() => importRef.current?.click()}>Import</GhostButton>
            <GhostButton onClick={exportData}>Export</GhostButton>
          </div>
        }
      />
      <input ref={importRef} type="file" accept=".xlsx" style={{ display: "none" }} onChange={importDataFile} />
      {message && <Alert type={message.type}>{message.text}</Alert>}
      <div style={{ marginBottom: 16 }}>
        <Label>{str.schoolFilter}</Label>
        <select
          value={filterSchoolId}
          onChange={(e) => setFilterSchoolId(e.target.value)}
          style={{ fontFamily: t.font, padding: "10px 12px", borderRadius: t.radius, border: `1px solid ${t.line}`, minWidth: 260 }}
        >
          <option value="">{str.allInScope}</option>
          {schools.map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </select>
      </div>
      <DataTable
        columns={[
          { key: "type", label: str.typeColumn },
          { key: "fullName", label: str.name },
          { key: "subject", label: "Subject" },
          { key: "schoolName", label: "School" },
          { key: "grades", label: "Grades" },
          { key: "username", label: "Username" },
          { key: "email", label: "Email" },
          { key: "phone", label: "Phone" },
          { key: "actions", label: "Actions" }
        ]}
        rows={visibleItems}
        empty="No staff in scope."
        renderCell={(key, row) => {
          if (key === "subject") return row.subject || "—";
          if (key === "schoolName") return row.schoolName || "—";
          if (key === "grades") {
            if (row.type === "TEACHER" && Array.isArray(row.responsibleGradeCodes) && row.responsibleGradeCodes.length) {
              return row.responsibleGradeCodes.join(", ");
            }
            return "—";
          }
          if (key === "actions") {
            return (
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <GhostButton onClick={() => openEditTeacher(row)} disabled={editTeacherBusy}>Edit</GhostButton>
                <GhostButton onClick={() => openDeleteTeacher(row.id, row.type)} disabled={deleteTeacherBusy}>Delete</GhostButton>
              </div>
            );
          }
          return row[key] || "—";
        }}
      />

      <Modal open={addOpen} onClose={() => setAddOpen(false)} title={`Add ${selectedType ? selectedType.name : "stuff"}`}>
        <form onSubmit={createStuff} style={{ display: "grid", gap: 12 }}>
          <div>
            <Label>Type</Label>
            <select value={selectedTypeId} onChange={(e) => setSelectedTypeId(e.target.value)} required
              style={{ fontFamily: t.font, width: "100%", padding: "10px 12px", borderRadius: t.radius, border: `1px solid ${t.line}` }}>
              {types.map((tp) => (
                <option key={tp.id} value={tp.id}>{tp.name}</option>
              ))}
            </select>
          </div>

          <div><Label>Full name</Label><Input value={form.fullName} onChange={(e) => setForm((p) => ({ ...p, fullName: e.target.value }))} required /></div>

          {selectedType?.name === "TEACHER" && (
            <>
              <div>
                <Label>Subject</Label>
                <select
                  value={form.subjectId}
                  onChange={(e) => setForm((p) => ({ ...p, subjectId: e.target.value }))}
                  required
                  style={{ fontFamily: t.font, width: "100%", padding: "10px 12px", borderRadius: t.radius, border: `1px solid ${t.line}` }}
                >
                  <option value="">Select…</option>
                  {subjects.map((s) => (
                    <option key={s.id} value={s.id}>{s.name}</option>
                  ))}
                </select>
                {subjects.length === 0 && (
                  <p style={{ margin: "6px 0 0", fontSize: 12, color: t.muted }}>Add subjects under “Manage subjects” first.</p>
                )}
              </div>
              <div>
                <Label>School</Label>
                <select
                  value={form.schoolId}
                  onChange={(e) => {
                    const nextSchoolId = e.target.value;
                    const sch = schools.find((s) => s.id === nextSchoolId);
                    const allowed =
                      sch && Array.isArray(sch.supportedGradeCodes) && sch.supportedGradeCodes.length > 0
                        ? sch.supportedGradeCodes
                        : CANONICAL_GRADE_CODES;
                    setForm((p) => ({
                      ...p,
                      schoolId: nextSchoolId,
                      responsibleGradeCodes: (p.responsibleGradeCodes || []).filter((c) => allowed.includes(c))
                    }));
                  }}
                  required
                  style={{ fontFamily: t.font, width: "100%", padding: "10px 12px", borderRadius: t.radius, border: `1px solid ${t.line}` }}
                >
                  <option value="">Select…</option>
                  {schools.map((s) => (
                    <option key={s.id} value={s.id}>{s.name}</option>
                  ))}
                </select>
              </div>
              <GradeCodeCheckboxes
                label="Grades this teacher is responsible for"
                value={form.responsibleGradeCodes}
                onChange={(codes) => setForm((p) => ({ ...p, responsibleGradeCodes: codes }))}
                codes={addGradeChoices}
              />
              <p style={{ margin: 0, fontSize: 12, color: t.muted }}>
                Optional login (leave blank to create teacher without an account).
              </p>
            </>
          )}

          {selectedType?.name === "SCHOOL_DIRECTOR" && (
            <>
              <div>
                <Label>School</Label>
                <select
                  value={form.schoolId}
                  onChange={(e) => setForm((p) => ({ ...p, schoolId: e.target.value }))}
                  required
                  style={{ fontFamily: t.font, width: "100%", padding: "10px 12px", borderRadius: t.radius, border: `1px solid ${t.line}` }}
                >
                  <option value="">Select…</option>
                  {schools.map((s) => (
                    <option key={s.id} value={s.id}>{s.name}</option>
                  ))}
                </select>
              </div>
            </>
          )}

          {selectedType && selectedType.name !== "TEACHER" && selectedType.name !== "SCHOOL_DIRECTOR" && (
            <p style={{ margin: 0, fontSize: 12, color: t.muted }}>This role type is created as a user account.</p>
          )}

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div><Label>Username</Label><Input value={form.username} onChange={(e) => setForm((p) => ({ ...p, username: e.target.value }))} required={selectedType?.name !== "TEACHER"} /></div>
            <div><Label>Password</Label><Input type="password" value={form.password} onChange={(e) => setForm((p) => ({ ...p, password: e.target.value }))} required={selectedType?.name !== "TEACHER"} /></div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div><Label>Email</Label><Input type="email" value={form.email} onChange={(e) => setForm((p) => ({ ...p, email: e.target.value }))} /></div>
            <div><Label>Phone</Label><Input value={form.phone} onChange={(e) => setForm((p) => ({ ...p, phone: e.target.value }))} placeholder="Optional" /></div>
          </div>

          {isSuperAdmin && (
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8 }}>
              <div><Label>City</Label><Input value={form.city} onChange={(e) => setForm((p) => ({ ...p, city: e.target.value }))} /></div>
              <div><Label>Sub city</Label><Input value={form.subCity} onChange={(e) => setForm((p) => ({ ...p, subCity: e.target.value }))} /></div>
              <div><Label>Wereda</Label><Input value={form.wereda} onChange={(e) => setForm((p) => ({ ...p, wereda: e.target.value }))} /></div>
            </div>
          )}

          <PrimaryButton type="submit" disabled={loadingBusy}>
            {loadingBusy ? "Saving…" : "Save"}
          </PrimaryButton>
        </form>
      </Modal>

      <Modal open={typeMgmtOpen} onClose={() => setTypeMgmtOpen(false)} title="Add school stuff type">
        <form onSubmit={createType} style={{ display: "grid", gap: 12 }}>
          <div><Label>Role type name</Label><Input value={typeForm.name} onChange={(e) => setTypeForm((p) => ({ ...p, name: e.target.value.toUpperCase() }))} required /></div>
          <div><Label>Description</Label><Input value={typeForm.description} onChange={(e) => setTypeForm((p) => ({ ...p, description: e.target.value }))} required /></div>
          <PrimaryButton type="submit" disabled={loadingBusy}>{loadingBusy ? "Saving…" : "Create type"}</PrimaryButton>
        </form>
      </Modal>

      <Modal open={editTeacherOpen} onClose={() => setEditTeacherOpen(false)} title={`Edit ${editTeacherForm.type.toLowerCase()}`} wide>
        <form onSubmit={saveEditTeacher} style={{ display: "grid", gap: 12 }}>
          <div><Label>Full name</Label><Input value={editTeacherForm.name} onChange={(e) => setEditTeacherForm((p) => ({ ...p, name: e.target.value }))} required /></div>
          {editTeacherForm.type === "TEACHER" && (
            <>
              <div>
                <Label>Subject</Label>
                <select
                  value={editTeacherForm.subjectId}
                  onChange={(e) => setEditTeacherForm((p) => ({ ...p, subjectId: e.target.value }))}
                  required
                  style={{ fontFamily: t.font, width: "100%", padding: "10px 12px", borderRadius: t.radius, border: `1px solid ${t.line}` }}
                >
                  <option value="">Select…</option>
                  {subjects.map((s) => (
                    <option key={s.id} value={s.id}>{s.name}</option>
                  ))}
                </select>
              </div>
              <div>
                <Label>School</Label>
                <select
                  value={editTeacherForm.schoolId}
                  onChange={(e) => {
                    const nextSchoolId = e.target.value;
                    const sch = schools.find((s) => s.id === nextSchoolId);
                    const allowed =
                      sch && Array.isArray(sch.supportedGradeCodes) && sch.supportedGradeCodes.length > 0
                        ? sch.supportedGradeCodes
                        : CANONICAL_GRADE_CODES;
                    setEditTeacherForm((p) => ({
                      ...p,
                      schoolId: nextSchoolId,
                      responsibleGradeCodes: (p.responsibleGradeCodes || []).filter((c) => allowed.includes(c))
                    }));
                  }}
                  required
                  style={{ fontFamily: t.font, width: "100%", padding: "10px 12px", borderRadius: t.radius, border: `1px solid ${t.line}` }}
                >
                  <option value="">Select…</option>
                  {schools.map((s) => (
                    <option key={s.id} value={s.id}>{s.name}</option>
                  ))}
                </select>
              </div>
              <GradeCodeCheckboxes
                label="Grades this teacher is responsible for"
                value={editTeacherForm.responsibleGradeCodes}
                onChange={(codes) => setEditTeacherForm((p) => ({ ...p, responsibleGradeCodes: codes }))}
                codes={editGradeChoices}
              />
            </>
          )}
          {editTeacherForm.type !== "TEACHER" && (
            <>
              <div><Label>Email</Label><Input type="email" value={editTeacherForm.email} onChange={(e) => setEditTeacherForm((p) => ({ ...p, email: e.target.value }))} /></div>
              <div><Label>Phone</Label><Input value={editTeacherForm.phone} onChange={(e) => setEditTeacherForm((p) => ({ ...p, phone: e.target.value }))} /></div>
            </>
          )}
          {editTeacherForm.type === "SCHOOL_DIRECTOR" && (
            <div>
              <Label>School</Label>
              <select
                value={editTeacherForm.schoolId}
                onChange={(e) => setEditTeacherForm((p) => ({ ...p, schoolId: e.target.value }))}
                required
                style={{ fontFamily: t.font, width: "100%", padding: "10px 12px", borderRadius: t.radius, border: `1px solid ${t.line}` }}
              >
                <option value="">Select…</option>
                {schools.map((s) => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </select>
            </div>
          )}
          {isSuperAdmin && editTeacherForm.type !== "TEACHER" && (
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8 }}>
              <div><Label>City</Label><Input value={editTeacherForm.city} onChange={(e) => setEditTeacherForm((p) => ({ ...p, city: e.target.value }))} /></div>
              <div><Label>Sub city</Label><Input value={editTeacherForm.subCity} onChange={(e) => setEditTeacherForm((p) => ({ ...p, subCity: e.target.value }))} /></div>
              <div><Label>Wereda</Label><Input value={editTeacherForm.wereda} onChange={(e) => setEditTeacherForm((p) => ({ ...p, wereda: e.target.value }))} /></div>
            </div>
          )}
          <PrimaryButton type="submit" disabled={editTeacherBusy}>{editTeacherBusy ? "Saving…" : "Save changes"}</PrimaryButton>
        </form>
      </Modal>

      <Modal open={deleteTeacherOpen} onClose={() => setDeleteTeacherOpen(false)} title={`Delete ${editTeacherForm.type.toLowerCase()}`}>
        <form onSubmit={confirmDeleteTeacher} style={{ display: "grid", gap: 12 }}>
          <p style={{ margin: 0, color: t.muted, fontSize: 14 }}>Deletion is blocked if related assignments exist.</p>
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
            <GhostButton type="button" onClick={() => setDeleteTeacherOpen(false)} disabled={deleteTeacherBusy}>Cancel</GhostButton>
            <PrimaryButton type="submit" disabled={deleteTeacherBusy}>{deleteTeacherBusy ? "Deleting…" : "Delete"}</PrimaryButton>
          </div>
        </form>
      </Modal>

      <Modal open={typesManageOpen} onClose={() => setTypesManageOpen(false)} title="Staff types" wide>
        <div style={{ display: "grid", gap: 12 }}>
          <p style={{ margin: 0, fontSize: 13, color: t.muted }}>System roles (Teacher, Director, etc.) cannot be edited or deleted here. Remove all users from a custom type before deleting it.</p>
          <DataTable
            columns={[
              { key: "name", label: "Name" },
              { key: "description", label: "Description" },
              { key: "actions", label: "Actions" }
            ]}
            rows={types}
            empty="No types."
            renderCell={(key, row) => {
              if (key === "actions") {
                const locked = row.systemRole === true;
                return (
                  <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                    <GhostButton
                      type="button"
                      disabled={locked || loadingBusy}
                      onClick={() => {
                        setEditTypeForm({ id: row.id, name: row.name || "", description: row.description || "" });
                        setEditTypeOpen(true);
                      }}
                    >Edit</GhostButton>
                    <GhostButton
                      type="button"
                      disabled={locked || loadingBusy}
                      onClick={() => { setDeleteTypeId(row.id); setDeleteTypeOpen(true); }}
                    >Delete</GhostButton>
                  </div>
                );
              }
              return row[key] ?? "—";
            }}
          />
        </div>
      </Modal>

      <Modal open={editTypeOpen} onClose={() => setEditTypeOpen(false)} title="Edit staff type">
        <form onSubmit={saveEditType} style={{ display: "grid", gap: 12 }}>
          <div><Label>Name</Label><Input value={editTypeForm.name} onChange={(e) => setEditTypeForm((p) => ({ ...p, name: e.target.value.toUpperCase() }))} required /></div>
          <div><Label>Description</Label><Input value={editTypeForm.description} onChange={(e) => setEditTypeForm((p) => ({ ...p, description: e.target.value }))} required /></div>
          <PrimaryButton type="submit" disabled={loadingBusy}>{loadingBusy ? "Saving…" : "Save"}</PrimaryButton>
        </form>
      </Modal>

      <Modal open={deleteTypeOpen} onClose={() => setDeleteTypeOpen(false)} title="Delete staff type?">
        <form onSubmit={confirmDeleteType} style={{ display: "grid", gap: 12 }}>
          <p style={{ margin: 0, color: t.muted, fontSize: 14 }}>Only types with no assigned users can be deleted.</p>
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
            <GhostButton type="button" onClick={() => setDeleteTypeOpen(false)} disabled={loadingBusy}>Cancel</GhostButton>
            <PrimaryButton type="submit" disabled={loadingBusy}>{loadingBusy ? "Deleting…" : "Delete"}</PrimaryButton>
          </div>
        </form>
      </Modal>

      <Modal open={subjectsManageOpen} onClose={() => setSubjectsManageOpen(false)} title="Subjects" wide>
        <div style={{ display: "grid", gap: 16 }}>
          <form onSubmit={createSubjectFromMgmt} style={{ display: "grid", gridTemplateColumns: "1fr auto", gap: 8, alignItems: "end" }}>
            <div><Label>New subject</Label><Input value={subjectForm.name} onChange={(e) => setSubjectForm((p) => ({ ...p, name: e.target.value }))} placeholder="e.g. Mathematics" /></div>
            <PrimaryButton type="submit" disabled={loadingBusy}>Add</PrimaryButton>
          </form>
          <DataTable
            columns={[
              { key: "name", label: "Name" },
              { key: "actions", label: "Actions" }
            ]}
            rows={subjects}
            empty="No subjects yet."
            renderCell={(key, row) => {
              if (key === "actions") {
                return (
                  <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                    <GhostButton type="button" disabled={loadingBusy} onClick={() => { setEditSubjectForm({ id: row.id, name: row.name || "" }); setEditSubjectOpen(true); }}>Edit</GhostButton>
                    <GhostButton type="button" disabled={loadingBusy} onClick={() => { setDeleteSubjectId(row.id); setDeleteSubjectOpen(true); }}>Delete</GhostButton>
                  </div>
                );
              }
              return row[key] ?? "—";
            }}
          />
        </div>
      </Modal>

      <Modal open={editSubjectOpen} onClose={() => setEditSubjectOpen(false)} title="Edit subject">
        <form onSubmit={saveEditSubject} style={{ display: "grid", gap: 12 }}>
          <div><Label>Name</Label><Input value={editSubjectForm.name} onChange={(e) => setEditSubjectForm((p) => ({ ...p, name: e.target.value }))} required /></div>
          <PrimaryButton type="submit" disabled={loadingBusy}>{loadingBusy ? "Saving…" : "Save"}</PrimaryButton>
        </form>
      </Modal>

      <Modal open={deleteSubjectOpen} onClose={() => setDeleteSubjectOpen(false)} title="Delete subject?">
        <form onSubmit={confirmDeleteSubject} style={{ display: "grid", gap: 12 }}>
          <p style={{ margin: 0, color: t.muted, fontSize: 14 }}>Subjects assigned to teachers cannot be deleted.</p>
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
            <GhostButton type="button" onClick={() => setDeleteSubjectOpen(false)} disabled={loadingBusy}>Cancel</GhostButton>
            <PrimaryButton type="submit" disabled={loadingBusy}>{loadingBusy ? "Deleting…" : "Delete"}</PrimaryButton>
          </div>
        </form>
      </Modal>
    </>
  );
}

function TeachersPage({ headers, isSuperAdmin }) {
  const { str } = useI18n();
  const [teachers, setTeachers] = useState([]);
  const [schools, setSchools] = useState([]);
  const [subjects, setSubjects] = useState([]);
  const [filterSchoolId, setFilterSchoolId] = useState("");
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState({ name: "", subjectId: "", schoolId: "", username: "", password: "", email: "", phone: "", city: "", subCity: "", wereda: "" });
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editBusy, setEditBusy] = useState(false);
  const [editForm, setEditForm] = useState({ id: "", name: "", subjectId: "", schoolId: "" });
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteTeacherId, setDeleteTeacherId] = useState("");
  const [message, setMessage] = useState(null);

  const loadTeachers = () => {
    const url = filterSchoolId ? `${API_BASE}/teachers?schoolId=${encodeURIComponent(filterSchoolId)}` : `${API_BASE}/teachers`;
    return fetch(url, { headers }).then((r) => r.json()).then(setTeachers);
  };
  useEffect(() => {
    fetch(`${API_BASE}/schools`, { headers }).then((r) => r.json()).then(setSchools);
    fetch(`${API_BASE}/school-stuff/subjects`, { headers }).then((r) => r.json()).then(setSubjects).catch(() => {});
  }, [headers]);
  useEffect(() => {
    loadTeachers();
  }, [filterSchoolId, headers]);

  const createTeacher = async (e) => {
    e.preventDefault();
    const body = isSuperAdmin
      ? form
      : { name: form.name, subjectId: form.subjectId, schoolId: form.schoolId, username: form.username, password: form.password, email: form.email, phone: form.phone };
    const res = await fetch(`${API_BASE}/teachers`, { method: "POST", headers: jsonHeaders(headers), body: JSON.stringify(body) });
    if (!res.ok) {
      let details = null;
      try {
        details = await res.json();
      } catch (_) {}
      setMessage({ type: "error", text: details?.message || "Could not create teacher." });
      return;
    }
    setMessage({ type: "ok", text: "Teacher added." });
    setForm({ name: "", subjectId: "", schoolId: "", username: "", password: "", email: "", phone: "", city: "", subCity: "", wereda: "" });
    setModalOpen(false);
    loadTeachers();
  };

  const openEditTeacher = (row) => {
    setEditForm({
      id: row.id,
      name: row.name ?? "",
      subjectId: row.subjectId ?? "",
      schoolId: row.schoolId ?? ""
    });
    setEditModalOpen(true);
  };

  const saveEditTeacher = async (e) => {
    e.preventDefault();
    if (!editForm.id) return;
    setEditBusy(true);
    try {
      const res = await fetch(`${API_BASE}/teachers/${editForm.id}`, {
        method: "PATCH",
        headers: jsonHeaders(headers),
        body: JSON.stringify({
          name: editForm.name,
          subjectId: editForm.subjectId,
          schoolId: editForm.schoolId
        })
      });
      if (!res.ok) {
        let details = null;
        try {
          details = await res.json();
        } catch (_) {}
        throw new Error(details?.message || "Could not update teacher.");
      }
      setEditModalOpen(false);
      setMessage({ type: "ok", text: "Teacher updated." });
      await loadTeachers();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setEditBusy(false);
    }
  };

  const openDeleteTeacher = (id) => {
    setDeleteTeacherId(id);
    setDeleteModalOpen(true);
  };

  const confirmDeleteTeacher = async (e) => {
    e.preventDefault();
    if (!deleteTeacherId) return;
    setDeleteBusy(true);
    try {
      const res = await fetch(`${API_BASE}/teachers/${deleteTeacherId}`, { method: "DELETE", headers });
      if (!res.ok) {
        let details = null;
        try {
          details = await res.json();
        } catch (_) {}
        throw new Error(details?.message || "Could not delete teacher.");
      }
      setDeleteModalOpen(false);
      setMessage({ type: "ok", text: "Teacher deleted." });
      await loadTeachers();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setDeleteBusy(false);
      setDeleteTeacherId("");
    }
  };

  return (
    <>
      <PageHeader title={str.teachersTitle} subtitle={str.teachersSubtitle} action={<PrimaryButton onClick={() => setModalOpen(true)}>{str.addTeacher}</PrimaryButton>} />
      {message && <Alert type={message.type}>{message.text}</Alert>}
      <div style={{ marginBottom: 16 }}>
        <Label>{str.schoolFilter}</Label>
        <select
          value={filterSchoolId}
          onChange={(e) => setFilterSchoolId(e.target.value)}
          style={{ fontFamily: t.font, padding: "10px 12px", borderRadius: t.radius, border: `1px solid ${t.line}`, minWidth: 220 }}
        >
          <option value="">{str.allInScope}</option>
          {schools.map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </select>
        <GhostButton onClick={loadTeachers} style={{ marginLeft: 8 }}>{str.refresh}</GhostButton>
      </div>
      <DataTable
        columns={[
          { key: "name", label: str.name },
          { key: "subject", label: "Subject" },
          { key: "schoolName", label: str.tabSchools },
          { key: "actions", label: str.actions }
        ]}
        rows={teachers}
        empty="No teachers."
        renderCell={(key, row) => {
          if (key === "actions") {
            return (
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <GhostButton onClick={() => openEditTeacher(row)} disabled={editBusy}>Edit</GhostButton>
                <GhostButton onClick={() => openDeleteTeacher(row.id)} disabled={deleteBusy}>Delete</GhostButton>
              </div>
            );
          }
          return row[key];
        }}
      />

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="New teacher" wide>
        {!isSuperAdmin && <p style={{ fontSize: 13, color: t.muted, marginTop: 0 }}>Optional accounts inherit your location.</p>}
        <form onSubmit={createTeacher} style={{ display: "grid", gap: 12 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div><Label>Full name</Label><Input value={form.name} onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))} required /></div>
            <div>
              <Label>Subject</Label>
              <select
                required
                value={form.subjectId}
                onChange={(e) => setForm((p) => ({ ...p, subjectId: e.target.value }))}
                style={{ fontFamily: t.font, width: "100%", padding: "10px 12px", borderRadius: t.radius, border: `1px solid ${t.line}` }}
              >
                <option value="">Select…</option>
                {subjects.map((s) => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </select>
            </div>
          </div>
          <div><Label>School</Label>
            <select
              required
              value={form.schoolId}
              onChange={(e) => setForm((p) => ({ ...p, schoolId: e.target.value }))}
              style={{ fontFamily: t.font, width: "100%", padding: "10px 12px", borderRadius: t.radius, border: `1px solid ${t.line}` }}
            >
              <option value="">Select…</option>
              {schools.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          </div>
          <p style={{ fontSize: 12, color: t.muted, margin: 0 }}>Optional login for teacher</p>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div><Label>Username</Label><Input value={form.username} onChange={(e) => setForm((p) => ({ ...p, username: e.target.value }))} /></div>
            <div><Label>Password</Label><Input type="password" value={form.password} onChange={(e) => setForm((p) => ({ ...p, password: e.target.value }))} /></div>
          </div>
          <div><Label>Email</Label><Input type="email" value={form.email} onChange={(e) => setForm((p) => ({ ...p, email: e.target.value }))} /></div>
          <div><Label>Phone</Label><Input value={form.phone} onChange={(e) => setForm((p) => ({ ...p, phone: e.target.value }))} placeholder="Optional" /></div>
          {isSuperAdmin && (
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8 }}>
              <div><Label>City</Label><Input value={form.city} onChange={(e) => setForm((p) => ({ ...p, city: e.target.value }))} /></div>
              <div><Label>Sub city</Label><Input value={form.subCity} onChange={(e) => setForm((p) => ({ ...p, subCity: e.target.value }))} /></div>
              <div><Label>Wereda</Label><Input value={form.wereda} onChange={(e) => setForm((p) => ({ ...p, wereda: e.target.value }))} /></div>
            </div>
          )}
          <PrimaryButton type="submit">Save</PrimaryButton>
        </form>
      </Modal>

      <Modal
        open={editModalOpen}
        onClose={() => (!editBusy ? setEditModalOpen(false) : null)}
        title="Edit teacher"
        wide
      >
        <form onSubmit={saveEditTeacher} style={{ display: "grid", gap: 12 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div><Label>Full name</Label><Input value={editForm.name} onChange={(e) => setEditForm((p) => ({ ...p, name: e.target.value }))} required /></div>
            <div>
              <Label>Subject</Label>
              <select
                required
                value={editForm.subjectId}
                onChange={(e) => setEditForm((p) => ({ ...p, subjectId: e.target.value }))}
                style={{ fontFamily: t.font, width: "100%", padding: "10px 12px", borderRadius: t.radius, border: `1px solid ${t.line}` }}
              >
                <option value="">Select…</option>
                {subjects.map((s) => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </select>
            </div>
          </div>
          <div><Label>School</Label>
            <select
              required
              value={editForm.schoolId}
              onChange={(e) => setEditForm((p) => ({ ...p, schoolId: e.target.value }))}
              style={{ fontFamily: t.font, width: "100%", padding: "10px 12px", borderRadius: t.radius, border: `1px solid ${t.line}` }}
            >
              <option value="">Select…</option>
              {schools.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          </div>
          <PrimaryButton type="submit" disabled={editBusy}>{editBusy ? "Saving…" : "Save changes"}</PrimaryButton>
        </form>
      </Modal>

      <Modal
        open={deleteModalOpen}
        onClose={() => (!deleteBusy ? setDeleteModalOpen(false) : null)}
        title="Delete teacher"
      >
        <form onSubmit={confirmDeleteTeacher} style={{ display: "grid", gap: 12 }}>
          <p style={{ margin: 0, color: t.muted, fontSize: 14 }}>Deletion is blocked if the teacher has assignments.</p>
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
            <GhostButton type="button" onClick={() => setDeleteModalOpen(false)} disabled={deleteBusy}>Cancel</GhostButton>
            <PrimaryButton type="submit" disabled={deleteBusy}>{deleteBusy ? "Deleting…" : "Delete"}</PrimaryButton>
          </div>
        </form>
      </Modal>
    </>
  );
}

function ChecklistsPage({ headers, isSuperAdmin, onOpenChecklistItems }) {
  const { str } = useI18n();
  const SUPPORTED_LANGS = ["en", "am"];
  const LANG_LABEL = { en: "English", am: "Amharic" };
  const [items, setItems] = useState([]);
  const [gradeGroups, setGradeGroups] = useState([]);
  const [typeDefaults, setTypeDefaults] = useState(null); // { [itemType]: { options: {}, validation: {} } }
  const [typeDefaultsLoading, setTypeDefaultsLoading] = useState(false);
  const [typeDefaultsModalOpen, setTypeDefaultsModalOpen] = useState(false);
  const [typeDefaultsEditingType, setTypeDefaultsEditingType] = useState("YES_NO");
  const [typeDefaultsDraftChoices, setTypeDefaultsDraftChoices] = useState(["YES", "NO"]);
  const [typeDefaultsDraftRating, setTypeDefaultsDraftRating] = useState({ min: 1, max: 5 });
  const [typeDefaultsSaving, setTypeDefaultsSaving] = useState(false);
  const [editingLang, setEditingLang] = useState("en");
  const [ggForm, setGgForm] = useState({ displayName: "", gradeCodes: [] });
  const [title, setTitle] = useState("");
  const [targetOptions, setTargetOptions] = useState([]);
  const [purposeOptions, setPurposeOptions] = useState([]);
  const [targetOptionId, setTargetOptionId] = useState("");
  const [purposeOptionId, setPurposeOptionId] = useState("");
  const [gradeGroupId, setGradeGroupId] = useState("");
  const [autoAssignOnPublish, setAutoAssignOnPublish] = useState(true);
  const [autoAssignDueAt, setAutoAssignDueAt] = useState("");
  const [skipAutoOnPublish, setSkipAutoOnPublish] = useState(false);
  const [publishingChecklistId, setPublishingChecklistId] = useState("");
  const [draftSections, setDraftSections] = useState(() => [
    { _id: DEFAULT_CHECKLIST_SECTION_ID, name: "General" }
  ]);
  const draftSectionsRef = useRef(draftSections);
  useEffect(() => {
    draftSectionsRef.current = draftSections;
  }, [draftSections]);
  const [draftItems, setDraftItems] = useState(() => [
    {
      _id: newChecklistItemRowId(),
      sectionId: DEFAULT_CHECKLIST_SECTION_ID,
      question: "",
      questionLocalizedText: JSON.stringify({ en: "", am: "" }),
      type: "YES_NO",
      order: 1,
      optionsText: JSON.stringify({ choices: ["YES", "NO"], choicesLocalized: { en: ["YES", "NO"], am: ["YES", "NO"] } }),
      validationText: '{"required":true}'
    }
  ]);
  const [message, setMessage] = useState(null);
  const [checklistSubTab, setChecklistSubTab] = useState("new");
  const [ggModal, setGgModal] = useState(false);

  const defaultDraftItems = useMemo(() => {
    const yesNoDefaults = typeDefaults?.YES_NO ?? { options: { choices: ["YES", "NO"] }, validation: { required: true } };
    const options = yesNoDefaults.options ?? { choices: ["YES", "NO"] };
    const validation = yesNoDefaults.validation ?? { required: true };
    const baseChoices = Array.isArray(options.choices) && options.choices.length ? options.choices : ["YES", "NO"];
    return [
      {
        _id: newChecklistItemRowId(),
        sectionId: DEFAULT_CHECKLIST_SECTION_ID,
        question: "",
        questionLocalizedText: JSON.stringify({ en: "", am: "" }),
        type: "YES_NO",
        order: 1,
        optionsText: JSON.stringify({ choices: baseChoices, choicesLocalized: { en: baseChoices, am: baseChoices } }),
        validationText: JSON.stringify(validation)
      }
    ];
  }, [typeDefaults]);

  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editBusy, setEditBusy] = useState(false);
  const [editForm, setEditForm] = useState({
    id: "",
    title: "",
    targetOptionId: "",
    purposeOptionId: "",
    targetType: "SCHOOL",
    gradeGroupId: "",
    autoAssignOnPublish: true,
    autoAssignDueAt: ""
  });
  const [targetOptionFormOpen, setTargetOptionFormOpen] = useState(false);
  const [targetOptionForm, setTargetOptionForm] = useState({ id: null, name: "", routingKind: "SCHOOL" });
  const [purposeOptionFormOpen, setPurposeOptionFormOpen] = useState(false);
  const [purposeOptionForm, setPurposeOptionForm] = useState({ id: null, name: "" });
  const [optionsSaving, setOptionsSaving] = useState(false);

  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteChecklistId, setDeleteChecklistId] = useState("");
  const [toggleBusyId, setToggleBusyId] = useState("");

  const resetDraftItems = () => {
    setDraftSections([{ _id: DEFAULT_CHECKLIST_SECTION_ID, name: "General" }]);
    setDraftItems(defaultDraftItems);
  };

  const loadChecklists = () => fetch(`${API_BASE}/checklists`, { headers }).then((r) => r.json()).then(setItems);
  const loadGradeGroups = () => fetch(`${API_BASE}/grade-groups`, { headers }).then((r) => r.json()).then(setGradeGroups);
  const loadChecklistOptions = () =>
    Promise.all([
      fetch(`${API_BASE}/checklists/options/targets`, { headers }),
      fetch(`${API_BASE}/checklists/options/purposes`, { headers })
    ]).then(async ([rt, rp]) => {
      const parseJson = async (res) => {
        if (!res.ok) {
          let msg = `${res.status} ${res.statusText}`;
          try {
            const body = await res.json();
            if (body?.message) msg = body.message;
          } catch (_) {}
          throw new Error(msg);
        }
        const text = await res.text();
        if (!text || !text.trim()) return [];
        return JSON.parse(text);
      };
      const [targets, purposes] = await Promise.all([parseJson(rt), parseJson(rp)]);
      setTargetOptions(Array.isArray(targets) ? targets : []);
      setPurposeOptions(Array.isArray(purposes) ? purposes : []);
    })
    .catch((err) => {
      setTargetOptions([]);
      setPurposeOptions([]);
      setMessage({ type: "error", text: err?.message || "Could not load targets and purposes." });
    });
  const loadTypeDefaults = () => {
    setTypeDefaultsLoading(true);
    return fetch(`${API_BASE}/checklists/type-defaults`, { headers })
      .then((r) => r.json())
      .then((list) => {
        const map = {};
        (list || []).forEach((row) => {
          map[row.itemType] = { options: row.options || {}, validation: row.validation || {} };
        });
        setTypeDefaults(map);
      })
      .catch(() => setTypeDefaults(null))
      .finally(() => setTypeDefaultsLoading(false));
  };

  useEffect(() => {
    loadChecklists();
    loadGradeGroups();
    loadTypeDefaults();
    loadChecklistOptions();
  }, [headers]);

  useEffect(() => {
    if (targetOptions.length && !targetOptionId) {
      setTargetOptionId(targetOptions[0].id);
    }
  }, [targetOptions, targetOptionId]);

  useEffect(() => {
    if (purposeOptions.length && !purposeOptionId) {
      setPurposeOptionId(purposeOptions[0].id);
    }
  }, [purposeOptions, purposeOptionId]);

  useEffect(() => {
    if (items.length === 0) {
      setPublishingChecklistId("");
      return;
    }
    if (publishingChecklistId && items.some((c) => c.id === publishingChecklistId)) return;
    const needsPublish = items.find((c) => c.activeVersion == null);
    setPublishingChecklistId((needsPublish ?? items[items.length - 1]).id);
  }, [items]);

  const openGradeGroupModal = () => {
    setGgForm({ displayName: "", gradeCodes: [] });
    setGgModal(true);
  };

  const createGradeGroup = async (e) => {
    e.preventDefault();
    if (!ggForm.gradeCodes || ggForm.gradeCodes.length === 0) {
      setMessage({ type: "error", text: "Select at least one grade for this group." });
      return;
    }
    const res = await fetch(`${API_BASE}/grade-groups`, {
      method: "POST",
      headers: jsonHeaders(headers),
      body: JSON.stringify({ displayName: ggForm.displayName, gradeCodes: ggForm.gradeCodes })
    });
    if (!res.ok) {
      let details = null;
      try {
        details = await res.json();
      } catch (_) {}
      if (!details?.message) {
        // Fallback: try to read response body to avoid hiding the real reason.
        let bodyText = "";
        try {
          bodyText = await res.text();
        } catch (_) {}
        const msg = bodyText
          ? `Grade group failed (${res.status}): ${bodyText}`
          : `Grade group failed (${res.status}).`;
        setMessage({ type: "error", text: msg });
        return;
      }
      const msg = `Grade group failed: ${details.message}`;
      setMessage({ type: "error", text: msg });
      return;
    }
    setGgForm({ displayName: "", gradeCodes: [] });
    setGgModal(false);
    setMessage({ type: "ok", text: "Grade group saved." });
    loadGradeGroups();
  };

  const resetDraftItemsWithDefaults = () => {
    setDraftSections([{ _id: DEFAULT_CHECKLIST_SECTION_ID, name: "General" }]);
    setDraftItems(defaultDraftItems);
  };

  const choiceDefaultTypes = ["SINGLE_CHOICE", "MULTIPLE_CHOICE", "YES_NO"];

  const openTypeDefaultsModal = () => {
    setTypeDefaultsEditingType("YES_NO");
    setTypeDefaultsModalOpen(true);
  };

  useEffect(() => {
    if (!typeDefaultsModalOpen) return;
    const tpl = typeDefaults?.[typeDefaultsEditingType];
    if (choiceDefaultTypes.includes(typeDefaultsEditingType)) {
      const choices = Array.isArray(tpl?.options?.choices) ? tpl?.options?.choices : ["YES", "NO"];
      setTypeDefaultsDraftChoices(choices.map((c) => c?.toString?.() ?? "").filter((c) => c !== null));
    } else if (typeDefaultsEditingType === "RATING") {
      const v = tpl?.validation ?? {};
      const min = typeof v.min === "number" ? v.min : 1;
      const max = typeof v.max === "number" ? v.max : 5;
      setTypeDefaultsDraftRating({ min, max });
    } else {
      setTypeDefaultsDraftChoices([]);
    }
  }, [typeDefaultsModalOpen, typeDefaultsEditingType, typeDefaults]);

  const saveTypeDefaults = async (e) => {
    e.preventDefault();
    if (!typeDefaultsEditingType) return;
    setTypeDefaultsSaving(true);
    try {
      const type = typeDefaultsEditingType;
      const url = `${API_BASE}/checklists/type-defaults/${type}`;

      let payload = {};
      if (choiceDefaultTypes.includes(type)) {
        const options = { choices: typeDefaultsDraftChoices.map((c) => c ?? "").filter((c) => c !== "") };
        const validation = typeDefaults?.[type]?.validation ?? {};
        payload = { options, validation };
      } else if (type === "RATING") {
        const baseValidation = typeDefaults?.[type]?.validation ?? {};
        payload = {
          validation: { ...baseValidation, min: Number(typeDefaultsDraftRating.min), max: Number(typeDefaultsDraftRating.max) }
        };
      } else {
        payload = {};
      }

      const res = await fetch(url, { method: "PATCH", headers: jsonHeaders(headers), body: JSON.stringify(payload) });
      if (!res.ok) {
        let details = null;
        try {
          details = await res.json();
        } catch (_) {}
        throw new Error(details?.message ? details.message : "Update defaults failed.");
      }
      setTypeDefaultsModalOpen(false);
      setMessage({ type: "ok", text: "Default item type updated." });
      await loadTypeDefaults();
      resetDraftItemsWithDefaults();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setTypeDefaultsSaving(false);
    }
  };

  const loadDraftFromChecklist = async (checklistId) => {
    // Used by the "Publish items" section to allow updating an existing checklist.
    if (!checklistId) return;
    try {
      const res = await fetch(`${API_BASE}/checklists/${checklistId}/render`, { headers });
      if (!res.ok) throw new Error("Could not load checklist render (ensure it has an active version).");
      const data = await res.json();
      const { sections, items } = buildEditorStateFromChecklistRender(data, typeDefaults);
      setDraftSections(sections);
      if (items.length) setDraftItems(items);
    } catch (_) {
      resetDraftItemsWithDefaults();
    }
  };

  const openEditChecklistItems = async (checklistId) => {
    if (!checklistId) return;
    if (onOpenChecklistItems) {
      onOpenChecklistItems(checklistId);
      return;
    }
    setPublishingChecklistId(checklistId);
    await loadDraftFromChecklist(checklistId);
    setMessage({ type: "ok", text: "Checklist items loaded for editing. Update and click Publish version." });
  };

  const openEditChecklist = (c) => {
    setEditForm({
      id: c.id,
      title: c.title ?? "",
      targetOptionId: c.targetOptionId ?? "",
      purposeOptionId: c.purposeOptionId ?? "",
      targetType: c.targetType ?? "SCHOOL",
      gradeGroupId: c.gradeGroupId ?? "",
      autoAssignOnPublish: c.autoAssignOnPublish !== false,
      autoAssignDueAt: c.autoAssignDueAt ? new Date(c.autoAssignDueAt).toISOString().slice(0, 16) : ""
    });
    setEditModalOpen(true);
  };

  const saveEditChecklist = async (e) => {
    e.preventDefault();
    if (!editForm.id) return;
    setEditBusy(true);
    try {
      const autoAssignable = CHECKLIST_AUTO_ASSIGN_TARGETS.has(editForm.targetType);
      const autoAssignEnabled = autoAssignable && Boolean(editForm.autoAssignOnPublish);
      if (autoAssignEnabled && !editForm.autoAssignDueAt) {
        throw new Error("Set auto-assignment due date when auto-assign is enabled.");
      }
      const res = await fetch(`${API_BASE}/checklists/${editForm.id}`, {
        method: "PATCH",
        headers: jsonHeaders(headers),
        body: JSON.stringify({
          title: editForm.title,
          targetOptionId: editForm.targetOptionId,
          purposeOptionId: editForm.purposeOptionId,
          gradeGroupId: editForm.gradeGroupId,
          autoAssignOnPublish: autoAssignEnabled,
          autoAssignDueAt: autoAssignEnabled && editForm.autoAssignDueAt ? new Date(editForm.autoAssignDueAt).toISOString() : null
        })
      });
      if (!res.ok) {
        let details = null;
        try {
          details = await res.json();
        } catch (_) {}
        throw new Error(details?.message ? details.message : "Checklist update failed.");
      }
      setEditModalOpen(false);
      setMessage({ type: "ok", text: "Checklist updated." });
      await loadChecklists();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setEditBusy(false);
    }
  };

  const openDeleteChecklist = (id) => {
    setDeleteChecklistId(id);
    setDeleteModalOpen(true);
  };

  const confirmDeleteChecklist = async (e) => {
    e.preventDefault();
    if (!deleteChecklistId) return;
    setDeleteBusy(true);
    try {
      const res = await fetch(`${API_BASE}/checklists/${deleteChecklistId}`, { method: "DELETE", headers });
      if (!res.ok) {
        let details = null;
        try {
          details = await res.json();
        } catch (_) {}
        throw new Error(details?.message ? details.message : "Checklist delete failed.");
      }
      setDeleteModalOpen(false);
      setMessage({ type: "ok", text: "Checklist deleted." });
      await loadChecklists();
      resetDraftItemsWithDefaults();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setDeleteBusy(false);
      setDeleteChecklistId("");
    }
  };

  const toggleChecklist = async (id, enable) => {
    if (!id) return;
    setToggleBusyId(id);
    try {
      const endpoint = enable ? "enable" : "disable";
      const res = await fetch(`${API_BASE}/checklists/${id}/${endpoint}`, { method: "PATCH", headers });
      if (!res.ok) {
        let details = null;
        try {
          details = await res.json();
        } catch (_) {}
        throw new Error(details?.message ? details.message : "Checklist toggle failed.");
      }
      setMessage({ type: "ok", text: enable ? "Checklist enabled." : "Checklist disabled." });
      await loadChecklists();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setToggleBusyId("");
    }
  };

  const saveTargetOptionForm = async (e) => {
    e.preventDefault();
    const name = targetOptionForm.name.trim();
    if (!name) {
      setMessage({ type: "error", text: "Enter a target name." });
      return;
    }
    setOptionsSaving(true);
    try {
      const body = { name, routingKind: targetOptionForm.routingKind };
      const url = targetOptionForm.id
        ? `${API_BASE}/checklists/options/targets/${targetOptionForm.id}`
        : `${API_BASE}/checklists/options/targets`;
      const res = await fetch(url, {
        method: targetOptionForm.id ? "PATCH" : "POST",
        headers: jsonHeaders(headers),
        body: JSON.stringify(body)
      });
      if (!res.ok) {
        let details = null;
        try {
          details = await res.json();
        } catch (_) {}
        throw new Error(details?.message ? details.message : "Save failed.");
      }
      setTargetOptionFormOpen(false);
      setMessage({ type: "ok", text: "Target saved." });
      await loadChecklistOptions();
      await loadChecklists();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setOptionsSaving(false);
    }
  };

  const deleteTargetOption = async (id) => {
    if (!window.confirm("Delete this target? It must not be used by any checklist.")) return;
    try {
      const res = await fetch(`${API_BASE}/checklists/options/targets/${id}`, { method: "DELETE", headers });
      if (!res.ok) {
        let details = null;
        try {
          details = await res.json();
        } catch (_) {}
        throw new Error(details?.message ? details.message : "Delete failed.");
      }
      setMessage({ type: "ok", text: "Target deleted." });
      await loadChecklistOptions();
      await loadChecklists();
      if (targetOptionId === id) setTargetOptionId("");
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    }
  };

  const savePurposeOptionForm = async (e) => {
    e.preventDefault();
    const name = purposeOptionForm.name.trim();
    if (!name) {
      setMessage({ type: "error", text: "Enter a purpose name." });
      return;
    }
    setOptionsSaving(true);
    try {
      const body = { name };
      const url = purposeOptionForm.id
        ? `${API_BASE}/checklists/options/purposes/${purposeOptionForm.id}`
        : `${API_BASE}/checklists/options/purposes`;
      const res = await fetch(url, {
        method: purposeOptionForm.id ? "PATCH" : "POST",
        headers: jsonHeaders(headers),
        body: JSON.stringify(body)
      });
      if (!res.ok) {
        let details = null;
        try {
          details = await res.json();
        } catch (_) {}
        throw new Error(details?.message ? details.message : "Save failed.");
      }
      setPurposeOptionFormOpen(false);
      setMessage({ type: "ok", text: "Purpose saved." });
      await loadChecklistOptions();
      await loadChecklists();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setOptionsSaving(false);
    }
  };

  const deletePurposeOption = async (id) => {
    if (!window.confirm("Delete this purpose? It must not be used by any checklist.")) return;
    try {
      const res = await fetch(`${API_BASE}/checklists/options/purposes/${id}`, { method: "DELETE", headers });
      if (!res.ok) {
        let details = null;
        try {
          details = await res.json();
        } catch (_) {}
        throw new Error(details?.message ? details.message : "Delete failed.");
      }
      setMessage({ type: "ok", text: "Purpose deleted." });
      await loadChecklistOptions();
      await loadChecklists();
      if (purposeOptionId === id) setPurposeOptionId("");
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    }
  };

  const create = async (e) => {
    e.preventDefault();
    if (!gradeGroupId) {
      setMessage({ type: "error", text: "Select a grade group." });
      return;
    }
    if (!targetOptionId || !purposeOptionId) {
      setMessage({ type: "error", text: "Select a target and purpose (load options or add them under Targets & purposes)." });
      return;
    }
    const routing =
      targetOptions.find((o) => o.id === targetOptionId)?.routingKind ?? "SCHOOL";
    const autoAssignEnabled = CHECKLIST_AUTO_ASSIGN_TARGETS.has(routing) && Boolean(autoAssignOnPublish);
    if (autoAssignEnabled && !autoAssignDueAt) {
      setMessage({ type: "error", text: "Set auto-assignment due date when auto-assign is enabled." });
      return;
    }
    const res = await fetch(`${API_BASE}/checklists`, {
      method: "POST",
      headers: jsonHeaders(headers),
        body: JSON.stringify({
        title,
        targetOptionId,
        purposeOptionId,
        gradeGroupId,
        autoAssignOnPublish: autoAssignEnabled,
        autoAssignDueAt: autoAssignEnabled && autoAssignDueAt ? new Date(autoAssignDueAt).toISOString() : null
      })
    });
    if (!res.ok) {
      setMessage({ type: "error", text: "Checklist create failed." });
      return;
    }
    const newId = await res.json();
    setTitle("");
    setAutoAssignOnPublish(true);
    setAutoAssignDueAt("");
    setMessage({ type: "ok", text: "Checklist created." });
    await loadChecklists();
    if (newId) setPublishingChecklistId(newId);
  };

  const addDraftItem = (sectionId) => {
    if (!sectionId) return;
    setDraftItems((prev) => {
      const sections = draftSectionsRef.current;
      const newRow = {
        _id: newChecklistItemRowId(),
        sectionId,
        question: "",
        questionLocalizedText: JSON.stringify({ en: "", am: "" }),
        type: "TEXT",
        order: prev.length + 1,
        optionsText: "{}",
        validationText: '{"required":false}'
      };
      const secOrder = sections.findIndex((s) => s._id === sectionId);
      if (secOrder < 0) {
        return [...prev, newRow];
      }
      let lastInSec = -1;
      for (let i = 0; i < prev.length; i++) {
        if (prev[i].sectionId === sectionId) lastInSec = i;
      }
      if (lastInSec >= 0) {
        const next = [...prev];
        next.splice(lastInSec + 1, 0, newRow);
        return next;
      }
      let lastBefore = -1;
      for (let i = 0; i < prev.length; i++) {
        const sid = prev[i].sectionId;
        const si = sections.findIndex((s) => s._id === sid);
        if (si >= 0 && si < secOrder) lastBefore = i;
      }
      const next = [...prev];
      next.splice(lastBefore + 1, 0, newRow);
      return next;
    });
  };

  const removeDraftItem = (index) => {
    setDraftItems((prev) => prev.filter((_, i) => i !== index));
  };

  const addSection = () => {
    const newSec = { _id: newChecklistItemRowId(), name: "New section" };
    setDraftSections((s) => [...s, newSec]);
  };

  const updateSectionName = (sectionId, name) => {
    setDraftSections((secs) => secs.map((s) => (s._id === sectionId ? { ...s, name } : s)));
  };

  const moveSection = (index, direction) => {
    setDraftSections((sections) => {
      const j = index + direction;
      if (j < 0 || j >= sections.length) return sections;
      const reordered = [...sections];
      [reordered[index], reordered[j]] = [reordered[j], reordered[index]];
      setDraftItems((items) => orderItemsToMatchSectionOrder(reordered, items));
      return reordered;
    });
  };

  const removeSection = (index) => {
    setDraftSections((secs) => {
      if (secs.length <= 1) return secs;
      const targetId = index > 0 ? secs[index - 1]._id : secs[1]._id;
      const removed = secs[index]._id;
      const nextSecs = secs.filter((_, i) => i !== index);
      setDraftItems((items) => {
        const reassign = items.map((it) => (it.sectionId === removed ? { ...it, sectionId: targetId } : it));
        return orderItemsToMatchSectionOrder(nextSecs, reassign);
      });
      return nextSecs;
    });
  };

  const publishVersion = async (e) => {
    e.preventDefault();
    if (!publishingChecklistId) return;
    try {
      const normName = (s) => (s?.name ?? "").trim().toLowerCase() || "general";
      const secNames = draftSections.map((s) => normName(s));
      if (new Set(secNames).size !== secNames.length) {
        setMessage({ type: "error", text: "Section names must be unique (after trimming)." });
        return;
      }
      const groupKeyFor = (item) => {
        const sec = draftSections.find((s) => s._id === item.sectionId);
        const g = (sec?.name ?? "").trim() || "General";
        return g;
      };
      const payload = {
        items: draftItems.map((item, index) => ({
          question: item.question,
          questionLocalized: JSON.parse(item.questionLocalizedText || "{\"en\":\"\",\"am\":\"\"}"),
          type: item.type,
          options: JSON.parse(item.optionsText || "{}"),
          validation: JSON.parse(item.validationText || "{}"),
          groupKey: groupKeyFor(item) || null,
          order: index + 1
        })),
        skipAutoAssignment: Boolean(skipAutoOnPublish)
      };
      const res = await fetch(`${API_BASE}/checklists/${publishingChecklistId}/versions`, { method: "POST", headers: jsonHeaders(headers), body: JSON.stringify(payload) });
      if (!res.ok) {
        setMessage({ type: "error", text: "Publish failed." });
        return;
      }
      setMessage({ type: "ok", text: "Version published." });
      loadChecklists();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    }
  };

  const safeJsonParse = (txt, fallback) => {
    try {
      if (!txt) return fallback;
      return JSON.parse(txt);
    } catch (_) {
      return fallback;
    }
  };

  return (
    <>
      <PageHeader
        title={str.checklistsTitle}
        subtitle={str.checklistsSubtitle}
        action={<GhostButton onClick={openGradeGroupModal}>{str.newGradeGroup}</GhostButton>}
      />
      {message && <Alert type={message.type}>{message.text}</Alert>}

      <div style={{ display: "flex", gap: 8, marginBottom: 16, flexWrap: "wrap" }}>
        <GhostButton
          onClick={() => setChecklistSubTab("new")}
          ariaLabel="Show new checklist section"
          style={checklistSubTab === "new" ? { background: t.accentSoft, borderColor: t.accent } : undefined}
        >
          New checklist
        </GhostButton>
        <GhostButton
          onClick={() => setChecklistSubTab("publish")}
          ariaLabel="Show publish items section"
          style={checklistSubTab === "publish" ? { background: t.accentSoft, borderColor: t.accent } : undefined}
        >
          Publish items
        </GhostButton>
        <GhostButton
          onClick={() => setChecklistSubTab("options")}
          ariaLabel="Manage targets and purposes"
          style={checklistSubTab === "options" ? { background: t.accentSoft, borderColor: t.accent } : undefined}
        >
          Targets & purposes
        </GhostButton>
      </div>

      {checklistSubTab === "new" && (
        <>
          <Card style={{ padding: 20, marginBottom: 20 }}>
            <h2 style={{ margin: "0 0 12px", fontSize: 15, fontWeight: 600 }}>{str.newChecklist}</h2>
            <form onSubmit={create} style={{ display: "grid", gap: 12, maxWidth: 480 }}>
              <div>
                <Label>Title</Label>
                <Input placeholder="e.g. Classroom observation" value={title} onChange={(e) => setTitle(e.target.value)} required aria-label="Checklist title" />
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                <div>
                  <Label>Target</Label>
                  <select
                    value={targetOptionId}
                    onChange={(e) => setTargetOptionId(e.target.value)}
                    aria-label="Target audience"
                    style={{ padding: 10, borderRadius: t.radius, border: `1px solid ${t.line}`, fontFamily: t.font, width: "100%" }}
              >
                    <option value="">Select…</option>
                    {targetOptions.map((o) => (
                      <option key={o.id} value={o.id}>
                        {o.name} ({o.routingKind})
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <Label>Purpose</Label>
                  <select
                    value={purposeOptionId}
                    onChange={(e) => setPurposeOptionId(e.target.value)}
                    aria-label="Checklist purpose"
                    style={{ padding: 10, borderRadius: t.radius, border: `1px solid ${t.line}`, fontFamily: t.font, width: "100%" }}
              >
                    <option value="">Select…</option>
                    {purposeOptions.map((o) => (
                      <option key={o.id} value={o.id}>
                        {o.name}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div>
                <Label>Grade group</Label>
                <select value={gradeGroupId} onChange={(e) => setGradeGroupId(e.target.value)} required aria-label="Grade group" style={{ padding: 10, borderRadius: t.radius, border: `1px solid ${t.line}`, fontFamily: t.font, width: "100%" }}>
                <option value="">Select a grade group…</option>
                {gradeGroups.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.displayName} — {Array.isArray(g.gradeCodes) && g.gradeCodes.length ? g.gradeCodes.join(", ") : g.gradesDescription}
                  </option>
                ))}
                </select>
              </div>
              <label style={{ display: "flex", alignItems: "center", gap: 10, fontSize: 14, color: t.text }}>
                <input
                  type="checkbox"
                  checked={autoAssignOnPublish}
                  onChange={(e) => setAutoAssignOnPublish(e.target.checked)}
                  disabled={
                    !CHECKLIST_AUTO_ASSIGN_TARGETS.has(
                      targetOptions.find((o) => o.id === targetOptionId)?.routingKind ?? ""
                    )
                  }
                  aria-label="Auto-assign on publish"
                />
                Auto-assign supervisors to matching schools when this checklist is published (school or director routing)
              </label>
              {CHECKLIST_AUTO_ASSIGN_TARGETS.has(targetOptions.find((o) => o.id === targetOptionId)?.routingKind ?? "") && autoAssignOnPublish && (
                <div>
                  <Label>Auto-assignment due date</Label>
                  <Input
                    type="datetime-local"
                    value={autoAssignDueAt}
                    onChange={(e) => setAutoAssignDueAt(e.target.value)}
                    required
                    aria-label="Auto-assignment due date"
                  />
                </div>
              )}
              <PrimaryButton type="submit">Create checklist</PrimaryButton>
            </form>
          </Card>

          {isSuperAdmin && (
            <Card style={{ padding: 20, marginBottom: 20 }}>
              <h2 style={{ margin: "0 0 12px", fontSize: 15, fontWeight: 600 }}>Default item types</h2>
              <p style={{ margin: "0 0 12px", color: t.muted, fontSize: 13 }}>
                Edit the default answer choices for <code>SINGLE_CHOICE</code>, <code>MULTIPLE_CHOICE</code>, <code>YES_NO</code>, and the rating range for <code>RATING</code>.
              </p>
              <PrimaryButton type="button" onClick={openTypeDefaultsModal} disabled={typeDefaultsLoading}>
                {typeDefaultsLoading ? "Loading…" : "Edit defaults"}
              </PrimaryButton>
            </Card>
          )}
        </>
      )}

      {checklistSubTab === "publish" && (
        <>
          <Card style={{ padding: 20, marginBottom: 20 }}>
            <h2 style={{ margin: "0 0 12px", fontSize: 15, fontWeight: 600 }}>Publish items</h2>
            <form onSubmit={publishVersion} style={{ display: "grid", gap: 12 }}>
          <div>
            <Label>Checklist to publish/update</Label>
            <select
              value={publishingChecklistId}
              onChange={(e) => {
                const nextId = e.target.value;
                setPublishingChecklistId(nextId);
                // Load existing checklist items so coordinators can update from current definition.
                loadDraftFromChecklist(nextId);
              }}
              style={{ padding: 10, borderRadius: t.radius, border: `1px solid ${t.line}`, fontFamily: t.font, width: "100%" }}
            >
              {items.length === 0 && <option value="">No checklists</option>}
              {items.map((c) => (
                <option key={c.id} value={c.id}>{c.title}{c.activeVersion == null ? " (draft)" : ` · v${c.activeVersion}`}</option>
              ))}
            </select>
          </div>
          <div>
            <Label>Edit language</Label>
            <select
              value={editingLang}
              onChange={(e) => setEditingLang(e.target.value)}
              style={{ padding: 10, borderRadius: t.radius, border: `1px solid ${t.line}`, fontFamily: t.font, width: "100%" }}
            >
              {SUPPORTED_LANGS.map((l) => (
                <option key={l} value={l}>
                  {LANG_LABEL[l] ?? l}
                </option>
              ))}
            </select>
          </div>
          {draftSections.map((section, secIndex) => (
            <div key={section._id} style={{ display: "grid", gap: 10, padding: 14, borderRadius: t.radius, border: `1px solid ${t.line}`, background: t.card }}>
              <div style={{ display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center" }}>
                <Label style={{ margin: 0 }}>{str.sectionNameLabel}</Label>
                <Input
                  value={section.name}
                  onChange={(e) => updateSectionName(section._id, e.target.value)}
                  placeholder="General"
                  style={{ minWidth: 180, maxWidth: 400, flex: 1 }}
                />
                <GhostButton
                  type="button"
                  disabled={secIndex <= 0}
                  onClick={() => moveSection(secIndex, -1)}
                  title={str.moveSectionUp}
                >
                  ↑
                </GhostButton>
                <GhostButton
                  type="button"
                  disabled={secIndex >= draftSections.length - 1}
                  onClick={() => moveSection(secIndex, 1)}
                  title={str.moveSectionDown}
                >
                  ↓
                </GhostButton>
                <GhostButton
                  type="button"
                  disabled={draftSections.length <= 1}
                  onClick={() => removeSection(secIndex)}
                >
                  {str.removeSection}
                </GhostButton>
                <GhostButton type="button" onClick={() => addDraftItem(section._id)}>
                  {str.addChecklistItem}
                </GhostButton>
              </div>
          {draftItems.map((item, index) => {
            if (item.sectionId !== section._id) return null;
            return (
            <Card key={item._id ?? `item-${index}`} style={{ padding: 14, background: t.accentSoft }}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
                <strong style={{ fontSize: 13 }}>Item {index + 1}</strong>
                <GhostButton type="button" onClick={() => removeDraftItem(index)}>{str.delete}</GhostButton>
              </div>
              {(() => {
                const optionsObj = safeJsonParse(item.optionsText, {});
                const choicesLocalized = (optionsObj.choicesLocalized && typeof optionsObj.choicesLocalized === "object")
                  ? optionsObj.choicesLocalized
                  : {};
                const choices =
                  Array.isArray(choicesLocalized[editingLang]) ? choicesLocalized[editingLang] :
                    (Array.isArray(optionsObj.choices) ? optionsObj.choices : []);
                const questionLocalizedText = safeJsonParse(
                  item.questionLocalizedText || "{\"en\":\"\",\"am\":\"\"}",
                  { en: item.question ?? "", am: "" }
                );
                const itemType = (item.type ?? "").toString().trim();
                const validationObj = safeJsonParse(item.validationText, {});
                const required = validationObj.required === true;
                const choiceTypes = ["SINGLE_CHOICE", "MULTIPLE_CHOICE", "YES_NO"];
                const isChoiceType = choiceTypes.includes(itemType);
                const hasChoices = Array.isArray(choices) && choices.length > 0;

                const minChoices = itemType === "YES_NO" ? 2 : 1;
                const templateChoices = Array.isArray(typeDefaults?.[itemType]?.options?.choices)
                  ? typeDefaults[itemType].options.choices
                  : null;
                const effectiveChoices = hasChoices
                  ? choices
                  : templateChoices && templateChoices.length > 0
                    ? templateChoices
                    : itemType === "YES_NO"
                      ? ["YES", "NO"]
                      : [""];

                const normalizedEffectiveChoices =
                  effectiveChoices.length >= minChoices
                    ? effectiveChoices
                    : itemType === "YES_NO"
                      ? ["YES", "NO"]
                      : Array.from({ length: minChoices }, () => "");

                const isRating = itemType === "RATING";
                const ratingMin = typeof validationObj.min === "number" ? validationObj.min : 1;
                const ratingMax = typeof validationObj.max === "number" ? validationObj.max : 5;
                return (
                  <>
                    <div style={{ marginTop: 8 }}>
                      <Label>Question ({LANG_LABEL[editingLang] ?? editingLang})</Label>
                      <Input
                        value={questionLocalizedText[editingLang] ?? ""}
                        onChange={(e) => {
                          const nextVal = e.target.value;
                          setDraftItems((p) => p.map((x, i) => {
                            if (i !== index) return x;
                            const currentQ = safeJsonParse(x.questionLocalizedText || "{\"en\":\"\",\"am\":\"\"}", { en: x.question ?? "", am: "" });
                            const updatedQ = { ...currentQ, [editingLang]: nextVal };
                            return {
                              ...x,
                              questionLocalizedText: JSON.stringify(updatedQ),
                              // Keep `question` as English fallback for backend-required field.
                              question: (typeof updatedQ.en === "string" && updatedQ.en.trim() !== "") ? updatedQ.en : nextVal
                            };
                          }));
                        }}
                        placeholder="Question"
                        required
                      />
                    </div>

                    <div style={{ marginTop: 8 }}>
                        <Label>Type</Label>
                        <select
                          value={item.type}
                          onChange={(e) => {
                            const nextType = e.target.value;
                            setDraftItems((p) =>
                              p.map((x, i) => {
                                if (i !== index) return x;
                                const prevValidation = safeJsonParse(x.validationText, {});
                                const prevRequired = prevValidation.required === true;
                                if (choiceTypes.includes(nextType)) {
                                  const prevOptionsObj = safeJsonParse(x.optionsText, {});
                                  const prevChoicesLocalized = (prevOptionsObj.choicesLocalized && typeof prevOptionsObj.choicesLocalized === "object")
                                    ? prevOptionsObj.choicesLocalized
                                    : {};
                                  const prevChoices =
                                    Array.isArray(prevChoicesLocalized[editingLang]) ? prevChoicesLocalized[editingLang] :
                                      (Array.isArray(prevOptionsObj.choices) ? prevOptionsObj.choices : []);
                                  const templateChoices = Array.isArray(typeDefaults?.[nextType]?.options?.choices)
                                    ? typeDefaults[nextType].options.choices
                                    : null;
                                  const c =
                                    Array.isArray(prevChoices) && prevChoices.length > 0
                                      ? prevChoices
                                      : (Array.isArray(templateChoices) && templateChoices.length > 0
                                        ? templateChoices
                                        : (nextType === "YES_NO" ? ["YES", "NO"] : ["", ""]));
                                  // YES_NO historically defaults to YES/NO. If user adds more, keep them.
                                  const normalizedChoices = nextType === "YES_NO"
                                    ? (c.length >= 2 ? c : ["YES", "NO"])
                                    : c;
                                  return {
                                    ...x,
                                    type: nextType,
                                    optionsText: JSON.stringify({
                                      choices: normalizedChoices,
                                      choicesLocalized: { en: normalizedChoices, am: normalizedChoices }
                                    }),
                                    validationText: JSON.stringify({ ...prevValidation, required: prevRequired })
                                  };
                                }
                                if (nextType === "TEXT" || nextType === "PHOTO") {
                                  return {
                                    ...x,
                                    type: nextType,
                                    optionsText: JSON.stringify({}),
                                    validationText: JSON.stringify({ ...prevValidation, required: prevRequired })
                                  };
                                }
                                if (nextType === "RATING") {
                                  const templateRating = typeDefaults?.RATING?.validation ?? {};
                                  const templateMin = typeof templateRating.min === "number" ? templateRating.min : 1;
                                  const templateMax = typeof templateRating.max === "number" ? templateRating.max : 5;
                                  const prevMin = typeof prevValidation.min === "number" ? prevValidation.min : templateMin;
                                  const prevMax = typeof prevValidation.max === "number" ? prevValidation.max : templateMax;
                                  return {
                                    ...x,
                                    type: nextType,
                                    optionsText: JSON.stringify({}),
                                    validationText: JSON.stringify({ ...prevValidation, required: prevRequired, min: prevMin, max: prevMax })
                                  };
                                }
                                return { ...x, type: nextType };
                              })
                            );
                          }}
                          style={{ padding: 10, fontFamily: t.font, borderRadius: t.radius, border: `1px solid ${t.line}`, width: "100%", marginTop: 6 }}
                        >
                          {["TEXT", "SINGLE_CHOICE", "MULTIPLE_CHOICE", "YES_NO", "RATING", "PHOTO"].map((x) => (
                            <option key={x} value={x}>{x}</option>
                          ))}
                        </select>
                    </div>

                    {isChoiceType ? (
                      <div style={{ marginTop: 8 }}>
                        <Label>Answers</Label>
                        <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
                          {normalizedEffectiveChoices.map((c, choiceIndex) => (
                            <div
                              key={choiceIndex}
                              style={{ display: "grid", gridTemplateColumns: "1fr auto", gap: 8, alignItems: "end" }}
                            >
                              <div>
                                <Label style={{ marginBottom: 6 }}>Answer {choiceIndex + 1}</Label>
                                <Input
                                  value={c}
                                  onChange={(e) => {
                                    const next = [...normalizedEffectiveChoices];
                                    next[choiceIndex] = e.target.value;
                                    setDraftItems((p) =>
                                      p.map((x, i) =>
                                        i === index
                                          ? (() => {
                                              const prevOptionsObj = safeJsonParse(x.optionsText, {});
                                              const prevChoicesLocalized = (prevOptionsObj.choicesLocalized && typeof prevOptionsObj.choicesLocalized === "object")
                                                ? prevOptionsObj.choicesLocalized
                                                : {};
                                              const nextChoicesLocalized = { ...prevChoicesLocalized, [editingLang]: next };
                                              // Keep the other languages array length in sync.
                                              SUPPORTED_LANGS.forEach((lang) => {
                                                if (!Array.isArray(nextChoicesLocalized[lang])) nextChoicesLocalized[lang] = [];
                                                if (nextChoicesLocalized[lang].length < next.length) {
                                                  nextChoicesLocalized[lang] = [
                                                    ...nextChoicesLocalized[lang],
                                                    ...Array.from({ length: next.length - nextChoicesLocalized[lang].length }, () => "")
                                                  ];
                                                }
                                              });
                                              const enChoices = Array.isArray(nextChoicesLocalized.en) ? nextChoicesLocalized.en : (editingLang === "en" ? next : []);
                                              return {
                                                ...x,
                                                optionsText: JSON.stringify({
                                                  choices: enChoices,
                                                  choicesLocalized: nextChoicesLocalized
                                                })
                                              };
                                            })()
                                          : x
                                      )
                                    );
                                  }}
                                  placeholder={itemType === "YES_NO" ? (choiceIndex === 0 ? "e.g. YES" : "e.g. NO") : "e.g. Option"}
                                />
                              </div>
                              <div style={{ paddingBottom: 6 }}>
                                <GhostButton
                                  type="button"
                                  disabled={normalizedEffectiveChoices.length <= minChoices}
                                  onClick={() => {
                                    const next = normalizedEffectiveChoices.filter((_, i) => i !== choiceIndex);
                                    setDraftItems((p) =>
                                      p.map((x, i) =>
                                        i === index
                                          ? (() => {
                                              const prevOptionsObj = safeJsonParse(x.optionsText, {});
                                              const prevChoicesLocalized = (prevOptionsObj.choicesLocalized && typeof prevOptionsObj.choicesLocalized === "object")
                                                ? prevOptionsObj.choicesLocalized
                                                : {};
                                              const nextChoicesLocalized = { ...prevChoicesLocalized, [editingLang]: next };
                                              SUPPORTED_LANGS.forEach((lang) => {
                                                if (!Array.isArray(nextChoicesLocalized[lang])) nextChoicesLocalized[lang] = [];
                                                if (nextChoicesLocalized[lang].length > next.length) {
                                                  nextChoicesLocalized[lang] = nextChoicesLocalized[lang].slice(0, next.length);
                                                }
                                              });
                                              const enChoices = Array.isArray(nextChoicesLocalized.en) ? nextChoicesLocalized.en : (editingLang === "en" ? next : []);
                                              return {
                                                ...x,
                                                optionsText: JSON.stringify({
                                                  choices: enChoices,
                                                  choicesLocalized: nextChoicesLocalized
                                                })
                                              };
                                            })()
                                          : x
                                      )
                                    );
                                  }}
                                >
                                  Remove
                                </GhostButton>
                              </div>
                            </div>
                          ))}
                        </div>
                        <div style={{ marginTop: 10, display: "flex", gap: 8, flexWrap: "wrap" }}>
                          <GhostButton
                            type="button"
                            onClick={() => {
                              const next = [...normalizedEffectiveChoices, ""];
                              setDraftItems((p) =>
                                p.map((x, i) =>
                                  i === index
                                    ? (() => {
                                        const prevOptionsObj = safeJsonParse(x.optionsText, {});
                                        const prevChoicesLocalized = (prevOptionsObj.choicesLocalized && typeof prevOptionsObj.choicesLocalized === "object")
                                          ? prevOptionsObj.choicesLocalized
                                          : {};
                                        const nextChoicesLocalized = { ...prevChoicesLocalized, [editingLang]: next };
                                        SUPPORTED_LANGS.forEach((lang) => {
                                          if (!Array.isArray(nextChoicesLocalized[lang])) nextChoicesLocalized[lang] = [];
                                          if (nextChoicesLocalized[lang].length < next.length) {
                                            nextChoicesLocalized[lang] = [
                                              ...nextChoicesLocalized[lang],
                                              ...Array.from({ length: next.length - nextChoicesLocalized[lang].length }, () => "")
                                            ];
                                          }
                                        });
                                        const enChoices = Array.isArray(nextChoicesLocalized.en) ? nextChoicesLocalized.en : (editingLang === "en" ? next : []);
                                        return {
                                          ...x,
                                          optionsText: JSON.stringify({
                                            choices: enChoices,
                                            choicesLocalized: nextChoicesLocalized
                                          })
                                        };
                                      })()
                                    : x
                                )
                              );
                            }}
                          >
                            Add answer
                          </GhostButton>
                        </div>
                      </div>
                    ) : isRating ? (
                      <div style={{ marginTop: 8 }}>
                        <Label>Rating range</Label>
                        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginTop: 8 }}>
                          <div>
                            <Label style={{ marginBottom: 6 }}>Min</Label>
                            <Input
                              type="number"
                              value={ratingMin}
                              onChange={(e) => {
                                const nextMinRaw = Number(e.target.value);
                                if (!Number.isFinite(nextMinRaw)) return;
                                const nextMin = nextMinRaw;
                                const currentMax = typeof validationObj.max === "number" ? validationObj.max : 5;
                                const nextMax = nextMin > currentMax ? nextMin : currentMax;
                                const nextVal = JSON.stringify({ ...validationObj, min: nextMin, max: nextMax, required });
                                setDraftItems((p) => p.map((x, i) => (i === index ? { ...x, validationText: nextVal } : x)));
                              }}
                            />
                          </div>
                          <div>
                            <Label style={{ marginBottom: 6 }}>Max</Label>
                            <Input
                              type="number"
                              value={ratingMax}
                              onChange={(e) => {
                                const nextMaxRaw = Number(e.target.value);
                                if (!Number.isFinite(nextMaxRaw)) return;
                                const nextMax = nextMaxRaw;
                                const currentMin = typeof validationObj.min === "number" ? validationObj.min : 1;
                                const nextMin = nextMax < currentMin ? nextMax : currentMin;
                                const nextVal = JSON.stringify({ ...validationObj, min: nextMin, max: nextMax, required });
                                setDraftItems((p) => p.map((x, i) => (i === index ? { ...x, validationText: nextVal } : x)));
                              }}
                            />
                          </div>
                        </div>
                      </div>
                    ) : null}

                    <div style={{ marginTop: 8 }}>
                      <Label>Required</Label>
                      <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                        <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 14, color: t.text }}>
                          <input
                            type="radio"
                            name={`required-${index}`}
                            checked={required}
                            onChange={() => {
                              const nextVal = JSON.stringify({ ...validationObj, required: true });
                              setDraftItems((p) => p.map((x, i) => (i === index ? { ...x, validationText: nextVal } : x)));
                            }}
                          />
                          True
                        </label>
                        <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 14, color: t.text }}>
                          <input
                            type="radio"
                            name={`required-${index}`}
                            checked={!required}
                            onChange={() => {
                              const nextVal = JSON.stringify({ ...validationObj, required: false });
                              setDraftItems((p) => p.map((x, i) => (i === index ? { ...x, validationText: nextVal } : x)));
                            }}
                          />
                          False
                        </label>
                      </div>
                    </div>
                  </>
                );
              })()}
            </Card>
            );
          })}
            </div>
          ))}
          <GhostButton type="button" onClick={addSection} style={{ justifySelf: "start" }}>
            {str.addSection}
          </GhostButton>
          <label style={{ display: "flex", alignItems: "center", gap: 10, fontSize: 14, color: t.text, marginTop: 4 }}>
            <input type="checkbox" checked={skipAutoOnPublish} onChange={(e) => setSkipAutoOnPublish(e.target.checked)} />
            Skip automatic supervisor assignment for this publish (manual assignment still available)
          </label>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <PrimaryButton type="submit">Publish version</PrimaryButton>
          </div>
            </form>
          </Card>

          <h2 style={{ fontSize: 14, fontWeight: 600, margin: "0 0 12px", color: t.muted }}>All checklists</h2>
          <DataTable
        columns={[
          { key: "title", label: "Title" },
          { key: "target", label: "Target" },
          { key: "purpose", label: "Purpose" },
          { key: "grades", label: "Grades" },
          { key: "auto", label: "Auto-assign" },
          { key: "ver", label: "Status" },
          { key: "actions", label: "Actions" }
        ]}
        rows={items}
        empty="No checklists."
        renderCell={(key, row) => {
          if (key === "target") return row.targetName ?? row.targetType ?? "—";
          if (key === "grades") {
            if (Array.isArray(row.gradeGroupGradeCodes) && row.gradeGroupGradeCodes.length) {
              const label = row.gradeGroupDisplayName ? `${row.gradeGroupDisplayName}: ` : "";
              return `${label}${row.gradeGroupGradeCodes.join(", ")}`;
            }
            return row.gradeGroupDisplayName || row.gradesDescription || "—";
          }
          if (key === "auto") {
            if (!CHECKLIST_AUTO_ASSIGN_TARGETS.has(row.targetType)) return "—";
            return row.autoAssignOnPublish !== false ? "On" : "Off";
          }
          if (key === "ver") return row.activeVersion == null ? "Disabled" : `v${row.activeVersion}`;
          if (key === "actions") {
            const isDisabled = row.activeVersion == null;
            return (
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <GhostButton
                  onClick={() => openEditChecklistItems(row.id)}
                >
                  Edit items
                </GhostButton>
                <GhostButton
                  onClick={() => openEditChecklist(row)}
                  disabled={editBusy}
                >
                  Edit
                </GhostButton>
                <GhostButton
                  onClick={() => toggleChecklist(row.id, isDisabled)}
                  disabled={toggleBusyId === row.id}
                  ariaLabel="Toggle enable/disable"
                >
                  {toggleBusyId === row.id ? "Please wait..." : isDisabled ? "Enable" : "Disable"}
                </GhostButton>
                <GhostButton
                  onClick={() => openDeleteChecklist(row.id)}
                  disabled={deleteBusy}
                >
                  Delete
                </GhostButton>
              </div>
            );
          }
          return row[key] ?? "—";
          }}
          />
        </>
      )}

      {checklistSubTab === "options" && (
        <div style={{ display: "grid", gap: 20 }}>
          <Card style={{ padding: 20 }}>
            <h2 style={{ margin: "0 0 12px", fontSize: 15, fontWeight: 600 }}>Targets</h2>
            <p style={{ margin: "0 0 12px", color: t.muted, fontSize: 13 }}>
              Each target has a display name and a routing kind (who the checklist applies to for assignments).
            </p>
            <div style={{ display: "flex", gap: 8, marginBottom: 12 }}>
              <PrimaryButton
                type="button"
                onClick={() => {
                  setTargetOptionForm({ id: null, name: "", routingKind: "SCHOOL" });
                  setTargetOptionFormOpen(true);
                }}
              >
                Add target
              </PrimaryButton>
            </div>
            <DataTable
              columns={[
                { key: "name", label: "Name" },
                { key: "routingKind", label: "Routing" },
                { key: "actions", label: "" }
              ]}
              rows={targetOptions}
              empty="No targets."
              renderCell={(key, row) => {
                if (key === "actions") {
                  return (
                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                      <GhostButton
                        type="button"
                        onClick={() => {
                          setTargetOptionForm({ id: row.id, name: row.name, routingKind: row.routingKind });
                          setTargetOptionFormOpen(true);
                        }}
                      >
                        Edit
                      </GhostButton>
                      <GhostButton type="button" onClick={() => deleteTargetOption(row.id)}>
                        Delete
                      </GhostButton>
                    </div>
                  );
                }
                return row[key] ?? "—";
              }}
            />
          </Card>
          <Card style={{ padding: 20 }}>
            <h2 style={{ margin: "0 0 12px", fontSize: 15, fontWeight: 600 }}>Purposes</h2>
            <p style={{ margin: "0 0 12px", color: t.muted, fontSize: 13 }}>Labels describing why the checklist exists (e.g. clinical vs administrative).</p>
            <div style={{ display: "flex", gap: 8, marginBottom: 12 }}>
              <PrimaryButton
                type="button"
                onClick={() => {
                  setPurposeOptionForm({ id: null, name: "" });
                  setPurposeOptionFormOpen(true);
                }}
              >
                Add purpose
              </PrimaryButton>
            </div>
            <DataTable
              columns={[
                { key: "name", label: "Name" },
                { key: "actions", label: "" }
              ]}
              rows={purposeOptions}
              empty="No purposes."
              renderCell={(key, row) => {
                if (key === "actions") {
                  return (
                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                      <GhostButton
                        type="button"
                        onClick={() => {
                          setPurposeOptionForm({ id: row.id, name: row.name });
                          setPurposeOptionFormOpen(true);
                        }}
                      >
                        Edit
                      </GhostButton>
                      <GhostButton type="button" onClick={() => deletePurposeOption(row.id)}>
                        Delete
                      </GhostButton>
                    </div>
                  );
                }
                return row[key] ?? "—";
              }}
            />
          </Card>
        </div>
      )}

      <Modal
        open={targetOptionFormOpen}
        onClose={() => (!optionsSaving ? setTargetOptionFormOpen(false) : null)}
        title={targetOptionForm.id ? "Edit target" : "New target"}
      >
        <form onSubmit={saveTargetOptionForm} style={{ display: "grid", gap: 12 }}>
          <div>
            <Label>Name</Label>
            <Input
              value={targetOptionForm.name}
              onChange={(e) => setTargetOptionForm((p) => ({ ...p, name: e.target.value }))}
              required
            />
          </div>
          <div>
            <Label>Routing kind</Label>
            <select
              value={targetOptionForm.routingKind}
              onChange={(e) => setTargetOptionForm((p) => ({ ...p, routingKind: e.target.value }))}
              style={{ padding: 10, borderRadius: t.radius, border: `1px solid ${t.line}`, fontFamily: t.font, width: "100%" }}
            >
              {["SCHOOL", "TEACHER", "DIRECTOR", "SCHOOL_STAFF"].map((k) => (
                <option key={k} value={k}>
                  {k}
                </option>
              ))}
            </select>
          </div>
          <PrimaryButton type="submit" disabled={optionsSaving}>
            {optionsSaving ? "Saving…" : "Save"}
          </PrimaryButton>
        </form>
      </Modal>

      <Modal
        open={purposeOptionFormOpen}
        onClose={() => (!optionsSaving ? setPurposeOptionFormOpen(false) : null)}
        title={purposeOptionForm.id ? "Edit purpose" : "New purpose"}
      >
        <form onSubmit={savePurposeOptionForm} style={{ display: "grid", gap: 12 }}>
          <div>
            <Label>Name</Label>
            <Input
              value={purposeOptionForm.name}
              onChange={(e) => setPurposeOptionForm((p) => ({ ...p, name: e.target.value }))}
              required
            />
          </div>
          <PrimaryButton type="submit" disabled={optionsSaving}>
            {optionsSaving ? "Saving…" : "Save"}
          </PrimaryButton>
        </form>
      </Modal>

      <Modal
        open={typeDefaultsModalOpen}
        onClose={() => (!typeDefaultsSaving ? setTypeDefaultsModalOpen(false) : null)}
        title="Default item types"
        wide
      >
        <form onSubmit={saveTypeDefaults} style={{ display: "grid", gap: 12 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div>
              <Label>Question type</Label>
              <select
                value={typeDefaultsEditingType}
                onChange={(e) => setTypeDefaultsEditingType(e.target.value)}
                style={{ padding: 10, borderRadius: t.radius, border: `1px solid ${t.line}`, fontFamily: t.font, width: "100%" }}
              >
                {["TEXT", "SINGLE_CHOICE", "MULTIPLE_CHOICE", "YES_NO", "RATING", "PHOTO"].map((x) => (
                  <option key={x} value={x}>{x}</option>
                ))}
              </select>
            </div>
            <div>
              <Label>What is editable</Label>
              <p style={{ margin: 0, color: t.muted, fontSize: 13 }}>
                Choice types: default answer choices. <br />
                Rating: default min/max.
              </p>
            </div>
          </div>

          {choiceDefaultTypes.includes(typeDefaultsEditingType) && (
            <div style={{ marginTop: 4 }}>
              <Label>Default answer choices</Label>
              <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
                {typeDefaultsDraftChoices.map((c, idx) => (
                  <div key={idx} style={{ display: "grid", gridTemplateColumns: "1fr auto", gap: 8, alignItems: "end" }}>
                    <div>
                      <Label style={{ marginBottom: 6 }}>Answer {idx + 1}</Label>
                      <Input
                        value={c}
                        onChange={(e) => {
                          const next = [...typeDefaultsDraftChoices];
                          next[idx] = e.target.value;
                          setTypeDefaultsDraftChoices(next);
                        }}
                        placeholder="e.g. YES"
                        required
                      />
                    </div>
                    <div style={{ paddingBottom: 6 }}>
                      <GhostButton
                        type="button"
                        disabled={typeDefaultsDraftChoices.length <= 2 && typeDefaultsEditingType === "YES_NO"}
                        onClick={() => {
                          const next = typeDefaultsDraftChoices.filter((_, i) => i !== idx);
                          setTypeDefaultsDraftChoices(next);
                        }}
                      >
                        Remove
                      </GhostButton>
                    </div>
                  </div>
                ))}
              </div>

              <div style={{ marginTop: 10, display: "flex", gap: 8, flexWrap: "wrap" }}>
                <GhostButton
                  type="button"
                  onClick={() => setTypeDefaultsDraftChoices((p) => [...p, ""])}
                >
                  Add answer choice
                </GhostButton>

                <GhostButton
                  type="button"
                  onClick={() => {
                    // Reload templates for the selected type.
                    const tpl = typeDefaults?.[typeDefaultsEditingType];
                    const resetChoices = Array.isArray(tpl?.options?.choices) ? tpl.options.choices : typeDefaultsEditingType === "YES_NO" ? ["YES", "NO"] : ["Option 1", "Option 2"];
                    setTypeDefaultsDraftChoices(resetChoices.map((x) => x?.toString?.() ?? ""));
                  }}
                >
                  Reset to stored
                </GhostButton>
              </div>
            </div>
          )}

          {typeDefaultsEditingType === "RATING" && (
            <div style={{ marginTop: 4 }}>
              <Label>Default rating range</Label>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginTop: 8 }}>
                <div>
                  <Label style={{ marginBottom: 6 }}>Min</Label>
                  <Input
                    type="number"
                    value={typeDefaultsDraftRating.min}
                    onChange={(e) => setTypeDefaultsDraftRating((p) => ({ ...p, min: Number(e.target.value) }))}
                    required
                  />
                </div>
                <div>
                  <Label style={{ marginBottom: 6 }}>Max</Label>
                  <Input
                    type="number"
                    value={typeDefaultsDraftRating.max}
                    onChange={(e) => setTypeDefaultsDraftRating((p) => ({ ...p, max: Number(e.target.value) }))}
                    required
                  />
                </div>
              </div>
            </div>
          )}

          {(typeDefaultsEditingType === "TEXT" || typeDefaultsEditingType === "PHOTO") && (
            <div>
              <p style={{ margin: 0, color: t.muted, fontSize: 13 }}>
                No answer-choice defaults for this type.
              </p>
            </div>
          )}

          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 8 }}>
            <GhostButton
              type="button"
              onClick={async () => {
                try {
                  const url = `${API_BASE}/checklists/type-defaults/${typeDefaultsEditingType}`;
                  await fetch(url, { method: "PATCH", headers: jsonHeaders(headers), body: JSON.stringify({}) });
                  await loadTypeDefaults();
                } catch (e) {
                  setMessage({ type: "error", text: e.message || "Reset failed" });
                }
              }}
              disabled={typeDefaultsSaving}
            >
              Restore built-in
            </GhostButton>
            <PrimaryButton type="submit" disabled={typeDefaultsSaving}>
              {typeDefaultsSaving ? "Saving…" : "Save defaults"}
            </PrimaryButton>
          </div>
        </form>
      </Modal>

      <Modal
        open={editModalOpen}
        onClose={() => (!editBusy ? setEditModalOpen(false) : null)}
        title="Edit checklist"
        wide
      >
        <form onSubmit={saveEditChecklist} style={{ display: "grid", gap: 12 }}>
          <div><Label>Title</Label><Input value={editForm.title} onChange={(e) => setEditForm((p) => ({ ...p, title: e.target.value }))} required /></div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div>
              <Label>Target</Label>
              <select
                value={editForm.targetOptionId}
                onChange={(e) => {
                  const nextId = e.target.value;
                  const rk = targetOptions.find((o) => o.id === nextId)?.routingKind ?? "SCHOOL";
                  setEditForm((p) => ({ ...p, targetOptionId: nextId, targetType: rk }));
                }}
                style={{ padding: 10, borderRadius: t.radius, border: `1px solid ${t.line}`, fontFamily: t.font, width: "100%" }}
              >
                <option value="">Select…</option>
                {targetOptions.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.name} ({o.routingKind})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <Label>Purpose</Label>
              <select
                value={editForm.purposeOptionId}
                onChange={(e) => setEditForm((p) => ({ ...p, purposeOptionId: e.target.value }))}
                style={{ padding: 10, borderRadius: t.radius, border: `1px solid ${t.line}`, fontFamily: t.font, width: "100%" }}
              >
                <option value="">Select…</option>
                {purposeOptions.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.name}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div>
            <Label>Grade group</Label>
            <select
              value={editForm.gradeGroupId}
              onChange={(e) => setEditForm((p) => ({ ...p, gradeGroupId: e.target.value }))}
              style={{ padding: 10, borderRadius: t.radius, border: `1px solid ${t.line}`, fontFamily: t.font, width: "100%" }}
              required
            >
              <option value="">Select…</option>
              {gradeGroups.map((g) => (
                <option key={g.id} value={g.id}>
                  {g.displayName} — {Array.isArray(g.gradeCodes) && g.gradeCodes.length ? g.gradeCodes.join(", ") : g.gradesDescription}
                </option>
              ))}
            </select>
          </div>
          <label style={{ display: "flex", alignItems: "center", gap: 10, fontSize: 14, color: t.text }}>
            <input
              type="checkbox"
              checked={Boolean(editForm.autoAssignOnPublish)}
              onChange={(e) => setEditForm((p) => ({ ...p, autoAssignOnPublish: e.target.checked }))}
              disabled={!CHECKLIST_AUTO_ASSIGN_TARGETS.has(editForm.targetType)}
            />
            Auto-assign on publish (school or director routing)
          </label>
          {CHECKLIST_AUTO_ASSIGN_TARGETS.has(editForm.targetType) && Boolean(editForm.autoAssignOnPublish) && (
            <div>
              <Label>Auto-assignment due date</Label>
              <Input
                type="datetime-local"
                value={editForm.autoAssignDueAt}
                onChange={(e) => setEditForm((p) => ({ ...p, autoAssignDueAt: e.target.value }))}
                required
              />
            </div>
          )}
          <PrimaryButton type="submit" disabled={editBusy}>
            {editBusy ? "Saving…" : "Save changes"}
          </PrimaryButton>
        </form>
      </Modal>

      <Modal
        open={deleteModalOpen}
        onClose={() => (!deleteBusy ? setDeleteModalOpen(false) : null)}
        title="Delete checklist"
      >
        <form onSubmit={confirmDeleteChecklist} style={{ display: "grid", gap: 12 }}>
          <p style={{ margin: 0, color: t.muted, fontSize: 14 }}>
            This will permanently delete the checklist and all its versions.
          </p>
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
            <GhostButton type="button" onClick={() => setDeleteModalOpen(false)} disabled={deleteBusy}>
              Cancel
            </GhostButton>
            <PrimaryButton type="submit" disabled={deleteBusy}>
              {deleteBusy ? "Deleting…" : "Delete"}
            </PrimaryButton>
          </div>
        </form>
      </Modal>
      <Modal open={ggModal} onClose={() => setGgModal(false)} title="New grade group" wide>
        <form onSubmit={createGradeGroup} style={{ display: "grid", gap: 12 }}>
          <div><Label>Label</Label><Input value={ggForm.displayName} onChange={(e) => setGgForm((p) => ({ ...p, displayName: e.target.value }))} required placeholder="e.g. Upper primary" /></div>
          <GradeCodeCheckboxes
            label="Grades in this group"
            value={ggForm.gradeCodes}
            onChange={(codes) => setGgForm((p) => ({ ...p, gradeCodes: codes }))}
            disabled={false}
          />
          <PrimaryButton type="submit">Save</PrimaryButton>
        </form>
      </Modal>
    </>
  );
}

function ChecklistItemsPage({ headers, initialChecklistId }) {
  const { str } = useI18n();
  const SUPPORTED_LANGS = ["en", "am"];
  const LANG_LABEL = { en: str.english, am: str.amharic };
  const [items, setItems] = useState([]);
  const [publishingChecklistId, setPublishingChecklistId] = useState(initialChecklistId || "");
  const [draftSections, setDraftSections] = useState([]);
  const draftSectionsRef = useRef(draftSections);
  useEffect(() => {
    draftSectionsRef.current = draftSections;
  }, [draftSections]);
  const [draftItems, setDraftItems] = useState([]);
  const [typeDefaults, setTypeDefaults] = useState(null);
  const [editingLang, setEditingLang] = useState("en");
  const [message, setMessage] = useState(null);
  const [skipAutoOnPublish, setSkipAutoOnPublish] = useState(false);

  const loadChecklists = () => fetch(`${API_BASE}/checklists`, { headers }).then((r) => r.json()).then(setItems);
  const loadTypeDefaults = () =>
    fetch(`${API_BASE}/checklists/type-defaults`, { headers })
      .then((r) => r.json())
      .then((list) => {
        const map = {};
        (list || []).forEach((row) => {
          map[row.itemType] = { options: row.options || {}, validation: row.validation || {} };
        });
        setTypeDefaults(map);
      })
      .catch(() => setTypeDefaults(null));

  useEffect(() => {
    loadChecklists();
    loadTypeDefaults();
  }, [headers]);

  useEffect(() => {
    if (initialChecklistId) setPublishingChecklistId(initialChecklistId);
  }, [initialChecklistId]);

  const safeJsonParse = (txt, fallback) => {
    try {
      if (!txt) return fallback;
      return JSON.parse(txt);
    } catch (_) {
      return fallback;
    }
  };

  const addDraftItem = (sectionId) => {
    if (!sectionId) return;
    setDraftItems((prev) => {
      const sections = draftSectionsRef.current;
      const newRow = {
        _id: newChecklistItemRowId(),
        sectionId,
        question: "",
        questionLocalizedText: JSON.stringify({ en: "", am: "" }),
        type: "TEXT",
        order: prev.length + 1,
        optionsText: "{}",
        validationText: '{"required":false}'
      };
      const secOrder = sections.findIndex((s) => s._id === sectionId);
      if (secOrder < 0) {
        return [...prev, newRow];
      }
      let lastInSec = -1;
      for (let i = 0; i < prev.length; i++) {
        if (prev[i].sectionId === sectionId) lastInSec = i;
      }
      if (lastInSec >= 0) {
        const next = [...prev];
        next.splice(lastInSec + 1, 0, newRow);
        return next;
      }
      let lastBefore = -1;
      for (let i = 0; i < prev.length; i++) {
        const sid = prev[i].sectionId;
        const si = sections.findIndex((s) => s._id === sid);
        if (si >= 0 && si < secOrder) lastBefore = i;
      }
      const next = [...prev];
      next.splice(lastBefore + 1, 0, newRow);
      return next;
    });
  };

  const removeDraftItem = (index) => {
    setDraftItems((prev) => prev.filter((_, i) => i !== index));
  };

  const addSection = () => {
    const newSec = { _id: newChecklistItemRowId(), name: "New section" };
    setDraftSections((s) => [...s, newSec]);
  };

  const updateSectionName = (sectionId, name) => {
    setDraftSections((secs) => secs.map((s) => (s._id === sectionId ? { ...s, name } : s)));
  };

  const moveSection = (index, direction) => {
    setDraftSections((sections) => {
      const j = index + direction;
      if (j < 0 || j >= sections.length) return sections;
      const reordered = [...sections];
      [reordered[index], reordered[j]] = [reordered[j], reordered[index]];
      setDraftItems((items) => orderItemsToMatchSectionOrder(reordered, items));
      return reordered;
    });
  };

  const removeSection = (index) => {
    setDraftSections((secs) => {
      if (secs.length <= 1) return secs;
      const targetId = index > 0 ? secs[index - 1]._id : secs[1]._id;
      const removed = secs[index]._id;
      const nextSecs = secs.filter((_, i) => i !== index);
      setDraftItems((items) => {
        const reassign = items.map((it) => (it.sectionId === removed ? { ...it, sectionId: targetId } : it));
        return orderItemsToMatchSectionOrder(nextSecs, reassign);
      });
      return nextSecs;
    });
  };

  const loadDraftFromChecklist = async (checklistId) => {
    if (!checklistId) return;
    try {
      const res = await fetch(`${API_BASE}/checklists/${checklistId}/render`, { headers });
      if (!res.ok) throw new Error("Could not load checklist render.");
      const data = await res.json();
      const { sections, items: mapped } = buildEditorStateFromChecklistRender(data, typeDefaults);
      setDraftSections(sections);
      setDraftItems(mapped);
    } catch (err) {
      setDraftSections([]);
      setDraftItems([]);
      setMessage({ type: "error", text: err.message || "Load failed." });
    }
  };

  useEffect(() => {
    if (!publishingChecklistId) return;
    loadDraftFromChecklist(publishingChecklistId);
  }, [publishingChecklistId, headers, typeDefaults]);

  const publishVersion = async (e) => {
    e.preventDefault();
    if (!publishingChecklistId) return;
    try {
      const normName = (s) => (s?.name ?? "").trim().toLowerCase() || "general";
      const secNames = draftSections.map((s) => normName(s));
      if (new Set(secNames).size !== secNames.length) {
        setMessage({ type: "error", text: "Section names must be unique (after trimming)." });
        return;
      }
      const groupKeyFor = (item) => {
        const sec = draftSections.find((s) => s._id === item.sectionId);
        return (sec?.name ?? "").trim() || "General";
      };
      const payload = {
        items: draftItems.map((item, index) => ({
          question: item.question,
          questionLocalized: JSON.parse(item.questionLocalizedText || "{\"en\":\"\",\"am\":\"\"}"),
          type: item.type,
          options: JSON.parse(item.optionsText || "{}"),
          validation: JSON.parse(item.validationText || "{}"),
          groupKey: groupKeyFor(item) || null,
          order: index + 1
        })),
        skipAutoAssignment: Boolean(skipAutoOnPublish)
      };
      const res = await fetch(`${API_BASE}/checklists/${publishingChecklistId}/versions`, {
        method: "POST",
        headers: jsonHeaders(headers),
        body: JSON.stringify(payload)
      });
      if (!res.ok) {
        let details = null;
        try {
          details = await res.json();
        } catch (_) {}
        throw new Error(details?.message || "Publish failed.");
      }
      setSkipAutoOnPublish(false);
      setMessage({ type: "ok", text: "Checklist items updated and version published." });
      await loadChecklists();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    }
  };

  return (
    <>
      <PageHeader title={str.checklistItemsTitle} subtitle={str.checklistItemsSubtitle} />
      {message && <Alert type={message.type}>{message.text}</Alert>}

      <Card style={{ padding: 20, marginBottom: 20 }}>
        <div style={{ display: "grid", gap: 12 }}>
          <div>
            <Label>{str.checklistLabel}</Label>
            <select
              value={publishingChecklistId}
              onChange={(e) => setPublishingChecklistId(e.target.value)}
              style={{ padding: 10, borderRadius: t.radius, border: `1px solid ${t.line}`, fontFamily: t.font, width: "100%" }}
            >
              <option value="">{str.selectChecklist}</option>
              {items.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.title}{c.activeVersion == null ? str.draftOption : ` · v${c.activeVersion}`}
                </option>
              ))}
            </select>
          </div>
          <div>
            <Label>{str.editLanguage}</Label>
            <select
              value={editingLang}
              onChange={(e) => setEditingLang(e.target.value)}
              style={{ padding: 10, borderRadius: t.radius, border: `1px solid ${t.line}`, fontFamily: t.font, width: "100%" }}
            >
              {SUPPORTED_LANGS.map((l) => (
                <option key={l} value={l}>{LANG_LABEL[l] ?? l}</option>
              ))}
            </select>
          </div>
        </div>
      </Card>

      {publishingChecklistId && (
        <Card style={{ padding: 20, marginBottom: 20 }}>
          <form onSubmit={publishVersion} style={{ display: "grid", gap: 12 }}>
            {draftSections.map((section, secIndex) => (
              <div
                key={section._id}
                style={{ display: "grid", gap: 10, padding: 14, borderRadius: t.radius, border: `1px solid ${t.line}`, background: t.card }}
              >
                <div style={{ display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center" }}>
                  <Label style={{ margin: 0 }}>{str.sectionNameLabel}</Label>
                  <Input
                    value={section.name}
                    onChange={(e) => updateSectionName(section._id, e.target.value)}
                    placeholder="General"
                    style={{ minWidth: 180, maxWidth: 400, flex: 1 }}
                  />
                  <GhostButton type="button" disabled={secIndex <= 0} onClick={() => moveSection(secIndex, -1)} title={str.moveSectionUp}>
                    ↑
                  </GhostButton>
                  <GhostButton
                    type="button"
                    disabled={secIndex >= draftSections.length - 1}
                    onClick={() => moveSection(secIndex, 1)}
                    title={str.moveSectionDown}
                  >
                    ↓
                  </GhostButton>
                  <GhostButton type="button" disabled={draftSections.length <= 1} onClick={() => removeSection(secIndex)}>
                    {str.removeSection}
                  </GhostButton>
                  <GhostButton type="button" onClick={() => addDraftItem(section._id)}>
                    {str.addChecklistItem}
                  </GhostButton>
                </div>
                {draftItems.map((item, index) => {
                  if (item.sectionId !== section._id) return null;
                  const optionsObj = safeJsonParse(item.optionsText, {});
                  const choicesLocalized = (optionsObj.choicesLocalized && typeof optionsObj.choicesLocalized === "object")
                    ? optionsObj.choicesLocalized
                    : {};
                  const choices = Array.isArray(choicesLocalized[editingLang])
                    ? choicesLocalized[editingLang]
                    : (Array.isArray(optionsObj.choices) ? optionsObj.choices : []);
                  const questionLocalizedText = safeJsonParse(
                    item.questionLocalizedText || "{\"en\":\"\",\"am\":\"\"}",
                    { en: item.question ?? "", am: "" }
                  );
                  const choiceTypes = ["SINGLE_CHOICE", "MULTIPLE_CHOICE", "YES_NO"];
                  const isChoiceType = choiceTypes.includes(item.type);
                  const minChoices = item.type === "YES_NO" ? 2 : 1;
                  const normalizedChoices = choices.length >= minChoices ? choices : (item.type === "YES_NO" ? ["YES", "NO"] : [""]);
                  return (
                    <Card key={item._id ?? index} style={{ padding: 14, background: t.accentSoft }}>
                      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
                        <strong style={{ fontSize: 13 }}>
                          {str.itemLabel} {index + 1}
                        </strong>
                        <GhostButton type="button" onClick={() => removeDraftItem(index)}>
                          {str.delete}
                        </GhostButton>
                      </div>
                      <div style={{ marginTop: 8 }}>
                        <Label>
                          {str.questionWord} ({LANG_LABEL[editingLang] ?? editingLang})
                        </Label>
                        <Input
                          value={questionLocalizedText[editingLang] ?? ""}
                          onChange={(e) => {
                            const nextVal = e.target.value;
                            setDraftItems((p) => p.map((x, i) => {
                              if (i !== index) return x;
                              const currentQ = safeJsonParse(x.questionLocalizedText || "{\"en\":\"\",\"am\":\"\"}", { en: x.question ?? "", am: "" });
                              const updatedQ = { ...currentQ, [editingLang]: nextVal };
                              return {
                                ...x,
                                questionLocalizedText: JSON.stringify(updatedQ),
                                question: (typeof updatedQ.en === "string" && updatedQ.en.trim() !== "") ? updatedQ.en : nextVal
                              };
                            }));
                          }}
                          required
                        />
                      </div>
                      {isChoiceType && (
                        <div style={{ marginTop: 8 }}>
                          <Label>{str.answersOptions}</Label>
                          <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
                            {normalizedChoices.map((c, choiceIndex) => (
                              <Input
                                key={choiceIndex}
                                value={c}
                                onChange={(e) => {
                                  const next = [...normalizedChoices];
                                  next[choiceIndex] = e.target.value;
                                  setDraftItems((p) =>
                                    p.map((x, i) => {
                                      if (i !== index) return x;
                                      const prevOptionsObj = safeJsonParse(x.optionsText, {});
                                      const prevChoicesLocalized = (prevOptionsObj.choicesLocalized && typeof prevOptionsObj.choicesLocalized === "object")
                                        ? prevOptionsObj.choicesLocalized
                                        : {};
                                      const nextChoicesLocalized = { ...prevChoicesLocalized, [editingLang]: next };
                                      const enChoices = Array.isArray(nextChoicesLocalized.en) ? nextChoicesLocalized.en : (editingLang === "en" ? next : []);
                                      return {
                                        ...x,
                                        optionsText: JSON.stringify({ choices: enChoices, choicesLocalized: nextChoicesLocalized })
                                      };
                                    })
                                  );
                                }}
                              />
                            ))}
                          </div>
                        </div>
                      )}
                    </Card>
                  );
                })}
              </div>
            ))}
            <GhostButton type="button" onClick={addSection} style={{ justifySelf: "start" }}>
              {str.addSection}
            </GhostButton>
            <label style={{ display: "flex", alignItems: "center", gap: 10, fontSize: 14, color: t.text }}>
              <input type="checkbox" checked={skipAutoOnPublish} onChange={(e) => setSkipAutoOnPublish(e.target.checked)} />
              {str.skipAutoAssignPublish}
            </label>
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              <PrimaryButton type="submit">{str.saveAndPublish}</PrimaryButton>
            </div>
          </form>
        </Card>
      )}
    </>
  );
}

function supervisorGetPosition() {
  return new Promise((resolve, reject) => {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      reject(new Error("Geolocation unavailable"));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ latitude: pos.coords.latitude, longitude: pos.coords.longitude }),
      reject,
      { enableHighAccuracy: true, timeout: 26000, maximumAge: 0 }
    );
  });
}

function supervisorCanvasPngBase64(canvas) {
  const data = canvas.toDataURL("image/png");
  const idx = data.indexOf("base64,");
  return idx >= 0 ? data.slice(idx + 7) : data;
}

function attachSignatureDrawing(canvas) {
  if (!canvas) return () => {};
  const ctx = canvas.getContext("2d");
  ctx.lineWidth = 2;
  ctx.lineCap = "round";
  ctx.strokeStyle = "#18181b";
  ctx.fillStyle = "#f4f4f5";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  let drawing = false;
  let lx = null;
  let ly = null;
  const pos = (e) => {
    const r = canvas.getBoundingClientRect();
    const cx = e.clientX ?? e.touches?.[0]?.clientX;
    const cy = e.clientY ?? e.touches?.[0]?.clientY;
    return { x: cx - r.left, y: cy - r.top };
  };
  const down = (e) => {
    e.preventDefault();
    drawing = true;
    const p = pos(e);
    lx = p.x;
    ly = p.y;
  };
  const up = () => {
    drawing = false;
    lx = null;
    ly = null;
  };
  const move = (e) => {
    if (!drawing) return;
    e.preventDefault();
    const p = pos(e);
    if (lx != null && ly != null) {
      ctx.beginPath();
      ctx.moveTo(lx, ly);
      ctx.lineTo(p.x, p.y);
      ctx.stroke();
    }
    lx = p.x;
    ly = p.y;
  };
  canvas.addEventListener("mousedown", down);
  canvas.addEventListener("mousemove", move);
  canvas.addEventListener("mouseup", up);
  canvas.addEventListener("mouseleave", up);
  canvas.addEventListener("touchstart", down, { passive: false });
  canvas.addEventListener("touchmove", move, { passive: false });
  canvas.addEventListener("touchend", up);
  return () => {
    canvas.removeEventListener("mousedown", down);
    canvas.removeEventListener("mousemove", move);
    canvas.removeEventListener("mouseup", up);
    canvas.removeEventListener("mouseleave", up);
    canvas.removeEventListener("touchstart", down);
    canvas.removeEventListener("touchmove", move);
    canvas.removeEventListener("touchend", up);
  };
}

function SupervisorChecklistField({ item, value, onChange }) {
  const type = item.type || "TEXT";
  const options = item.options && typeof item.options === "object" ? item.options : {};
  const validation = item.validation && typeof item.validation === "object" ? item.validation : {};
  const choices = Array.isArray(options.choices) ? options.choices.map((c) => String(c)) : [];
  const required = validation.required === true;
  const q = item.question || "";

  if (type === "YES_NO") {
    const a = choices[0] ?? "YES";
    const b = choices[1] ?? "NO";
    return (
      <div style={{ marginBottom: 16 }}>
        <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 8 }}>
          {q}
          {required ? <span style={{ color: "#b91c1c", marginLeft: 6 }}>*</span> : null}
        </div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {[a, b].map((label) => (
            <GhostButton key={label} type="button" onClick={() => onChange(label)} aria-pressed={value === label}>
              {label}
            </GhostButton>
          ))}
        </div>
      </div>
    );
  }

  if (type === "SINGLE_CHOICE") {
    return (
      <div style={{ marginBottom: 16 }}>
        <Label>
          {q}
          {required ? " *" : ""}
        </Label>
        <select
          value={value ?? ""}
          onChange={(e) => onChange(e.target.value || null)}
          style={{ padding: 10, borderRadius: t.radius, border: `1px solid ${t.line}`, fontFamily: t.font, width: "100%" }}
        >
          <option value="">Select…</option>
          {choices.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
      </div>
    );
  }

  if (type === "MULTIPLE_CHOICE") {
    const set = new Set(Array.isArray(value) ? value.map(String) : []);
    return (
      <div style={{ marginBottom: 16 }}>
        <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 8 }}>
          {q}
          {required ? <span style={{ color: "#b91c1c", marginLeft: 6 }}>*</span> : null}
        </div>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
          {choices.map((c) => (
            <label key={c} style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 14 }}>
              <input
                type="checkbox"
                checked={set.has(c)}
                onChange={(e) => {
                  const next = new Set(set);
                  if (e.target.checked) next.add(c);
                  else next.delete(c);
                  onChange([...next]);
                }}
              />
              {c}
            </label>
          ))}
        </div>
      </div>
    );
  }

  if (type === "RATING") {
    const min = typeof validation.min === "number" ? validation.min : 1;
    const max = typeof validation.max === "number" ? validation.max : 5;
    const v = typeof value === "number" ? value : min;
    return (
      <div style={{ marginBottom: 16 }}>
        <Label>
          {q}
          {required ? " *" : ""}
        </Label>
        <input
          type="range"
          min={min}
          max={max}
          value={v}
          onChange={(e) => onChange(Number(e.target.value))}
          style={{ width: "100%" }}
        />
        <div style={{ fontSize: 13, color: t.muted }}>{v}</div>
      </div>
    );
  }

  if (type === "PHOTO") {
    return (
      <div style={{ marginBottom: 16 }}>
        <Label>
          {q}
          {required ? " *" : ""}
        </Label>
        <input
          type="file"
          accept="image/*"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (!file) {
              onChange(null);
              return;
            }
            const reader = new FileReader();
            reader.onload = () => {
              const s = String(reader.result || "");
              const i = s.indexOf("base64,");
              onChange(i >= 0 ? s.slice(i + 7) : s);
            };
            reader.readAsDataURL(file);
          }}
        />
      </div>
    );
  }

  return (
    <div style={{ marginBottom: 16 }}>
      <Label>
        {q}
        {required ? " *" : ""}
      </Label>
      <Input value={value ?? ""} onChange={(e) => onChange(e.target.value)} />
    </div>
  );
}

function SupervisorVisitPanel({ headers, assignmentId, onBack, onCompleted }) {
  const { str, locale } = useI18n();
  const [render, setRender] = useState(null);
  const [answers, setAnswers] = useState({});
  const [step, setStep] = useState("form");
  const [reviewId, setReviewId] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState(null);
  const [manualLat, setManualLat] = useState("9.03");
  const [manualLon, setManualLon] = useState("38.74");
  const [groupIdx, setGroupIdx] = useState(0);
  const [oneByOneIdx, setOneByOneIdx] = useState(0);
  const teacherCanvasRef = useRef(null);
  const directorCanvasRef = useRef(null);

  const displayMode = (render?.displayMode || "ALL_AT_ONCE").toString().toUpperCase();
  const groups = useMemo(() => groupChecklistItemsInApiOrder(render?.items || []), [render?.items]);
  const flatItems = render?.items || [];
  const showAllAtOnceSectionHeaders = groups.length > 1 || (groups.length === 1 && groups[0].key !== "General");

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const lang = locale === "am" ? "am" : "en";
        const res = await fetch(`${API_BASE}/assignments/${assignmentId}/render?lang=${encodeURIComponent(lang)}`, { headers });
        if (!res.ok) throw new Error(str.couldNotLoadChecklist);
        const data = await res.json();
        if (!cancelled) setRender(data);
      } catch (e) {
        if (!cancelled) setMsg({ type: "error", text: e.message || str.loadFailedShort });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [assignmentId, headers, locale]);

  useEffect(() => {
    setGroupIdx(0);
    setOneByOneIdx(0);
  }, [assignmentId, render]);

  useEffect(() => {
    if (step !== "sign") return undefined;
    const tEl = teacherCanvasRef.current;
    const dEl = directorCanvasRef.current;
    const c1 = attachSignatureDrawing(tEl);
    const c2 = attachSignatureDrawing(dEl);
    return () => {
      c1();
      c2();
    };
  }, [step]);

  const setAnswer = (itemId, v) => {
    setAnswers((prev) => ({ ...prev, [itemId]: v }));
  };

  const resolvePosition = async () => {
    try {
      return await supervisorGetPosition();
    } catch {
      const lat = Number(manualLat);
      const lon = Number(manualLon);
      if (!Number.isFinite(lat) || !Number.isFinite(lon)) throw new Error(str.enterValidLatLon);
      return { latitude: lat, longitude: lon };
    }
  };

  const submitVisit = async (e) => {
    e.preventDefault();
    if (!render?.items?.length) return;
    setBusy(true);
    setMsg(null);
    try {
      const pos = await resolvePosition();
      const startRes = await fetch(`${API_BASE}/assignments/${assignmentId}/start`, {
        method: "POST",
        headers: jsonHeaders(headers),
        body: JSON.stringify({ latitude: pos.latitude, longitude: pos.longitude })
      });
      if (!startRes.ok) {
        const j = await startRes.json().catch(() => ({}));
        throw new Error(j.message || str.couldNotStartVisit);
      }
      const payloadAnswers = [];
      for (const it of render.items) {
        const id = it.id;
        const raw = answers[id];
        if (raw === undefined || raw === null || raw === "") continue;
        if (Array.isArray(raw) && raw.length === 0) continue;
        payloadAnswers.push({ checklistItemId: id, answer: raw });
      }
      const subRes = await fetch(`${API_BASE}/assignments/${assignmentId}/submit`, {
        method: "POST",
        headers: jsonHeaders(headers),
        body: JSON.stringify({
          latitude: pos.latitude,
          longitude: pos.longitude,
          policy: "ALLOW_AND_FLAG_OUT_OF_RANGE",
          answers: payloadAnswers
        })
      });
      if (!subRes.ok) {
        const j = await subRes.json().catch(() => ({}));
        throw new Error(j.message || str.submitFailedShort);
      }
      const rawSubmit = await subRes.text();
      let idStr = rawSubmit.trim();
      try {
        idStr = JSON.parse(rawSubmit);
      } catch {
        idStr = idStr.replace(/^"|"$/g, "").trim();
      }
      if (!idStr) throw new Error(str.noReviewIdReturned);
      setReviewId(idStr);
      setStep("sign");
      setMsg({ type: "ok", text: str.reviewSavedSignatures });
    } catch (err) {
      setMsg({ type: "error", text: err.message || str.failedGeneric });
    } finally {
      setBusy(false);
    }
  };

  const submitSignatures = async (e) => {
    e.preventDefault();
    if (!reviewId) return;
    const t = teacherCanvasRef.current;
    const d = directorCanvasRef.current;
    if (!t || !d) return;
    setBusy(true);
    setMsg(null);
    try {
      const tB64 = supervisorCanvasPngBase64(t);
      const dB64 = supervisorCanvasPngBase64(d);
      if (tB64.length < 50 || dB64.length < 50) throw new Error(str.pleaseSignBoth);
      const r1 = await fetch(`${API_BASE}/reviews/${reviewId}/signatures`, {
        method: "POST",
        headers: jsonHeaders(headers),
        body: JSON.stringify({ signerRole: "TEACHER", imageBase64: tB64 })
      });
      if (!r1.ok) {
        const j = await r1.json().catch(() => ({}));
        throw new Error(j.message || str.teacherSignatureFailedShort);
      }
      const r2 = await fetch(`${API_BASE}/reviews/${reviewId}/signatures`, {
        method: "POST",
        headers: jsonHeaders(headers),
        body: JSON.stringify({ signerRole: "SCHOOL_DIRECTOR", imageBase64: dB64 })
      });
      if (!r2.ok) {
        const j = await r2.json().catch(() => ({}));
        throw new Error(j.message || str.directorSignatureFailedShort);
      }
      setMsg({ type: "ok", text: str.visitCompleteNotice });
      setStep("done");
      onCompleted?.();
    } catch (err) {
      setMsg({ type: "error", text: err.message || str.signaturesFailedShort });
    } finally {
      setBusy(false);
    }
  };

  if (!render) {
    return (
      <Card style={{ padding: 20 }}>
        <p style={{ margin: 0, color: t.muted }}>{str.loadingChecklist}</p>
        {msg?.type === "error" && <Alert type="error">{msg.text}</Alert>}
        <GhostButton onClick={onBack}>{str.back}</GhostButton>
      </Card>
    );
  }

  const title = `${str.checklistWord} (v${render.version ?? "?"})`;

  if (step === "sign") {
    return (
      <Card style={{ padding: 20 }}>
        <PageHeader
          title={str.signaturesTitle}
          subtitle={str.signaturesSubtitle}
          action={<GhostButton onClick={onBack}>{str.close}</GhostButton>}
        />
        {msg && <Alert type={msg.type}>{msg.text}</Alert>}
        <p style={{ color: t.muted, fontSize: 14 }}>{str.teacherSigner}</p>
        <canvas ref={teacherCanvasRef} width={340} height={150} style={{ border: `1px solid ${t.line}`, borderRadius: t.radius, touchAction: "none", maxWidth: "100%" }} />
        <p style={{ color: t.muted, fontSize: 14, marginTop: 16 }}>{str.directorSigner}</p>
        <canvas ref={directorCanvasRef} width={340} height={150} style={{ border: `1px solid ${t.line}`, borderRadius: t.radius, touchAction: "none", maxWidth: "100%" }} />
        <div style={{ marginTop: 16, display: "flex", gap: 8, flexWrap: "wrap" }}>
          <PrimaryButton type="button" disabled={busy} onClick={submitSignatures}>
            {busy ? str.savingEllipsis : str.saveSignatures}
          </PrimaryButton>
          <GhostButton type="button" onClick={onBack}>
            {str.backToList}
          </GhostButton>
        </div>
      </Card>
    );
  }

  if (step === "done") {
    return (
      <Card style={{ padding: 20 }}>
        <p style={{ margin: "0 0 12px", fontSize: 15 }}>{str.visitComplete}</p>
        <PrimaryButton type="button" onClick={onBack}>
          {str.backToAssignments}
        </PrimaryButton>
      </Card>
    );
  }

  return (
    <Card style={{ padding: 20 }}>
      <PageHeader
        title={title}
        subtitle={str.answerChecklistSubtitle}
        action={<GhostButton onClick={onBack}>{str.back}</GhostButton>}
      />
      {msg && <Alert type={msg.type}>{msg.text}</Alert>}
      <details style={{ marginBottom: 16, fontSize: 13, color: t.muted }}>
        <summary style={{ cursor: "pointer" }}>{str.locationGpsSummary}</summary>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginTop: 8 }}>
          <div>
            <Label>{str.latitudeLabel}</Label>
            <Input value={manualLat} onChange={(e) => setManualLat(e.target.value)} />
          </div>
          <div>
            <Label>{str.longitudeLabel}</Label>
            <Input value={manualLon} onChange={(e) => setManualLon(e.target.value)} />
          </div>
        </div>
      </details>
      <form onSubmit={submitVisit}>
        {displayMode === "GROUPED" && groups.length > 0 ? (() => {
          const g = groups[Math.min(groupIdx, groups.length - 1)];
          return (
            <>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8, marginBottom: 12, flexWrap: "wrap" }}>
                <h3 style={{ margin: 0, fontSize: 16, color: t.text }}>{g.key}</h3>
                <span style={{ color: t.muted, fontSize: 13 }}>
                  {str.sectionLabel} {Math.min(groupIdx, groups.length - 1) + 1} / {groups.length}
                </span>
              </div>
              {g.items.map((it) => (
                <SupervisorChecklistField key={it.id} item={it} value={answers[it.id]} onChange={(v) => setAnswer(it.id, v)} />
              ))}
              <div style={{ display: "flex", justifyContent: "space-between", marginTop: 16, marginBottom: 16, gap: 8, flexWrap: "wrap" }}>
                <GhostButton type="button" disabled={groupIdx <= 0} onClick={() => setGroupIdx((i) => Math.max(0, i - 1))}>
                  {str.checklistNavPrevious}
                </GhostButton>
                <GhostButton
                  type="button"
                  disabled={groupIdx >= groups.length - 1}
                  onClick={() => setGroupIdx((i) => Math.min(groups.length - 1, i + 1))}
                >
                  {str.checklistNavNext}
                </GhostButton>
              </div>
            </>
          );
        })() : displayMode === "ONE_BY_ONE" && flatItems.length > 0 ? (() => {
          const it = flatItems[Math.min(oneByOneIdx, flatItems.length - 1)];
          const showSec = showAllAtOnceSectionHeaders;
          return (
            <>
              {showSec ? (
                <p style={{ color: t.muted, fontSize: 13, margin: "0 0 12px" }}>
                  {str.sectionLabel}: {normalizeGroupKeyForDisplay(it)}
                </p>
              ) : null}
              <SupervisorChecklistField key={it.id} item={it} value={answers[it.id]} onChange={(v) => setAnswer(it.id, v)} />
              <div style={{ display: "flex", justifyContent: "space-between", marginTop: 16, marginBottom: 16, gap: 8, flexWrap: "wrap", alignItems: "center" }}>
                <GhostButton type="button" disabled={oneByOneIdx <= 0} onClick={() => setOneByOneIdx((i) => Math.max(0, i - 1))}>
                  {str.checklistNavPrevious}
                </GhostButton>
                <span style={{ color: t.muted, fontSize: 13, fontWeight: 600 }}>
                  {oneByOneIdx + 1} / {flatItems.length}
                </span>
                <GhostButton
                  type="button"
                  disabled={oneByOneIdx >= flatItems.length - 1}
                  onClick={() => setOneByOneIdx((i) => Math.min(flatItems.length - 1, i + 1))}
                >
                  {str.checklistNavNext}
                </GhostButton>
              </div>
            </>
          );
        })() : (
          groups.map((g) => (
            <div key={g.key} style={{ marginBottom: 4 }}>
              {showAllAtOnceSectionHeaders ? (
                <h3
                  style={{
                    fontSize: 15,
                    fontWeight: 600,
                    margin: "16px 0 10px",
                    color: t.text,
                    borderBottom: `1px solid ${t.line}`,
                    paddingBottom: 6
                }}
                >
                  {g.key}
                </h3>
              ) : null}
              {g.items.map((it) => (
                <SupervisorChecklistField key={it.id} item={it} value={answers[it.id]} onChange={(v) => setAnswer(it.id, v)} />
              ))}
            </div>
          ))
        )}
        <PrimaryButton type="submit" disabled={busy}>
          {busy ? str.submittingEllipsis : str.submitVisit}
        </PrimaryButton>
      </form>
    </Card>
  );
}

function SupervisorMyAssignmentsPage({ headers }) {
  const { str } = useI18n();
  const [items, setItems] = useState([]);
  const [workload, setWorkload] = useState(null);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState(null);
  const [visitId, setVisitId] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      const [aRes, wRes] = await Promise.all([
        fetch(`${API_BASE}/assignments/my`, { headers }),
        fetch(`${API_BASE}/supervision/my-workload`, { headers })
      ]);
      if (!aRes.ok) {
        const j = await aRes.json().catch(() => ({}));
        throw new Error(j.message || str.couldNotLoadAssignments);
      }
      const list = await aRes.json();
      setItems(Array.isArray(list) ? list : []);
      if (wRes.ok) setWorkload(await wRes.json());
      else setWorkload(null);
    } catch (e) {
      setErr(e.message || str.failedToLoadShort);
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [headers, str]);

  useEffect(() => {
    load();
  }, [load]);

  if (visitId) {
    return (
      <SupervisorVisitPanel
        headers={headers}
        assignmentId={visitId}
        onBack={() => setVisitId("")}
        onCompleted={load}
      />
    );
  }

  return (
    <>
      <PageHeader
        title={str.myAssignmentsTitle}
        subtitle={str.myAssignmentsSubtitle}
        action={<GhostButton onClick={load}>{str.refresh}</GhostButton>}
      />
      {err && <Alert type="error">{err}</Alert>}
      {workload && (
        <Card style={{ padding: 16, marginBottom: 16 }}>
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>{str.supervisorAtAGlance}</div>
          <p style={{ margin: 0, fontSize: 14, color: t.muted, lineHeight: 1.5 }}>
            {workload.completedAssignments ?? 0} {str.doneWord} · {workload.pendingAssignments ?? 0} {str.pendingWord} · {workload.inProgressAssignments ?? 0}{" "}
            {str.inProgressWord} · {workload.overdueAssignments ?? 0} {str.overdueWord} · {workload.visitsCompleted ?? 0} {str.visitsWord}
          </p>
        </Card>
      )}
      {loading ? (
        <p style={{ color: t.muted }}>{str.loadingEllipsis}</p>
      ) : items.length === 0 ? (
        <Card style={{ padding: 24 }}>
          <p style={{ margin: 0, color: t.muted }}>{str.noAssignmentsYet}</p>
        </Card>
      ) : (
        <div style={{ display: "grid", gap: 10 }}>
          {items.map((a) => {
            const id = a.id;
            const status = a.status || "UNKNOWN";
            const done = status === "COMPLETED";
            const short = id && String(id).length >= 8 ? `${String(id).slice(0, 8)}…` : str.assignmentFallback;
            return (
              <Card key={id} style={{ padding: 16 }}>
                <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", justifyContent: "space-between", gap: 12 }}>
                  <div>
                    <div style={{ fontWeight: 600 }}>{short}</div>
                    <div style={{ fontSize: 13, color: t.muted, marginTop: 4 }}>
                      {str.targetPrefix} {formatTargetWithGrade(a.targetType, a.targetGradeCode)} · {str.duePrefix} {a.dueDate || str.notSet}
                    </div>
                    <div style={{ fontSize: 12, color: t.muted, marginTop: 4 }}>
                      {str.statusPrefix} {String(status).replace(/_/g, " ")}
                    </div>
                  </div>
                  {!done && (
                    <PrimaryButton type="button" onClick={() => setVisitId(String(id))}>
                      {str.startVisit}
                    </PrimaryButton>
                  )}
                </div>
              </Card>
            );
          })}
        </div>
      )}
    </>
  );
}

function AssignmentsPage({ headers }) {
  const { str } = useI18n();
  const [items, setItems] = useState([]);
  const [checklists, setChecklists] = useState([]);
  const [versions, setVersions] = useState([]);
  const [supervisors, setSupervisors] = useState([]);
  const [schools, setSchools] = useState([]);
  const [teachers, setTeachers] = useState([]);
  const [schoolStuff, setSchoolStuff] = useState([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [editBusy, setEditBusy] = useState(false);
  const [editingAssignmentId, setEditingAssignmentId] = useState("");
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteAssignmentId, setDeleteAssignmentId] = useState("");
  const [bulkModalOpen, setBulkModalOpen] = useState(false);
  const [bulkBusy, setBulkBusy] = useState(false);
  const [message, setMessage] = useState(null);
  const [form, setForm] = useState({
    checklistId: "",
    checklistVersionId: "",
    supervisorId: "",
    targetType: "SCHOOL",
    schoolId: "",
    teacherId: "",
    targetGradeCode: "",
    staffUserId: "",
    dueDate: ""
  });
  const [bulkForm, setBulkForm] = useState({
    checklistId: "",
    checklistVersionId: "",
    schoolIds: [],
    supervisorIds: [],
    dueDate: ""
  });

  const load = () => fetch(`${API_BASE}/assignments`, { headers }).then((r) => r.json()).then(setItems);
  useEffect(() => {
    load();
  }, [headers]);
  useEffect(() => {
    fetch(`${API_BASE}/checklists`, { headers }).then((r) => r.json()).then(setChecklists);
    fetch(`${API_BASE}/users/supervisors`, { headers }).then((r) => r.json()).then(setSupervisors);
    fetch(`${API_BASE}/schools`, { headers }).then((r) => r.json()).then(setSchools);
    fetch(`${API_BASE}/teachers`, { headers }).then((r) => r.json()).then(setTeachers);
    fetch(`${API_BASE}/school-stuff`, { headers }).then((r) => r.json()).then(setSchoolStuff);
  }, [headers]);
  useEffect(() => {
    if (!form.checklistId) return;
    fetch(`${API_BASE}/checklists/${form.checklistId}/versions`, { headers }).then((r) => r.json()).then(setVersions);
  }, [form.checklistId, headers]);
  useEffect(() => {
    if (!bulkForm.checklistId) return;
    fetch(`${API_BASE}/checklists/${bulkForm.checklistId}/versions`, { headers }).then((r) => r.json()).then(setVersions);
  }, [bulkForm.checklistId, headers]);

  const toLocalDatetimeValue = (iso) => {
    if (!iso) return "";
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return "";
    // datetime-local expects "YYYY-MM-DDTHH:mm"
    return d.toISOString().slice(0, 16);
  };

  const openCreateAssignment = () => {
    setEditingAssignmentId("");
    setForm({
      checklistId: "",
      checklistVersionId: "",
      supervisorId: "",
      targetType: "SCHOOL",
      schoolId: "",
      teacherId: "",
      targetGradeCode: "",
      staffUserId: "",
      dueDate: ""
    });
    setModalOpen(true);
  };

  const openEditAssignment = (row) => {
    setEditingAssignmentId(row.id);
    setForm({
      checklistId: row.checklistId ?? "",
      checklistVersionId: row.checklistVersionId ?? "",
      supervisorId: row.supervisorId ?? "",
      targetType: row.targetType ?? "SCHOOL",
      schoolId: row.schoolId ?? "",
      teacherId: row.teacherId ?? "",
      targetGradeCode: row.targetGradeCode ?? "",
      staffUserId: row.staffUserId ?? "",
      dueDate: toLocalDatetimeValue(row.dueDate)
    });
    setModalOpen(true);
  };

  const saveAssignment = async (e) => {
    e.preventDefault();
    if (!form.checklistId || !form.checklistVersionId || !form.supervisorId) return;
    setEditBusy(true);
    try {
      const payload = {
        checklistId: form.checklistId,
        checklistVersionId: form.checklistVersionId,
        supervisorId: form.supervisorId,
        targetType: form.targetType,
        schoolId: ASSIGNMENT_SCHOOL_TARGETS.has(form.targetType) ? form.schoolId : null,
        teacherId: form.targetType === "TEACHER" ? form.teacherId : null,
        targetGradeCode: form.targetType === "TEACHER" ? form.targetGradeCode : null,
        staffUserId: form.targetType === "SCHOOL_STAFF" ? form.staffUserId : null,
        dueDate: form.dueDate ? new Date(form.dueDate).toISOString() : null
      };

      const url = editingAssignmentId ? `${API_BASE}/assignments/${editingAssignmentId}` : `${API_BASE}/assignments`;
      const method = editingAssignmentId ? "PATCH" : "POST";
      const res = await fetch(url, { method, headers: jsonHeaders(headers), body: JSON.stringify(payload) });
      if (!res.ok) {
        let details = null;
        try {
          details = await res.json();
        } catch (_) {}
        throw new Error(details?.message || str.assignmentFailed);
      }

      setMessage({
        type: "ok",
        text: editingAssignmentId ? str.assignmentUpdated : str.assignmentCreated
      });
      setModalOpen(false);
      setEditingAssignmentId("");
      await load();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setEditBusy(false);
    }
  };

  const openDeleteAssignment = (id) => {
    setDeleteAssignmentId(id);
    setDeleteModalOpen(true);
  };

  const openBulkAssignments = () => {
    setBulkForm({
      checklistId: "",
      checklistVersionId: "",
      schoolIds: [],
      supervisorIds: [],
      dueDate: ""
    });
    setBulkModalOpen(true);
  };

  const saveBulkAssignments = async (e) => {
    e.preventDefault();
    if (!bulkForm.checklistId || !bulkForm.checklistVersionId) return;
    setBulkBusy(true);
    try {
      const payload = {
        checklistId: bulkForm.checklistId,
        checklistVersionId: bulkForm.checklistVersionId,
        schoolIds: bulkForm.schoolIds,
        supervisorIds: bulkForm.supervisorIds,
        dueDate: bulkForm.dueDate ? new Date(bulkForm.dueDate).toISOString() : null
      };
      const res = await fetch(`${API_BASE}/assignments/bulk-create`, {
        method: "POST",
        headers: jsonHeaders(headers),
        body: JSON.stringify(payload)
      });
      if (!res.ok) {
        const j = await res.json().catch(() => ({}));
        throw new Error(j.message || "Bulk assignment failed");
      }
      const result = await res.json();
      setMessage({
        type: "ok",
        text: `Bulk created ${result.created || 0}. Skipped duplicates: ${result.skippedDuplicate || 0}, no supervisor: ${result.skippedNoEligibleSupervisor || 0}, out of scope: ${result.skippedOutOfScope || 0}.`
      });
      setBulkModalOpen(false);
      await load();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setBulkBusy(false);
    }
  };

  const confirmDeleteAssignment = async (e) => {
    e.preventDefault();
    if (!deleteAssignmentId) return;
    setDeleteBusy(true);
    try {
      const res = await fetch(`${API_BASE}/assignments/${deleteAssignmentId}`, {
        method: "DELETE",
        headers
      });
      if (!res.ok) {
        let details = null;
        try {
          details = await res.json();
        } catch (_) {}
        throw new Error(details?.message || str.assignmentDeleteFailed);
      }
      setDeleteModalOpen(false);
      setMessage({ type: "ok", text: str.assignmentDeleted });
      await load();
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    } finally {
      setDeleteBusy(false);
      setDeleteAssignmentId("");
    }
  };

  const checklistById = useMemo(
    () => new Map((checklists || []).map((c) => [c.id, c])),
    [checklists]
  );
  const supervisorById = useMemo(
    () => new Map((supervisors || []).map((u) => [u.id, u])),
    [supervisors]
  );
  const schoolById = useMemo(
    () => new Map((schools || []).map((s) => [s.id, s])),
    [schools]
  );
  const teacherById = useMemo(
    () => new Map((teachers || []).map((t) => [t.id, t])),
    [teachers]
  );
  const staffById = useMemo(
    () => new Map((schoolStuff || []).map((s) => [s.id, s])),
    [schoolStuff]
  );

  const exportAssignments = async () => {
    try {
      await downloadXlsx(`${API_BASE}/assignments/export`, headers, "assignments-export.xlsx");
    } catch (err) {
      setMessage({ type: "error", text: err.message });
    }
  };

  return (
    <>
      <PageHeader
        title={str.assignmentsTitle}
        subtitle={str.assignmentsSubtitle}
        action={
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <PrimaryButton onClick={openCreateAssignment}>{str.newAssignment}</PrimaryButton>
            <GhostButton onClick={openBulkAssignments}>Bulk assign</GhostButton>
            <GhostButton onClick={exportAssignments}>Export</GhostButton>
          </div>
        }
      />
      {message && <Alert type={message.type}>{message.text}</Alert>}
      <GhostButton onClick={load} style={{ marginBottom: 12 }}>
        {str.refreshList}
      </GhostButton>
      <DataTable
        columns={[
          { key: "checklist", label: "Checklist" },
          { key: "targetName", label: "Target name" },
          { key: "position", label: "Position" },
          { key: "targetSchool", label: "Target school" },
          { key: "supervisor", label: "Supervisor" },
          { key: "targetType", label: str.reportColTarget },
          { key: "status", label: str.columnStatus },
          { key: "dueDate", label: str.columnDue },
          { key: "actions", label: str.actions }
        ]}
        rows={items}
        empty={str.noAssignmentsRows}
        renderCell={(key, row) => {
          if (key === "checklist") return checklistById.get(row.checklistId)?.title || "—";
          if (key === "targetName") {
            if (row.targetType === "TEACHER") return teacherById.get(row.teacherId)?.name || "—";
            if (row.targetType === "SCHOOL_STAFF") return staffById.get(row.staffUserId)?.fullName || "—";
            if (row.targetType === "DIRECTOR") {
              const school = schoolById.get(row.schoolId);
              const director = (schoolStuff || []).find((s) => s.type === "SCHOOL_DIRECTOR" && s.schoolId === row.schoolId);
              return director?.fullName || school?.name || "—";
            }
            return schoolById.get(row.schoolId)?.name || "—";
          }
          if (key === "position") {
            if (row.targetType === "TEACHER") return "Teacher";
            if (row.targetType === "DIRECTOR") return "School Director";
            if (row.targetType === "SCHOOL_STAFF") return staffById.get(row.staffUserId)?.type || "School Staff";
            return "School";
          }
          if (key === "targetSchool") {
            if (row.schoolId) return schoolById.get(row.schoolId)?.name || "—";
            if (row.targetType === "TEACHER") return teacherById.get(row.teacherId)?.schoolName || "—";
            if (row.targetType === "SCHOOL_STAFF") return staffById.get(row.staffUserId)?.schoolName || "—";
            return "—";
          }
          if (key === "supervisor") return supervisorById.get(row.supervisorId)?.fullName || "—";
          if (key === "dueDate") return row.dueDate || "—";
          if (key === "targetType") return formatTargetWithGrade(row.targetType, row.targetGradeCode);
          if (key === "actions") {
            const isPending = row.status === "PENDING";
            return (
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <GhostButton onClick={() => openEditAssignment(row)} disabled={editBusy || !isPending}>
                  {str.edit}
                </GhostButton>
                <GhostButton onClick={() => openDeleteAssignment(row.id)} disabled={deleteBusy || !isPending}>
                  {str.delete}
                </GhostButton>
              </div>
            );
          }
          return row[key];
        }}
      />

      <Modal
        open={bulkModalOpen}
        onClose={() => setBulkModalOpen(false)}
        title="Bulk create assignments"
        wide
      >
        <form onSubmit={saveBulkAssignments} style={{ display: "grid", gap: 12 }}>
          <div>
            <Label>Checklist</Label>
            <select
              required
              value={bulkForm.checklistId}
              onChange={(e) => setBulkForm((p) => ({ ...p, checklistId: e.target.value, checklistVersionId: "" }))}
              style={{ padding: 10, fontFamily: t.font, borderRadius: t.radius, border: `1px solid ${t.line}`, width: "100%" }}
            >
              <option value="">{str.selectChecklist}</option>
              {checklists.map((c) => (
                <option key={c.id} value={c.id}>{c.title}</option>
              ))}
            </select>
          </div>
          <div>
            <Label>Checklist version</Label>
            <select
              required
              value={bulkForm.checklistVersionId}
              onChange={(e) => setBulkForm((p) => ({ ...p, checklistVersionId: e.target.value }))}
              style={{ padding: 10, fontFamily: t.font, borderRadius: t.radius, border: `1px solid ${t.line}`, width: "100%" }}
            >
              <option value="">{str.selectVersion}</option>
              {versions.map((v) => (
                <option key={v.id} value={v.id}>v{v.versionNo} ({v.status})</option>
              ))}
            </select>
          </div>
          <div>
            <Label>Schools (optional, defaults to all in scope)</Label>
            <select
              multiple
              value={bulkForm.schoolIds}
              onChange={(e) => setBulkForm((p) => ({
                ...p,
                schoolIds: Array.from(e.target.selectedOptions).map((o) => o.value)
              }))}
              style={{ minHeight: 120, padding: 10, fontFamily: t.font, borderRadius: t.radius, border: `1px solid ${t.line}`, width: "100%" }}
            >
              {schools.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          </div>
          <div>
            <Label>Supervisors pool (optional, defaults to all in scope)</Label>
            <select
              multiple
              value={bulkForm.supervisorIds}
              onChange={(e) => setBulkForm((p) => ({
                ...p,
                supervisorIds: Array.from(e.target.selectedOptions).map((o) => o.value)
              }))}
              style={{ minHeight: 120, padding: 10, fontFamily: t.font, borderRadius: t.radius, border: `1px solid ${t.line}`, width: "100%" }}
            >
              {supervisors.map((u) => (
                <option key={u.id} value={u.id}>{u.fullName}</option>
              ))}
            </select>
          </div>
          <p style={{ margin: 0, fontSize: 13, color: t.muted }}>
            Grade scope is taken from the selected checklist grade group; bulk matching uses that together with each school.
          </p>
          <div>
            <Label>Due date override (optional)</Label>
            <Input
              type="datetime-local"
              value={bulkForm.dueDate}
              onChange={(e) => setBulkForm((p) => ({ ...p, dueDate: e.target.value }))}
            />
          </div>
          <PrimaryButton type="submit" disabled={bulkBusy}>
            {bulkBusy ? "Creating..." : "Create bulk assignments"}
          </PrimaryButton>
        </form>
      </Modal>

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editingAssignmentId ? str.editAssignmentTitle : str.newAssignment}
        wide
      >
        <form onSubmit={saveAssignment} style={{ display: "grid", gap: 12 }}>
          <div>
            <Label htmlFor="assignment-form-checklist">{str.assignmentFormChecklist}</Label>
            <select
              id="assignment-form-checklist"
              required
              value={form.checklistId}
              onChange={(e) => setForm((p) => ({ ...p, checklistId: e.target.value, checklistVersionId: "" }))}
              aria-label={str.assignmentFormChecklist}
              style={{ padding: 10, fontFamily: t.font, borderRadius: t.radius, border: `1px solid ${t.line}`, width: "100%" }}
            >
              <option value="">{str.selectChecklist}</option>
              {checklists.map((c) => (
                <option key={c.id} value={c.id}>{c.title}{c.purpose ? ` · ${c.purpose}` : ""}</option>
              ))}
            </select>
          </div>
          <div>
            <Label htmlFor="assignment-form-version">{str.assignmentFormVersion}</Label>
            <select
              id="assignment-form-version"
              required
              value={form.checklistVersionId}
              onChange={(e) => setForm((p) => ({ ...p, checklistVersionId: e.target.value }))}
              aria-label={str.assignmentFormVersion}
              style={{ padding: 10, fontFamily: t.font, borderRadius: t.radius, border: `1px solid ${t.line}`, width: "100%" }}
            >
              <option value="">{str.selectVersion}</option>
              {versions.map((v) => (
                <option key={v.id} value={v.id}>v{v.versionNo} ({v.status})</option>
              ))}
            </select>
          </div>
          <div>
            <Label htmlFor="assignment-form-supervisor">{str.assignmentFormSupervisor}</Label>
            <select
              id="assignment-form-supervisor"
              required
              value={form.supervisorId}
              onChange={(e) => setForm((p) => ({ ...p, supervisorId: e.target.value }))}
              aria-label={str.assignmentFormSupervisor}
              style={{ padding: 10, fontFamily: t.font, borderRadius: t.radius, border: `1px solid ${t.line}`, width: "100%" }}
            >
              <option value="">{str.selectSupervisor}</option>
              {supervisors.map((u) => (
                <option key={u.id} value={u.id}>{u.fullName}</option>
              ))}
            </select>
          </div>
          <div>
            <Label htmlFor="assignment-form-target-type">{str.assignmentFormTargetType}</Label>
            <select
              id="assignment-form-target-type"
              value={form.targetType}
              onChange={(e) => {
                const next = e.target.value;
                setForm((p) => ({
                  ...p,
                  targetType: next,
                  schoolId: ASSIGNMENT_SCHOOL_TARGETS.has(next) ? p.schoolId : "",
                  teacherId: next === "TEACHER" ? p.teacherId : "",
                  targetGradeCode: next === "TEACHER" ? p.targetGradeCode : "",
                  staffUserId: next === "SCHOOL_STAFF" ? p.staffUserId : ""
                }));
              }}
              aria-label={str.assignmentFormTargetType}
              style={{ padding: 10, fontFamily: t.font, borderRadius: t.radius, border: `1px solid ${t.line}`, width: "100%" }}
            >
              <option value="SCHOOL">{str.targetTypeSchool}</option>
              <option value="TEACHER">{str.targetTypeTeacher}</option>
              <option value="DIRECTOR">{str.targetTypeDirector}</option>
              <option value="SCHOOL_STAFF">{str.targetTypeSchoolStaff}</option>
            </select>
          </div>
          {ASSIGNMENT_SCHOOL_TARGETS.has(form.targetType) && (
            <div>
              <Label htmlFor="assignment-form-school">{str.assignmentFormSchool}</Label>
              <select
                id="assignment-form-school"
                required
                value={form.schoolId}
                onChange={(e) => setForm((p) => ({ ...p, schoolId: e.target.value }))}
                aria-label={str.assignmentFormSchool}
                style={{ padding: 10, fontFamily: t.font, borderRadius: t.radius, border: `1px solid ${t.line}`, width: "100%" }}
              >
                <option value="">{str.selectSchool}</option>
                {schools.map((s) => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </select>
            </div>
          )}
          {form.targetType === "TEACHER" && (
            <div>
              <Label htmlFor="assignment-form-teacher">{str.assignmentFormTeacher}</Label>
              <select
                id="assignment-form-teacher"
                required
                value={form.teacherId}
                onChange={(e) => setForm((p) => ({ ...p, teacherId: e.target.value }))}
                aria-label={str.assignmentFormTeacher}
                style={{ padding: 10, fontFamily: t.font, borderRadius: t.radius, border: `1px solid ${t.line}`, width: "100%" }}
              >
                <option value="">{str.selectTeacher}</option>
                {teachers.map((teacher) => (
                  <option key={teacher.id} value={teacher.id}>{teacher.name} · {teacher.subject}{teacher.schoolName ? ` · ${teacher.schoolName}` : ""}</option>
                ))}
              </select>
            </div>
          )}
          {form.targetType === "TEACHER" && (
            <div>
              <Label htmlFor="assignment-form-target-grade">Target grade</Label>
              <select
                id="assignment-form-target-grade"
                required
                value={form.targetGradeCode}
                onChange={(e) => setForm((p) => ({ ...p, targetGradeCode: e.target.value }))}
                aria-label="Target grade"
                style={{ padding: 10, fontFamily: t.font, borderRadius: t.radius, border: `1px solid ${t.line}`, width: "100%" }}
              >
                <option value="">Select grade…</option>
                {CANONICAL_GRADE_CODES.map((gradeCode) => (
                  <option key={gradeCode} value={gradeCode}>{gradeCode}</option>
                ))}
              </select>
            </div>
          )}
          {form.targetType === "SCHOOL_STAFF" && (
            <div>
              <Label htmlFor="assignment-form-staff">{str.assignmentFormStaff}</Label>
              <select
                id="assignment-form-staff"
                required
                value={form.staffUserId}
                onChange={(e) => setForm((p) => ({ ...p, staffUserId: e.target.value }))}
                aria-label={str.assignmentFormStaff}
                style={{ padding: 10, fontFamily: t.font, borderRadius: t.radius, border: `1px solid ${t.line}`, width: "100%" }}
              >
                <option value="">{str.selectStaffMember}</option>
                {schoolStuff
                  .filter((s) => s.type && s.type !== "TEACHER")
                  .map((s) => (
                    <option key={`${s.type}-${s.id}`} value={s.id}>
                      {s.fullName} ({s.type})
                    </option>
                  ))}
              </select>
            </div>
          )}
          <div>
            <Label htmlFor="assignment-form-due">{str.assignmentFormDueDate}</Label>
            <Input
              id="assignment-form-due"
              type="datetime-local"
              value={form.dueDate}
              onChange={(e) => setForm((p) => ({ ...p, dueDate: e.target.value }))}
              aria-label={str.assignmentFormDueDate}
              style={{ width: "100%" }}
            />
          </div>
          <PrimaryButton type="submit" disabled={editBusy}>
            {editBusy ? str.savingEllipsis : editingAssignmentId ? str.saveChanges : str.create}
          </PrimaryButton>
        </form>
      </Modal>

      <Modal
        open={deleteModalOpen}
        onClose={() => (!deleteBusy ? setDeleteModalOpen(false) : null)}
        title={str.deleteAssignmentTitle}
      >
        <form onSubmit={confirmDeleteAssignment} style={{ display: "grid", gap: 12 }}>
          <p style={{ margin: 0, color: t.muted, fontSize: 14 }}>{str.deletePendingOnlyNote}</p>
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
            <GhostButton type="button" onClick={() => setDeleteModalOpen(false)} disabled={deleteBusy}>
              {str.cancel}
            </GhostButton>
            <PrimaryButton type="submit" disabled={deleteBusy}>
              {deleteBusy ? str.deletingEllipsis : str.delete}
            </PrimaryButton>
          </div>
        </form>
      </Modal>
    </>
  );
}

function MiniStatCard({ label, value }) {
  return (
    <Card style={{ padding: 12 }}>
      <div style={{ fontSize: 12, color: t.muted }}>{label}</div>
      <div style={{ fontSize: 20, fontWeight: 700, marginTop: 4 }}>{value}</div>
    </Card>
  );
}

function SimplePieChart({ title, data, colors }) {
  const safeData = (data || []).filter((d) => Number(d.value) > 0);
  const total = safeData.reduce((sum, d) => sum + d.value, 0);
  const radius = 52;
  const cx = 64;
  const cy = 64;
  let running = -Math.PI / 2;

  const arcs = safeData.map((d, idx) => {
    const angle = (d.value / total) * Math.PI * 2;
    const start = running;
    const end = running + angle;
    running = end;
    const x1 = cx + radius * Math.cos(start);
    const y1 = cy + radius * Math.sin(start);
    const x2 = cx + radius * Math.cos(end);
    const y2 = cy + radius * Math.sin(end);
    const largeArc = angle > Math.PI ? 1 : 0;
    const color = colors[idx % colors.length];
    const path = `M ${cx} ${cy} L ${x1} ${y1} A ${radius} ${radius} 0 ${largeArc} 1 ${x2} ${y2} Z`;
    return { path, color, label: d.label, value: d.value };
  });

  return (
    <Card style={{ padding: 14 }}>
      <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>{title}</div>
      {total === 0 ? (
        <div style={{ color: t.muted, fontSize: 13 }}>No data</div>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "140px 1fr", gap: 12, alignItems: "center" }}>
          <svg width="128" height="128" viewBox="0 0 128 128" aria-label={title}>
            {arcs.map((a, idx) => (
              <path key={idx} d={a.path} fill={a.color} stroke={t.card} strokeWidth="1" />
            ))}
          </svg>
          <div style={{ display: "grid", gap: 6 }}>
            {arcs.map((a, idx) => (
              <div key={idx} style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 12 }}>
                <span style={{ width: 10, height: 10, borderRadius: 999, background: a.color, display: "inline-block" }} />
                <span style={{ flex: 1 }}>{a.label}</span>
                <strong>{a.value}</strong>
              </div>
            ))}
          </div>
        </div>
      )}
    </Card>
  );
}

function SupervisionActivityPage({ headers }) {
  const { str, locale } = useI18n();
  const [summaries, setSummaries] = useState([]);
  const [detailOpen, setDetailOpen] = useState(false);
  const [selected, setSelected] = useState(null);
  const [visits, setVisits] = useState([]);
  const [loadingVisits, setLoadingVisits] = useState(false);
  const [error, setError] = useState("");

  const loadSummaries = useCallback(() => {
    setError("");
    fetch(`${API_BASE}/supervision/supervisor-summaries`, { headers })
      .then((r) => (r.ok ? r.json() : Promise.reject()))
      .then(setSummaries)
      .catch(() => setError(str.activityLoadFailed));
  }, [headers, str]);

  useEffect(() => {
    loadSummaries();
  }, [loadSummaries]);

  const openVisits = async (row) => {
    setSelected(row);
    setDetailOpen(true);
    setLoadingVisits(true);
    setVisits([]);
    try {
      const r = await fetch(`${API_BASE}/supervision/supervisors/${row.supervisorId}/visits`, { headers });
      if (!r.ok) throw new Error();
      setVisits(await r.json());
    } catch {
      setError(str.activityVisitsFailed);
    } finally {
      setLoadingVisits(false);
    }
  };

  const exportActivity = async () => {
    try {
      await downloadXlsx(`${API_BASE}/supervision/export`, headers, "activity-export.xlsx");
    } catch (err) {
      setError(err.message || str.activityLoadFailed);
    }
  };

  const tableRows = useMemo(
    () =>
      summaries.map((s) => {
        const openAssignments = (s.pendingAssignments || 0) + (s.inProgressAssignments || 0);
        const totalAssignments = openAssignments + (s.completedAssignments || 0);
        const coveragePct = totalAssignments > 0 ? Math.round(((s.completedAssignments || 0) * 100) / totalAssignments) : 0;
        return {
          ...s,
          id: s.supervisorId,
          name: `${s.fullName} (${s.username})`,
          openAssignments,
          coveragePct: `${coveragePct}%`
        };
      }),
    [summaries]
  );

  const analytics = useMemo(() => {
    const targetTypeMap = new Map();
    const locationMap = new Map();
    const gradeMap = new Map();
    let totalDistance = 0;
    let distanceCount = 0;
    for (const v of visits) {
      const targetType = v.targetType || "—";
      targetTypeMap.set(targetType, (targetTypeMap.get(targetType) || 0) + 1);
      const loc = v.locationStatus || "UNKNOWN";
      locationMap.set(loc, (locationMap.get(loc) || 0) + 1);
      if (v.targetGradeCode) gradeMap.set(v.targetGradeCode, (gradeMap.get(v.targetGradeCode) || 0) + 1);
      if (typeof v.distanceFromSchoolMeters === "number") {
        totalDistance += v.distanceFromSchoolMeters;
        distanceCount += 1;
      }
    }
    const locationOk = (locationMap.get("WITHIN_RANGE") || 0) + (locationMap.get("MANUAL_OVERRIDE") || 0);
    const geoCompliancePct = visits.length > 0 ? Math.round((locationOk * 100) / visits.length) : 0;
    return {
      targetTypeData: Array.from(targetTypeMap.entries()).map(([label, value]) => ({ label, value })),
      locationData: Array.from(locationMap.entries()).map(([label, value]) => ({ label, value })),
      gradeData: Array.from(gradeMap.entries())
        .map(([label, value]) => ({ label, value }))
        .sort((a, b) => b.value - a.value),
      geoCompliancePct,
      avgDistance: distanceCount > 0 ? Math.round(totalDistance / distanceCount) : null
    };
  }, [visits]);

  return (
    <>
      <PageHeader
        title={str.supervisorActivityTitle}
        subtitle={str.supervisorActivitySubtitle}
        action={
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <GhostButton onClick={loadSummaries}>{str.refresh}</GhostButton>
            <GhostButton onClick={exportActivity}>Export</GhostButton>
          </div>
        }
      />
      {error && <Alert type="error">{error}</Alert>}
      <DataTable
        columns={[
          { key: "name", label: str.activitySupervisorCol },
          { key: "visitsCompleted", label: str.activityVisitsCol },
          { key: "openAssignments", label: "Open" },
          { key: "completedAssignments", label: str.activityDoneCol },
          { key: "overdueAssignments", label: str.activityOverdueCol },
          { key: "coveragePct", label: "Coverage" },
          { key: "_", label: "" }
        ]}
        rows={tableRows}
        empty={str.activityNoSupervisors}
        renderCell={(key, row) => {
          if (key === "_") {
            return <GhostButton onClick={() => openVisits(row)}>{str.activityDetails}</GhostButton>;
          }
          return row[key];
        }}
      />

      <Modal
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        title={selected ? `${str.visitsModalTitle} · ${selected.fullName}` : str.visitsModalTitle}
        wide
      >
        {loadingVisits ? (
          <p style={{ color: t.muted }}>{str.loadingEllipsis}</p>
        ) : (
          <div style={{ display: "grid", gap: 14 }}>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(3, minmax(160px, 1fr))", gap: 10 }}>
              <MiniStatCard label="Completed visits" value={visits.length} />
              <MiniStatCard label="Geo compliance" value={`${analytics.geoCompliancePct}%`} />
              <MiniStatCard label="Avg distance" value={analytics.avgDistance == null ? "—" : `${analytics.avgDistance} m`} />
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <SimplePieChart
                title="Visits by target type"
                data={analytics.targetTypeData}
                colors={["#111827", "#2563eb", "#7c3aed", "#059669", "#d97706"]}
              />
              <SimplePieChart
                title="Location status mix"
                data={analytics.locationData}
                colors={["#059669", "#16a34a", "#d97706", "#dc2626", "#6b7280"]}
              />
            </div>

            <Card style={{ padding: 14 }}>
              <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 10 }}>Teacher grade coverage</div>
              {analytics.gradeData.length === 0 ? (
                <div style={{ color: t.muted, fontSize: 13 }}>No teacher-grade visits in this slice.</div>
              ) : (
                <div style={{ display: "grid", gap: 8 }}>
                  {analytics.gradeData.map((g) => {
                    const width = Math.max(8, Math.round((g.value * 100) / Math.max(...analytics.gradeData.map((x) => x.value))));
                    return (
                      <div key={g.label} style={{ display: "grid", gridTemplateColumns: "60px 1fr 32px", alignItems: "center", gap: 8 }}>
                        <div style={{ fontSize: 12 }}>{g.label}</div>
                        <div style={{ background: "#eef2ff", borderRadius: 8, overflow: "hidden", height: 10 }}>
                          <div style={{ width: `${width}%`, height: "100%", background: "#4f46e5" }} />
                        </div>
                        <div style={{ fontSize: 12, textAlign: "right" }}>{g.value}</div>
                      </div>
                    );
                  })}
                </div>
              )}
            </Card>

            <DataTable
              columns={[
                { key: "completedAt", label: str.reportColCompleted },
                { key: "checklistTitle", label: str.reportColChecklist },
                { key: "targetType", label: str.reportColTarget },
                { key: "place", label: str.reportColPlace },
                { key: "dueDate", label: str.columnDue },
                { key: "geo", label: str.reportColGeo }
              ]}
              rows={visits}
              empty={str.noCompletedVisits}
              renderCell={(key, row) => {
                if (key === "completedAt") return formatReportInstant(row.completedAt, locale);
                if (key === "targetType") return formatTargetWithGrade(row.targetType, row.targetGradeCode);
                if (key === "place") {
                  if (row.targetType === "TEACHER" && row.teacherName) return `${row.teacherName}${row.schoolName ? ` · ${row.schoolName}` : ""}`;
                  if (row.targetType === "SCHOOL_STAFF" && row.staffFullName) return `${row.staffFullName}${row.schoolName ? ` · ${row.schoolName}` : ""}`;
                  return row.schoolName || "—";
                }
                if (key === "dueDate") return formatReportInstant(row.dueDate, locale);
                if (key === "geo") {
                  const parts = [];
                  if (row.locationStatus) parts.push(String(row.locationStatus).replace(/_/g, " "));
                  if (row.distanceFromSchoolMeters != null) parts.push(`${Math.round(row.distanceFromSchoolMeters)} m`);
                  return parts.length ? parts.join(" · ") : "—";
                }
                return row[key] ?? "—";
              }}
            />
          </div>
        )}
      </Modal>
    </>
  );
}

function formatReportInstant(iso, locale) {
  if (!iso) return "—";
  try {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return String(iso);
    const loc = locale === "am" ? "am-ET" : undefined;
    return d.toLocaleString(loc, { dateStyle: "medium", timeStyle: "short" });
  } catch {
    return String(iso);
  }
}

function ReportsPage({ headers }) {
  const { str, locale } = useI18n();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [preview, setPreview] = useState(null);
  const [openingReviewId, setOpeningReviewId] = useState("");
  const [pdfError, setPdfError] = useState(null);

  const loadList = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`${API_BASE}/reports/submitted-reviews`, { headers });
      if (!res.ok) {
        const j = await res.json().catch(() => ({}));
        throw new Error(j.message || str.couldNotLoadReports);
      }
      const data = await res.json();
      setRows(Array.isArray(data) ? data : []);
    } catch (e) {
      setError(e.message || str.failedToLoadShort);
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [headers, str]);

  useEffect(() => {
    loadList();
  }, [loadList]);

  const closePreview = useCallback(() => {
    setPreview((prev) => {
      if (prev?.url) {
        try {
          URL.revokeObjectURL(prev.url);
        } catch {
          /* ignore */
        }
      }
      return null;
    });
    setPdfError(null);
  }, []);

  const openPdf = async (reviewId) => {
    setOpeningReviewId(reviewId);
    setPdfError(null);
    try {
      const res = await fetch(`${API_BASE}/reports/reviews/${reviewId}/pdf`, { headers });
      if (!res.ok) {
        const j = await res.json().catch(() => ({}));
        throw new Error(j.message || str.couldNotLoadPdf);
      }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      setPreview((prev) => {
        if (prev?.url) {
          try {
            URL.revokeObjectURL(prev.url);
          } catch {
            /* ignore */
          }
        }
        return { reviewId, url };
      });
    } catch (e) {
      setPdfError(e.message || str.pdfFailedShort);
    } finally {
      setOpeningReviewId("");
    }
  };

  const printPreview = () => {
    const iframe = document.getElementById("report-pdf-iframe");
    if (iframe?.contentWindow) {
      iframe.contentWindow.focus();
      iframe.contentWindow.print();
    }
  };

  return (
    <>
      <PageHeader title={str.reportsTitle} subtitle={str.reportsSubtitle} action={<GhostButton onClick={loadList}>{str.refresh}</GhostButton>} />
      {error && <Alert type="error">{error}</Alert>}
      {pdfError && <Alert type="error">{pdfError}</Alert>}

      {loading ? (
        <p style={{ color: t.muted }}>{str.loadingReports}</p>
      ) : rows.length === 0 ? (
        <Card style={{ padding: 24 }}>
          <p style={{ margin: 0, color: t.muted }}>{str.noReportsYet}</p>
        </Card>
      ) : (
        <DataTable
          columns={[
            { key: "completed", label: str.reportColCompleted },
            { key: "checklist", label: str.reportColChecklist },
            { key: "supervisor", label: str.reportColSupervisor },
            { key: "target", label: str.reportColTarget },
            { key: "place", label: str.reportColPlace },
            { key: "geo", label: str.reportColGeo },
            { key: "actions", label: "" }
          ]}
          rows={rows}
          empty={str.noReportsTable}
          renderCell={(key, row) => {
            if (key === "completed") return formatReportInstant(row.completedAt, locale);
            if (key === "checklist") return row.checklistTitle || "—";
            if (key === "supervisor") {
              const n = row.supervisorFullName || "—";
              const u = row.supervisorUsername ? ` (${row.supervisorUsername})` : "";
              return `${n}${u}`;
            }
            if (key === "target") return formatTargetWithGrade(row.targetType, row.targetGradeCode);
            if (key === "place") {
              if (row.targetType === "TEACHER" && row.teacherName) return row.teacherName;
              if (row.targetType === "SCHOOL_STAFF" && row.staffFullName) return row.staffFullName;
              if (row.schoolName) return row.schoolName;
              return "—";
            }
            if (key === "geo") {
              const parts = [];
              if (row.locationStatus) parts.push(String(row.locationStatus).replace(/_/g, " "));
              if (row.distanceFromSchoolMeters != null) parts.push(`${Math.round(row.distanceFromSchoolMeters)} m`);
              return parts.length ? parts.join(" · ") : "—";
            }
            if (key === "actions") {
              return (
                <PrimaryButton
                  type="button"
                  disabled={openingReviewId === row.reviewId}
                  onClick={() => openPdf(row.reviewId)}
                >
                  {openingReviewId === row.reviewId ? str.loadingEllipsis : str.viewReport}
                </PrimaryButton>
              );
            }
            return row[key] ?? "—";
          }}
        />
      )}

      {preview && (
        <div
          role="presentation"
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(0,0,0,0.45)",
            zIndex: 2000,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: 16
          }}
          onClick={closePreview}
          onKeyDown={(e) => e.key === "Escape" && closePreview()}
        >
          <div
            role="dialog"
            style={{
              background: t.card,
              borderRadius: t.radius + 4,
              width: "100%",
              maxWidth: 960,
              maxHeight: "92vh",
              display: "flex",
              flexDirection: "column",
              boxShadow: "0 20px 50px rgba(0,0,0,0.2)"
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                gap: 12,
                padding: "14px 18px",
                borderBottom: `1px solid ${t.line}`
              }}
            >
              <div style={{ fontWeight: 600, fontSize: 15 }}>{str.reviewReport}</div>
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <GhostButton type="button" onClick={printPreview}>
                  {str.print}
                </GhostButton>
                <GhostButton type="button" onClick={closePreview}>
                  {str.close}
                </GhostButton>
              </div>
            </div>
            <div style={{ flex: 1, minHeight: 0, padding: 12 }}>
              <iframe
                id="report-pdf-iframe"
                title={str.reviewPdfIframeTitle}
                src={preview.url}
                style={{
                  width: "100%",
                  height: "min(72vh, 640px)",
                  border: `1px solid ${t.line}`,
                  borderRadius: t.radius
                }}
              />
            </div>
          </div>
        </div>
      )}
    </>
  );
}

createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <I18nProvider>
      <App />
    </I18nProvider>
  </React.StrictMode>
);
