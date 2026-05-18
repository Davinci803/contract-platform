import { useEffect, useMemo, useState } from "react";
import Panel from "../components/Panel";
import StatusBadge from "../components/StatusBadge";
import { getReadModelSummary, listArtifacts, listPublicationLogs } from "../api";

const CopyIcon = () => (
  <svg width="13" height="13" viewBox="0 0 13 13" fill="none" aria-hidden="true">
    <rect x="4.5" y="0.5" width="8" height="8" rx="1.5" stroke="currentColor" />
    <path d="M3.5 3.5H1.5a1 1 0 00-1 1V11a1 1 0 001 1h6.5a1 1 0 001-1V9.5" stroke="currentColor" />
  </svg>
);

function CopyButton({ label, value, onCopy }) {
  const [copied, setCopied] = useState(false);
  async function handleClick() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      onCopy(label);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      onCopy("— Copy failed. Check browser permissions.");
    }
  }
  return (
    <button
      className="secondary"
      onClick={handleClick}
      disabled={!value}
      style={{ fontSize: "var(--text-xs)", height: 30, padding: "0 10px" }}
    >
      <CopyIcon />
      {copied ? "Copied!" : label}
    </button>
  );
}

export default function ArtifactsPage({ onReadModelLoaded = () => {}, currentJob = null }) {
  const [summary, setSummary] = useState(null);
  const [artifacts, setArtifacts] = useState([]);
  const [logs, setLogs] = useState([]);
  const [state, setState] = useState({ loading: false, error: "" });
  const [copiedMessage, setCopiedMessage] = useState("");
  const [activeSection, setActiveSection] = useState("events");
  const [traceMode, setTraceMode] = useState("latest");

  const sortedArtifacts = useMemo(
    () => [...artifacts].sort((a, b) => Number(b.id) - Number(a.id)),
    [artifacts]
  );
  const sortedLogs = useMemo(
    () => [...logs].sort((a, b) => Number(b.id) - Number(a.id)),
    [logs]
  );

  async function loadReadModel() {
    setState({ loading: true, error: "" });
    const correlationId =
      traceMode === "current-job" ? currentJob?.correlationId ?? "" : "";
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

  useEffect(() => {
    loadReadModel();
  }, []);

  function handleCopy(label) {
    setCopiedMessage(`${label} copied — paste it into your integration settings.`);
  }

  return (
    <div className="page">
      {/* ── Controls panel ────────────────────────────────────── */}
      <Panel
        title="Artifacts and Publication Events"
        description="Load the read-model, then copy values needed by external services."
      >
        {state.error && (
          <div className="error-msg" role="alert">
            {state.error}
          </div>
        )}

        {!state.loading && !state.error && !summary && (
          <p className="empty-state">Read model is empty. Upload and publish a contract version first.</p>
        )}

        {copiedMessage && (
          <div className="success-msg" role="status">
            {copiedMessage}
          </div>
        )}

        {/* Stats */}
        {summary && (
          <div className="stats">
            <div className="stat-card">
              <span className="stat-label">Artifacts</span>
              <span className="stat-value">{summary.artifacts}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Publication Logs</span>
              <span className="stat-value">{summary.publicationLogs}</span>
            </div>
          </div>
        )}

        {/* Trace mode switch */}
        {summary && (
          <>
            <div className="section-switch" style={{ marginTop: 16 }}>
              <button
                className={traceMode === "latest" ? "active-tab" : ""}
                onClick={() => setTraceMode("latest")}
              >
                Latest events
              </button>
              <button
                className={traceMode === "current-job" ? "active-tab" : ""}
                disabled={!currentJob?.correlationId}
                onClick={() => setTraceMode("current-job")}
              >
                Trace current job
              </button>
            </div>

            <p className="helper-text">
              Filter:{" "}
              {traceMode === "current-job" && currentJob?.correlationId
                ? <code style={{ fontFamily: "var(--font-mono)", fontSize: "var(--text-xs)" }}>correlationId={currentJob.correlationId}</code>
                : "last records (no correlation filter)"}
            </p>
          </>
        )}

        {/* View switch */}
        {summary && (
          <div className="section-switch">
            <button
              className={activeSection === "events" ? "active-tab" : ""}
              onClick={() => setActiveSection("events")}
            >
              Publication Events
            </button>
            <button
              className={activeSection === "artifacts" ? "active-tab" : ""}
              onClick={() => setActiveSection("artifacts")}
            >
              Generated Artifacts
            </button>
          </div>
        )}
      </Panel>

      {/* ── Publication Events ────────────────────────────────── */}
      {activeSection === "events" && summary && (
        <Panel
          title="Publication Events"
          description="Copy a correlationId to trace request → job → publication logs."
        >
          {sortedLogs.length === 0 && (
            <p className="empty-state">No publication events found.</p>
          )}
          {sortedLogs.length > 0 && (
            <div className="cards compact-cards">
              {sortedLogs.map((log) => (
                <article className="card" key={log.id}>
                  <div className="split">
                    <strong style={{ fontSize: "var(--text-base)" }}>{log.target}</strong>
                    <StatusBadge value={log.status} />
                  </div>

                  <p style={{ fontSize: "var(--text-sm)", color: "var(--c-text-2)", margin: "4px 0" }}>
                    {log.message}
                  </p>

                  <div className="meta-row">
                    <span className="meta-label">Job: {log.jobId}</span>
                    <span className="meta-label">Event: {log.eventType}</span>
                    {log.errorCategory && (
                      <span className="meta-label">Category: {log.errorCategory}</span>
                    )}
                  </div>

                  {log.correlationId && (
                    <div
                      style={{
                        margin: "8px 0 4px",
                        padding: "6px 10px",
                        background: "var(--c-surface-2)",
                        borderRadius: "var(--r-md)",
                        border: "1px solid var(--c-border)",
                      }}
                    >
                      <span className="mono">{log.correlationId}</span>
                    </div>
                  )}

                  {log.correlationId && (
                    <div className="row" style={{ marginTop: 4 }}>
                      <CopyButton label="Copy correlation ID" value={log.correlationId} onCopy={handleCopy} />
                    </div>
                  )}
                </article>
              ))}
            </div>
          )}
        </Panel>
      )}

      {/* ── Generated Artifacts ───────────────────────────────── */}
      {activeSection === "artifacts" && summary && (
        <Panel
          title="Generated Artifacts"
          description="Copy coordinates, publication URL, and schema subject for external service setup."
        >
          {sortedArtifacts.length === 0 && (
            <p className="empty-state">No artifacts found.</p>
          )}
          {sortedArtifacts.length > 0 && (
            <div className="cards compact-cards">
              {sortedArtifacts.map((artifact) => (
                <article className="card" key={artifact.id}>
                  <div className="split" style={{ marginBottom: 4 }}>
                    <strong style={{ fontSize: "var(--text-base)" }}>
                      Artifact #{artifact.id}
                    </strong>
                    <span className="meta-label">Job {artifact.jobId}</span>
                  </div>

                  <div className="divider" style={{ margin: "8px 0" }} />

                  {artifact.coordinates && (
                    <div style={{ marginBottom: 6 }}>
                      <span className="stat-label">Coordinates</span>
                      <div className="mono" style={{ marginTop: 3 }}>{artifact.coordinates}</div>
                    </div>
                  )}

                  {artifact.publicationUrl && (
                    <div style={{ marginBottom: 6 }}>
                      <span className="stat-label">Publication URL</span>
                      <div className="mono" style={{ marginTop: 3 }}>{artifact.publicationUrl}</div>
                    </div>
                  )}

                  {artifact.schemaSubject && (
                    <div style={{ marginBottom: 6 }}>
                      <span className="stat-label">Schema Subject</span>
                      <div className="mono" style={{ marginTop: 3 }}>{artifact.schemaSubject}</div>
                    </div>
                  )}

                  <div className="divider" style={{ margin: "8px 0" }} />

                  <div className="row" style={{ marginTop: 0, gap: 6, flexWrap: "wrap" }}>
                    <CopyButton label="Coordinates" value={artifact.coordinates} onCopy={handleCopy} />
                    <CopyButton label="URL" value={artifact.publicationUrl} onCopy={handleCopy} />
                    {artifact.schemaSubject && (
                      <CopyButton label="Schema subject" value={artifact.schemaSubject} onCopy={handleCopy} />
                    )}
                  </div>

                  <p style={{ fontSize: "var(--text-xs)", color: "var(--c-text-3)", marginTop: 8 }}>
                    coordinates → dependency config · URL → release evidence · schema subject → registry consumer
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
