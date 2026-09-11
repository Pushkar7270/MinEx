import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { api } from "../api";

export default function Dashboard() {
  const navigate = useNavigate();
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState(null);
  const [series, setSeries] = useState([]);
  const [periodFilter, setPeriodFilter] = useState("ALL");

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

  const periods = useMemo(
    () => ["ALL", ...new Set(series.map((p) => p.period).filter(Boolean))],
    [series]
  );
  const filtered =
    periodFilter === "ALL" ? series : series.filter((p) => p.period === periodFilter);

  // Aggregate latest value per field name for the chart (period on X axis).
  const chartData = useMemo(() => {
    const byPeriod = new Map();
    for (const p of filtered) {
      if (!byPeriod.has(p.period)) byPeriod.set(p.period, { period: p.period });
      byPeriod.get(p.period)[p.field] = Number(p.value) || 0;
    }
    return [...byPeriod.values()].sort((a, b) => String(a.period).localeCompare(String(b.period)));
  }, [filtered]);

  const fieldKeys = useMemo(
    () => [...new Set(filtered.map((p) => p.field))].slice(0, 5),
    [filtered]
  );

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
        <button className="tab" onClick={() => navigate("/chat")}>AI Chat · Soon</button>
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
              onClick={() => setSelected(c)}
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
                onClick={() => setSelected(c)}
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
          <div className="tabs">
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
            <ResponsiveContainer>
              <AreaChart data={chartData}>
                <defs>
                  <linearGradient id="g0" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#c084b8" stopOpacity={0.7} />
                    <stop offset="100%" stopColor="#c084b8" stopOpacity={0.05} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke="rgba(255,255,255,.06)" vertical={false} />
                <XAxis dataKey="period" tick={{ fill: "#ab9cb9", fontSize: 11 }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fill: "#ab9cb9", fontSize: 11 }} axisLine={false} tickLine={false} width={60} />
                <Tooltip
                  contentStyle={{ background: "#2c2138", border: "1px solid rgba(192,132,184,.4)", borderRadius: 12 }}
                />
                {fieldKeys.map((k, i) => (
                  <Area
                    key={k}
                    type="monotone"
                    dataKey={k}
                    stroke={i === 0 ? "#c084b8" : "#8b6cc1"}
                    fill="url(#g0)"
                    strokeWidth={2}
                  />
                ))}
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>
    </>
  );
}
