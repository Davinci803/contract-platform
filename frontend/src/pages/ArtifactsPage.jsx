import { useMemo, useState } from "react";
import Panel from "../components/Panel";
import StatusBadge from "../components/StatusBadge";
import { getReadModelSummary, listArtifacts, listPublicationLogs } from "../api";

export default function ArtifactsPage({ onReadModelLoaded = () => {}, currentJob = null }) {
  const [summary, setSummary] = useState(null);
  const [artifacts, setArtifacts] = useState([]);
  const [logs, setLogs] = useState([]);
  const [state, setState] = useState({ loading: false, error: "" });
  const [copiedMessage, setCopiedMessage] = useState("");
  const [activeSection, setActiveSection] = useState("events");
  const [traceMode, setTraceMode] = useState("latest");
  const sortedArtifacts = useMemo(
    () => [...artifacts].sort((left, right) => Number(right.id) - Number(left.id)),
    [artifacts]
  );
  const sortedLogs = useMemo(
    () => [...logs].sort((left, right) => Number(right.id) - Number(left.id)),
    [logs]
  );

  async function loadReadModel() {
    setState({ loading: true, error: "" });
    const correlationId = traceMode === "current-job" ? currentJob?.correlationId ?? "" : "";
    try {
      const [summaryData, artifactsData, logsData] = await Promise.all([
        getReadModelSummary(),
        listArtifacts(20, correlationId),
        listPublicationLogs(30, correlationId)
      ]);
      setSummary(summaryData);
      setArtifacts(artifactsData);
      setLogs(logsData);
      onReadModelLoaded(Boolean(summaryData));
    } catch (error) {
      setState({ loading: false, error: error.message });
      onReadModelLoaded(false);
      return;
    }
    setState({ loading: false, error: "" });
  }

  async function copy(text, hintLabel) {
    if (!text) {
      return;
    }
    try {
      await navigator.clipboard.writeText(text);
      setCopiedMessage(`Copied ${hintLabel}. Paste it into external integration settings.`);
    } catch {
      setCopiedMessage("Copy failed. Check browser clipboard permissions.");
    }
  }

  return (
    <div className="page">
      <Panel
        title="Step 4: Artifacts and Publication Events"
        description="Load read-model, then copy values needed by external services."
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
        {copiedMessage && <p className="copy-success">{copiedMessage}</p>}
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
        {summary && (
          <div className="section-switch">
            <button
              className={traceMode === "latest" ? "" : "secondary"}
              onClick={() => setTraceMode("latest")}
            >
              Latest events
            </button>
            <button
              className={traceMode === "current-job" ? "" : "secondary"}
              disabled={!currentJob?.correlationId}
              onClick={() => setTraceMode("current-job")}
            >
              Trace current job by correlationId
            </button>
          </div>
        )}
        {summary && (
          <p className="muted helper-text">
            Active filter:{" "}
            {traceMode === "current-job" && currentJob?.correlationId
              ? `correlationId=${currentJob.correlationId}`
              : "last records (no correlation filter)"}
          </p>
        )}
        {summary && (
          <div className="section-switch">
            <button
              className={activeSection === "events" ? "" : "secondary"}
              onClick={() => setActiveSection("events")}
            >
              Publication Events
            </button>
            <button
              className={activeSection === "artifacts" ? "" : "secondary"}
              onClick={() => setActiveSection("artifacts")}
            >
              Generated Artifacts
            </button>
          </div>
        )}
      </Panel>

      {activeSection === "events" && (
        <Panel
          title="Publication Events"
          description="Copy correlationId to trace request -> job -> publication logs."
        >
          {!state.loading && sortedLogs.length === 0 && <p className="muted">No publication events found.</p>}
          {sortedLogs.length > 0 && (
            <div className="cards compact-cards">
              {sortedLogs.map((log) => (
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
                  {log.correlationId && (
                    <div className="row">
                      <button
                        className="secondary"
                        onClick={() => copy(log.correlationId, "correlation ID")}
                      >
                        Copy correlation ID
                      </button>
                    </div>
                  )}
                </article>
              ))}
            </div>
          )}
        </Panel>
      )}

      {activeSection === "artifacts" && (
        <Panel
          title="Generated Artifacts"
          description="Copy coordinates, publication URL, and schema subject for external service setup."
        >
          {!state.loading && sortedArtifacts.length === 0 && <p className="muted">No artifacts found.</p>}
          {sortedArtifacts.length > 0 && (
            <div className="cards compact-cards">
              {sortedArtifacts.map((artifact) => (
                <article className="card" key={artifact.id}>
                  <strong>Artifact #{artifact.id}</strong>
                  <p>Job: {artifact.jobId}</p>
                  <p className="mono">{artifact.coordinates}</p>
                  <p className="mono">{artifact.publicationUrl}</p>
                  {artifact.schemaSubject && <p className="mono">Schema: {artifact.schemaSubject}</p>}
                  <div className="row">
                    <button
                      className="secondary"
                      onClick={() => copy(artifact.coordinates, "coordinates")}
                    >
                      Copy coordinates
                    </button>
                    <button
                      className="secondary"
                      onClick={() => copy(artifact.publicationUrl, "publication URL")}
                    >
                      Copy publication URL
                    </button>
                    {artifact.schemaSubject && (
                      <button
                        className="secondary"
                        onClick={() => copy(artifact.schemaSubject, "schema subject")}
                      >
                        Copy schema subject
                      </button>
                    )}
                  </div>
                  <p className="muted helper-text">
                    coordinates -> dependency config, publication URL -> release evidence, schema
                    subject -> registry consumer config.
                  </p>
                </article>
              ))}
            </div>
          )}
        </Panel>
      )}
    </div>
  );
}
