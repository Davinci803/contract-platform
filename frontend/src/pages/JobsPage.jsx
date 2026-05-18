import { useMemo, useState } from "react";
import Panel from "../components/Panel";
import StatusBadge from "../components/StatusBadge";
import { createGenerationJob, getGenerationJob } from "../api";

const STAGES = ["PENDING", "RUNNING", "SUCCESS"];

const STAGE_LABEL = {
  PENDING: "Queued",
  RUNNING: "Processing",
  SUCCESS: "Completed"
};

export default function JobsPage({ selectedContractVersionId, currentJob, onJobUpdated }) {
  const [state, setState] = useState({ loading: false, error: "" });
  const [copyState, setCopyState] = useState("");
  const stageState = useMemo(() => deriveStageState(currentJob?.status), [currentJob?.status]);

  async function startJob() {
    if (!selectedContractVersionId) {
      setState({ loading: false, error: "Select a contract version first (step 1)." });
      return;
    }
    setState({ loading: true, error: "" });
    try {
      const job = await createGenerationJob(Number(selectedContractVersionId));
      onJobUpdated(job);
    } catch (error) {
      setState({ loading: false, error: error.message });
      return;
    }
    setState({ loading: false, error: "" });
  }

  async function refreshJob() {
    if (!currentJob?.jobId) {
      setState({ loading: false, error: "No job created yet." });
      return;
    }
    setState({ loading: true, error: "" });
    try {
      const refreshed = await getGenerationJob(currentJob.jobId);
      onJobUpdated(refreshed);
    } catch (error) {
      setState({ loading: false, error: error.message });
      return;
    }
    setState({ loading: false, error: "" });
  }

  async function copyCorrelationId() {
    if (!currentJob?.correlationId) return;
    try {
      await navigator.clipboard.writeText(currentJob.correlationId);
      setCopyState("Correlation ID copied — paste it in step 4 to filter by this job.");
    } catch {
      setCopyState("Copy failed. Check browser clipboard permissions.");
    }
  }

  return (
    <div className="page">
      {/* ── Controls ──────────────────────────────────────────── */}
      <Panel
        title="Generate and Publish"
        description="Run the pipeline for the selected version. Wait for SUCCESS before moving on."
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 12,
            padding: "12px 16px",
            background: "var(--c-surface-2)",
            borderRadius: "var(--r-lg)",
            border: "1px solid var(--c-border)",
            marginBottom: 16,
          }}
        >
          <span style={{ fontSize: "var(--text-sm)", color: "var(--c-text-2)" }}>
            Version ID:
          </span>
          <strong style={{ fontFamily: "var(--font-mono)", fontSize: "var(--text-sm)" }}>
            {selectedContractVersionId || "—"}
          </strong>
          {selectedContractVersionId && (
            <span
              style={{
                marginLeft: "auto",
                fontSize: "var(--text-xs)",
                color: "var(--c-success)",
                fontWeight: 600,
              }}
            >
              ✓ ready
            </span>
          )}
        </div>

        <div className="row" style={{ marginTop: 0 }}>
          <button
            disabled={state.loading || !selectedContractVersionId}
            onClick={startJob}
          >
            {state.loading ? "Submitting…" : "Generate and publish"}
          </button>
          <button
            className="secondary"
            disabled={state.loading || !currentJob?.jobId}
            onClick={refreshJob}
          >
            Refresh status
          </button>
        </div>

        {state.error && (
          <div className="error-msg" role="alert" style={{ marginTop: 12 }}>
            {state.error}
          </div>
        )}

        <p className="helper-text">
          When status reaches <strong>Success</strong>, proceed to step 3.
        </p>
      </Panel>

      {/* ── Job status ────────────────────────────────────────── */}
      <Panel title="Job Status" description="Pipeline execution state for this generation request.">
        {!currentJob && (
          <p className="empty-state">No job created yet. Click Generate above.</p>
        )}

        {currentJob && (
          <>
            {/* Job meta */}
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))",
                gap: 10,
                marginBottom: 16,
              }}
            >
              <div className="stat-card">
                <span className="stat-label">Job ID</span>
                <span className="stat-value" style={{ fontSize: "var(--text-md)", fontFamily: "var(--font-mono)" }}>
                  {currentJob.jobId}
                </span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Status</span>
                <span style={{ marginTop: 4 }}>
                  <StatusBadge value={currentJob.status} />
                </span>
              </div>
            </div>

            {/* Correlation ID */}
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: 10,
                padding: "10px 14px",
                background: "var(--c-surface-2)",
                border: "1px solid var(--c-border)",
                borderRadius: "var(--r-md)",
                marginBottom: 16,
              }}
            >
              <span style={{ fontSize: "var(--text-xs)", color: "var(--c-text-3)", minWidth: 90 }}>
                Correlation ID
              </span>
              <span
                className="mono"
                style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}
              >
                {currentJob.correlationId || "—"}
              </span>
              <button
                className="secondary"
                disabled={!currentJob.correlationId}
                onClick={copyCorrelationId}
                style={{ flexShrink: 0 }}
              >
                Copy
              </button>
            </div>

            {copyState && (
              <p className="helper-text" style={{ color: "var(--c-success)", marginBottom: 12 }}>
                {copyState}
              </p>
            )}

            {/* Timeline */}
            <div className="timeline">
              {STAGES.map((stage) => {
                const isDone = stageState.completed.includes(stage);
                const isActive = stageState.current === stage;
                return (
                  <div
                    key={stage}
                    className={`timeline-step${isDone ? " done" : ""}${isActive && !isDone ? " active" : ""}`}
                  >
                    {STAGE_LABEL[stage] ?? stage}
                    {isDone && (
                      <span style={{ marginLeft: "auto", fontSize: "var(--text-xs)" }}>✓</span>
                    )}
                    {isActive && !isDone && (
                      <span style={{ marginLeft: "auto", fontSize: "var(--text-xs)" }}>●</span>
                    )}
                  </div>
                );
              })}
              {stageState.failed && (
                <div className="timeline-step failed">
                  Failed
                  <span style={{ marginLeft: "auto", fontSize: "var(--text-xs)" }}>✕</span>
                </div>
              )}
            </div>
          </>
        )}
      </Panel>

      {/* ── Log ───────────────────────────────────────────────── */}
      {(currentJob?.log) && (
        <Panel title="Execution Log" description="Raw backend log for debugging failures and retries.">
          <pre>{currentJob.log}</pre>
        </Panel>
      )}
    </div>
  );
}

function deriveStageState(status) {
  if (!status) return { completed: [], current: null, failed: false };
  if (status === "PENDING") return { completed: [], current: "PENDING", failed: false };
  if (status === "RUNNING") return { completed: ["PENDING"], current: "RUNNING", failed: false };
  if (status === "SUCCESS") return { completed: ["PENDING", "RUNNING", "SUCCESS"], current: "SUCCESS", failed: false };
  return { completed: ["PENDING", "RUNNING"], current: null, failed: true };
}
