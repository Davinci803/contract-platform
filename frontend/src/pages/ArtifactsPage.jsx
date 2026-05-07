import { useState } from "react";
import Panel from "../components/Panel";
import StatusBadge from "../components/StatusBadge";
import { getReadModelSummary, listArtifacts, listPublicationLogs } from "../api";

export default function ArtifactsPage() {
  const [summary, setSummary] = useState(null);
  const [artifacts, setArtifacts] = useState([]);
  const [logs, setLogs] = useState([]);
  const [state, setState] = useState({ loading: false, error: "" });

  async function loadReadModel() {
    setState({ loading: true, error: "" });
    try {
      const [summaryData, artifactsData, logsData] = await Promise.all([
        getReadModelSummary(),
        listArtifacts(),
        listPublicationLogs()
      ]);
      setSummary(summaryData);
      setArtifacts(artifactsData);
      setLogs(logsData);
    } catch (error) {
      setState({ loading: false, error: error.message });
      return;
    }
    setState({ loading: false, error: "" });
  }

  async function copy(text) {
    if (!text) {
      return;
    }
    await navigator.clipboard.writeText(text);
  }

  return (
    <div className="page">
      <Panel
        title="Artifacts & Publications"
        description="Read model for generated coordinates and publication events."
        actions={
          <button className="secondary" disabled={state.loading} onClick={loadReadModel}>
            {state.loading ? "Loading..." : "Load Read Model"}
          </button>
        }
      >
        {state.error && <p className="error">{state.error}</p>}
        {!state.loading && !state.error && !summary && (
          <p className="muted">Load read model to see artifact and publication data.</p>
        )}
        {summary && (
          <div className="stats">
            <article className="card">
              <strong>Artifacts</strong>
              <p>{summary.artifacts}</p>
            </article>
            <article className="card">
              <strong>Publication Logs</strong>
              <p>{summary.publicationLogs}</p>
            </article>
          </div>
        )}
      </Panel>

      <Panel title="Generated Artifacts" description="Copy Maven coordinates and publication URLs.">
        {!state.loading && artifacts.length === 0 && <p className="muted">No artifacts found.</p>}
        {artifacts.length > 0 && (
          <div className="cards">
            {artifacts.map((artifact) => (
              <article className="card" key={artifact.id}>
                <strong>Artifact #{artifact.id}</strong>
                <p>Job: {artifact.jobId}</p>
                <p className="mono">{artifact.coordinates}</p>
                <p className="mono">{artifact.publicationUrl}</p>
                {artifact.schemaSubject && <p className="mono">Schema: {artifact.schemaSubject}</p>}
                <div className="row">
                  <button className="secondary" onClick={() => copy(artifact.coordinates)}>
                    Copy coordinates
                  </button>
                  <button className="secondary" onClick={() => copy(artifact.publicationUrl)}>
                    Copy publication URL
                  </button>
                </div>
              </article>
            ))}
          </div>
        )}
      </Panel>

      <Panel title="Publication Events" description="Recent publication and compatibility events.">
        {!state.loading && logs.length === 0 && <p className="muted">No publication events found.</p>}
        {logs.length > 0 && (
          <div className="cards">
            {logs.map((log) => (
              <article className="card" key={log.id}>
                <div className="split">
                  <strong>{log.target}</strong>
                  <StatusBadge value={log.status} />
                </div>
                <p className="muted">Job: {log.jobId}</p>
                <p>{log.message}</p>
                <small>
                  event={log.eventType} | category={log.errorCategory} | correlationId={log.correlationId}
                </small>
              </article>
            ))}
          </div>
        )}
      </Panel>
    </div>
  );
}
