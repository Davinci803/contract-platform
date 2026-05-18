import { useMemo, useState } from "react";
import Panel from "../components/Panel";
import StatusBadge from "../components/StatusBadge";
import { listCompatibilityReports } from "../api";

export default function CompatibilityPage({ onReportsLoaded = () => {} }) {
  const [reports, setReports] = useState([]);
  const [selectedReportId, setSelectedReportId] = useState("");
  const [state, setState] = useState({ loading: false, error: "" });

  const sortedReports = useMemo(
    () => [...reports].sort((a, b) => Number(b.id) - Number(a.id)),
    [reports]
  );

  const selectedReport =
    sortedReports.find((r) => String(r.id) === selectedReportId) ?? sortedReports[0] ?? null;

  async function loadReports() {
    setState({ loading: true, error: "" });
    try {
      const data = await listCompatibilityReports();
      setReports(data);
      const next = [...data].sort((a, b) => Number(b.id) - Number(a.id));
      setSelectedReportId(next.length > 0 ? String(next[0].id) : "");
      onReportsLoaded(data.length > 0);
    } catch (error) {
      setState({ loading: false, error: error.message });
      onReportsLoaded(false);
      return;
    }
    setState({ loading: false, error: "" });
  }

  return (
    <div className="page">
      <Panel
        title="Compatibility Check"
        description="Load reports to review the SemVer recommendation, risk level, and migration advice."
        actions={
          <button className="secondary" disabled={state.loading} onClick={loadReports}>
            {state.loading ? "Loading…" : "Load Reports"}
          </button>
        }
      >
        {state.error && (
          <div className="error-msg" role="alert">
            {state.error}
          </div>
        )}

        {!state.loading && !state.error && reports.length === 0 && (
          <p className="empty-state">No compatibility reports loaded yet. Click Load Reports.</p>
        )}

        {sortedReports.length > 0 && (
          <div className="report-browser">
            {/* ── List ─────────────────────────────────────── */}
            <aside className="report-list">
              <div className="row" style={{ marginTop: 0, marginBottom: 4 }}>
                <button
                  className="secondary"
                  onClick={() => setSelectedReportId(String(sortedReports[0].id))}
                >
                  Newest
                </button>
                <button
                  className="secondary"
                  onClick={() =>
                    setSelectedReportId(String(sortedReports[sortedReports.length - 1].id))
                  }
                >
                  Oldest
                </button>
              </div>

              {sortedReports.map((report) => (
                <button
                  key={report.id}
                  className={`report-item${String(report.id) === String(selectedReport?.id) ? " selected" : ""}`}
                  onClick={() => setSelectedReportId(String(report.id))}
                >
                  <div className="split">
                    <strong style={{ fontSize: "var(--text-sm)" }}>Report #{report.id}</strong>
                    <StatusBadge value={report.level} />
                  </div>
                  <span className="meta-label">SemVer: {report.semverRecommendation}</span>
                </button>
              ))}
            </aside>

            {/* ── Detail ───────────────────────────────────── */}
            {selectedReport && (
              <article className="card report-details">
                <div className="split" style={{ marginBottom: 12 }}>
                  <h3 style={{ fontSize: "var(--text-md)" }}>Report #{selectedReport.id}</h3>
                  <StatusBadge value={selectedReport.level} />
                </div>

                <div
                  style={{
                    display: "grid",
                    gridTemplateColumns: "1fr 1fr",
                    gap: 10,
                    marginBottom: 14,
                  }}
                >
                  <div className="stat-card">
                    <span className="stat-label">SemVer bump</span>
                    <span
                      className="stat-value"
                      style={{ fontSize: "var(--text-lg)", color: "var(--c-primary)" }}
                    >
                      {selectedReport.semverRecommendation}
                    </span>
                  </div>
                  <div className="stat-card">
                    <span className="stat-label">Risk level</span>
                    <span style={{ marginTop: 4 }}>
                      <StatusBadge value={selectedReport.level} />
                    </span>
                  </div>
                </div>

                {selectedReport.migrationAdvice && (
                  <p
                    style={{
                      fontSize: "var(--text-sm)",
                      color: "var(--c-text-2)",
                      marginBottom: 12,
                      padding: "10px 14px",
                      background: "var(--c-surface-2)",
                      borderRadius: "var(--r-md)",
                      border: "1px solid var(--c-border)",
                    }}
                  >
                    {selectedReport.migrationAdvice}
                  </p>
                )}

                <details open>
                  <summary>Findings</summary>
                  <pre>{selectedReport.findings}</pre>
                </details>
              </article>
            )}
          </div>
        )}

        <p className="helper-text">
          After reviewing the recommendation, proceed to <strong>Artifacts</strong> to copy integration values.
        </p>
      </Panel>
    </div>
  );
}
