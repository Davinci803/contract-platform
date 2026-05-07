import { useState } from "react";
import Panel from "../components/Panel";
import StatusBadge from "../components/StatusBadge";
import { listCompatibilityReports } from "../api";

export default function CompatibilityPage() {
  const [reports, setReports] = useState([]);
  const [state, setState] = useState({ loading: false, error: "" });

  async function loadReports() {
    setState({ loading: true, error: "" });
    try {
      const data = await listCompatibilityReports();
      setReports(data);
    } catch (error) {
      setState({ loading: false, error: error.message });
      return;
    }
    setState({ loading: false, error: "" });
  }

  return (
    <div className="page">
      <Panel
        title="Compatibility Reports"
        description="SemVer recommendation and migration advice."
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
        {reports.length > 0 && (
          <div className="cards">
            {reports.map((report) => (
              <article key={report.id} className="card">
                <div className="split">
                  <strong>Report #{report.id}</strong>
                  <StatusBadge value={report.level} />
                </div>
                <p>
                  Recommended bump: <strong>{report.semverRecommendation}</strong>
                </p>
                <p className="muted">{report.migrationAdvice}</p>
                <details>
                  <summary>Findings</summary>
                  <pre>{report.findings}</pre>
                </details>
              </article>
            ))}
          </div>
        )}
      </Panel>
    </div>
  );
}
