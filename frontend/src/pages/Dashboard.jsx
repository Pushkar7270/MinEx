import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { api } from "../api";

const CHART_COLORS = ["#c084b8", "#8b6cc1", "#6fc3df", "#7ddba3", "#e3b341"];

const shortName = (f) => (f && f.length > 28 ? f.slice(0, 27) + "…" : f || "—");

// Extractor field names are noisy ("602.14 MT", sentence-length headings,
// "... Page"). These are still selectable, but the default chart only plots
// labels that look like real metrics so the graph stays legible.
const isMetricLabel = (f) => {
  if (!f) return false;
  const s = f.trim();
  if (s.length < 3 || s.length > 40) return false;
  if (!/^[A-Za-z]/.test(s)) return false;
  if ((s.match(/[A-Za-z]/g) || []).length < 3) return false;
  return !/(\bpage\b|annexure|\bfy\s?\d)/i.test(s);
};

export default function Dashboard() {
  const navigate = useNavigate();
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState(null);
  const [series, setSeries] = useState([]);
  const [periodFilter, setPeriodFilter] = useState("ALL");
  const [fieldFilter, setFieldFilter] = useState("ALL");

  useEffect(() => {
    api
      .summary()
      .then((s) => {
        setSummary(s);
        const first = (s.categories || []).find((c) => c.publishedMetrics > 0) || s.categories?.[0];
        if (first) setSelected(first);
      })
      .catch((e) => setError(e.message));
  }, []);

  useEffect(() => {
    if (!selected) return;
    api
      .timeseries(selected.id)
      .then(setSeries)
      .catch((e) => setError(e.message));
  }, [selected]);

  // Reset period/metric filters whenever the user switches category.
  const pickCategory = (c) => {
    setSelected(c);
    setPeriodFilter("ALL");
    setFieldFilter("ALL");
  };

  const periods = useMemo(
    () => ["ALL", ...new Set(series.map((p) => p.period || "Undated"))],
    [series]
  );
  const filtered =
    periodFilter === "ALL"
      ? series
      : series.filter((p) => (p.period || "Undated") === periodFilter);

  // Every distinct metric in the current period slice, most-complete first.
  const fieldOptions = useMemo(() => {
    const coverage = new Map();
    for (const p of filtered) {
      if (!p.field) continue;
      const seen = coverage.get(p.field) || new Set();
      seen.add(p.period || "Undated");
      coverage.set(p.field, seen);
    }
    return [...coverage.entries()]
      .sort((a, b) => b[1].size - a[1].size || a[0].localeCompare(b[0]))
      .map(([name]) => name);
  }, [filtered]);

  // Aggregate latest value per field for the chart (period on X axis).
  // Null periods (e.g. spreadsheets with no FY mention) bucket as "Undated"
  // and sort last; same period+field collisions keep the last value.
  const chartData = useMemo(() => {
    const byPeriod = new Map();
    for (const p of filtered) {
      const period = p.period || "Undated";
      if (!byPeriod.has(period)) byPeriod.set(period, { period });
      byPeriod.get(period)[p.field || "—"] = Number(p.value) || 0;
    }
    const rank = (x) => (x === "Undated" ? "~~~" : String(x));
    return [...byPeriod.values()].sort((a, b) => rank(a.period).localeCompare(rank(b.period)));
  }, [filtered]);

  const fieldKeys = useMemo(() => {
    // A specific metric wins; otherwise show up to 5 real-metric labels by peak
    // value, so sentence/number headings don't drown the chart.
    if (fieldFilter !== "ALL" && fieldOptions.includes(fieldFilter)) return [fieldFilter];
    const meaningful = fieldOptions.filter(isMetricLabel);
    const pool = meaningful.length ? meaningful : fieldOptions;
    const peak = new Map();
    for (const p of filtered) {
      if (!p.field) continue;
      const v = Number(p.value) || 0;
      if (v > (peak.get(p.field) || 0)) peak.set(p.field, v);
    }
    return [...pool].sort((a, b) => (peak.get(b) || 0) - (peak.get(a) || 0)).slice(0, 5);
  }, [fieldFilter, fieldOptions, filtered]);

  const compact = (v) =>
    Math.abs(v) >= 1000 ? `${(v / 1000).toFixed(1)}k` : `${v}`;

  const verifiedTotal = (summary?.categories || []).reduce(
    (n, c) => n + (c.publishedMetrics || 0),
    0
  );

  if (error) return <div className="card"><div className="error">{error}</div></div>;
  if (!summary) return <div className="card"><p className="muted">Loading dashboard…</p></div>;

  return (
    <>
      <div className="tabs">
        <button className="tab active">Overview</button>
        <button className="tab" onClick={() => navigate("/review")}>Review Queue</button>
        <button className="tab" onClick={() => navigate("/chat")}>AI Chat</button>
      </div>
      <div className="grid-3">
        <div className="card hero">
          <span className="pill accent">Verified metrics</span>
          <div className="big">{verifiedTotal}</div>
          <p className="sub">
            {summary.documentsProcessed} of {summary.documentsTotal} documents processed ·{" "}
            {summary.fieldsPendingReview} fields awaiting review
          </p>
          <div className="row-btns">
            <span className="pill bad">{summary.openAnomalies} open anomalies</span>
            <span className="pill">{summary.documentsQueuedForOcr} queued for OCR</span>
          </div>
        </div>
        <div className="card">
          <h3>Decisions Powered by Data</h3>
          <p className="sub">
            Move beyond guesswork with verified mining figures tailored to your role.
          </p>
          <button className="btn" onClick={() => navigate("/review")}>
            Open Review Queue
          </button>
        </div>
        <div className="card">
          <h3>Categories</h3>
          <p className="sub">Latest verified figure per category</p>
          {(summary.categories || []).map((c) => (
            <div
              key={c.id}
              className="watch-row"
              onClick={() => pickCategory(c)}
              style={{ cursor: "pointer" }}
            >
              <div>
                <div className="name">{c.name}</div>
                <div className="meta">{c.publishedMetrics} verified</div>
              </div>
              <div className="val">
                {c.latestValue != null ? (
                  <>
                    <b>
                      {c.latestValue} {c.latestUnit || ""}
                    </b>
                    <div className="meta">{c.latestPeriod}</div>
                  </>
                ) : (
                  <span className="meta">no data yet</span>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>
      <div className="grid-2">
        <div className="card">
          <h3>Category Detail</h3>
          <p className="sub">Verified metrics per category</p>
          <div className="tiles">
            {(summary.categories || []).map((c) => (
              <div
                key={c.id}
                className="tile"
                onClick={() => pickCategory(c)}
                style={{
                  cursor: "pointer",
                  borderColor: selected?.id === c.id ? "rgba(192,132,184,.6)" : undefined,
                }}
              >
                <div className="t-name">{c.name}</div>
                <div className="t-val">
                  {c.latestValue != null ? `${c.latestValue} ${c.latestUnit || ""}` : "—"}
                </div>
                <div className="t-sub">
                  {c.latestField || "No verified figures"} · {c.publishedMetrics} verified
                </div>
              </div>
            ))}
          </div>
        </div>
        <div className="card">
          <h3>Performance {selected ? `— ${selected.name}` : ""}</h3>
          <p className="sub">Time series from approved data only</p>
          <div className="tabs" style={{ flexWrap: "wrap", alignItems: "center" }}>
            <select
              value={fieldFilter}
              onChange={(e) => setFieldFilter(e.target.value)}
              title="Choose which metric to chart"
              style={{ width: "auto", minWidth: 200, padding: "7px 12px", borderRadius: 20 }}
            >
              <option value="ALL">All metrics (top 5)</option>
              {fieldOptions.map((f) => (
                <option key={f} value={f}>{shortName(f)}</option>
              ))}
            </select>
            {periods.map((p) => (
              <button
                key={p}
                className={"tab" + (periodFilter === p ? " active" : "")}
                onClick={() => setPeriodFilter(p)}
              >
                {p === "ALL" ? "All periods" : p}
              </button>
            ))}
          </div>
          <div className="chart-wrap">
            {chartData.length === 0 ? (
              <p className="muted" style={{ padding: "60px 0", textAlign: "center" }}>
                No approved figures here yet — approve items in the Review Queue and they will chart here.
              </p>
            ) : (
            <ResponsiveContainer>
              <LineChart data={chartData} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
                <CartesianGrid stroke="rgba(255,255,255,.06)" vertical={false} />
                <XAxis dataKey="period" tick={{ fill: "#ab9cb9", fontSize: 11 }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fill: "#ab9cb9", fontSize: 11 }} axisLine={false} tickLine={false} width={60} tickFormatter={compact} domain={["auto", "auto"]} />
                <Tooltip
                  contentStyle={{ background: "#2c2138", border: "1px solid rgba(192,132,184,.4)", borderRadius: 12 }}
                  formatter={(v) => [Number(v).toLocaleString(), ""]}
                />
                <Legend wrapperStyle={{ fontSize: 11 }} />
                {fieldKeys.map((k, i) => (
                  <Line
                    key={k}
                    type="monotone"
                    dataKey={k}
                    name={shortName(k)}
                    connectNulls
                    stroke={CHART_COLORS[i % CHART_COLORS.length]}
                    strokeWidth={2.5}
                    dot={{ r: 4 }}
                    activeDot={{ r: 6 }}
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
            )}
          </div>
        </div>
      </div>
    </>
  );
}
