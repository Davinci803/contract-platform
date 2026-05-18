import { useMemo, useState } from "react";
import Panel from "../components/Panel";
import StatusBadge from "../components/StatusBadge";
import { listCompatibilityReports } from "../api";

export default function CompatibilityPage({ onReportsLoaded = () => {} }) {
  const [reports, setReports] = useState([]);
  const [selectedReportId, setSelectedReportId] = useState("");
  const [state, setState] = useState({ loading: false, error: "" });
  const sortedReports = useMemo(
    () => [...reports].sort((left, right) => Number(right.id) - Number(left.id)),
    [reports]
  );
  const selectedReport =
    sortedReports.find((report) => String(report.id) === selectedReportId) ?? sortedReports[0] ?? null;

  async function loadReports() {
    setState({ loading: true, error: "" });
    try {
      const data = await listCompatibilityReports();
      setReports(data);
      const nextSorted = [...data].sort((left, right) => Number(right.id) - Number(left.id));
      setSelectedReportId(nextSorted.length > 0 ? String(nextSorted[0].id) : "");
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
        title="Step 3: Compatibility Check"
        description="Load report to see SemVer recommendation, risk level, and migration advice."
        actions={
          <button className="secondary" disabled={state.loading} onClick={loadReports}>
            {state.loading ? "Loading..." : "Load Reports"}
          </button>
        }
      >
        {state.error && <p className="error">{state.error}</p>}
        {!state.loading && !state.error && reports.length === 0 && (
          <p className="muted">No compatibility reports loaded yet.</p>
        )}
        <p className="muted helper-text">
          After reviewing recommendation, continue to step 4 and copy artifact values.
        </p>
        {sortedReports.length > 0 && (
          <div className="report-browser">
            <aside className="report-list">
              <div className="row">
                <button className="secondary" onClick={() => setSelectedReportId(String(sortedReports[0].id))}>
                  Newest
                </button>
                <button
                  className="secondary"
                  onClick={() => setSelectedReportId(String(sortedReports[sortedReports.length - 1].id))}
                >
                  Oldest
                </button>
              </div>
              {sortedReports.map((report) => (
                <button
                  key={report.id}
                  className={`report-item ${String(report.id) === String(selectedReport?.id) ? "selected" : ""}`}
                  onClick={() => setSelectedReportId(String(report.id))}
                >
                  <div className="split">
                    <strong>Report #{report.id}</strong>
                    <StatusBadge value={report.level} />
                  </div>
                  <small>SemVer: {report.semverRecommendation}</small>
                </button>
              ))}
            </aside>
            {selectedReport && (
              <article className="card report-details">
                <div className="split">
                  <strong>Report #{selectedReport.id}</strong>
                  <StatusBadge value={selectedReport.level} />
                </div>
                <p>
                  Recommended bump: <strong>{selectedReport.semverRecommendation}</strong>
                </p>
                <p className="muted">{selectedReport.migrationAdvice}</p>
                <details open>
                  <summary>Findings</summary>
                  <pre>{selectedReport.findings}</pre>
                </details>
              </article>
            )}
          </div>
        )}
      </Panel>
    </div>
  );
}
