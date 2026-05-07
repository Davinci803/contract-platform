import { useMemo, useState } from "react";
import Panel from "../components/Panel";
import StatusBadge from "../components/StatusBadge";
import { createGenerationJob, getGenerationJob } from "../api";

const STAGES = ["PENDING", "RUNNING", "SUCCESS"];

export default function JobsPage({ selectedContractVersionId, currentJob, onJobUpdated }) {
  const [state, setState] = useState({ loading: false, error: "" });
  const stageState = useMemo(() => deriveStageState(currentJob?.status), [currentJob?.status]);

  async function startJob() {
    if (!selectedContractVersionId) {
      setState({ loading: false, error: "Select contract version first in Contracts page." });
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
      setState({ loading: false, error: "No job selected yet." });
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

  return (
    <div className="page">
      <Panel
        title="Generation Job"
        description="Run pipeline for selected contract version and track status."
      >
        <div className="row">
          <p>
            Selected version ID: <strong>{selectedContractVersionId || "-"}</strong>
          </p>
          <button disabled={state.loading || !selectedContractVersionId} onClick={startJob}>
            {state.loading ? "Submitting..." : "Generate & Publish"}
          </button>
          <button
            className="secondary"
            disabled={state.loading || !currentJob?.jobId}
            onClick={refreshJob}
          >
            Refresh Job
          </button>
        </div>
        {state.error && <p className="error">{state.error}</p>}
      </Panel>

      <Panel title="Job Status" description="Pipeline stages visualization.">
        {!currentJob && <p className="muted">No job created yet.</p>}
        {currentJob && (
          <>
            <p>
              Job ID: <strong>{currentJob.jobId}</strong>
            </p>
            <p>
              Status: <StatusBadge value={currentJob.status} />
            </p>
            <div className="timeline">
              {STAGES.map((stage) => (
                <div
                  key={stage}
                  className={`timeline-step ${stageState.completed.includes(stage) ? "done" : ""} ${
                    stageState.current === stage ? "active" : ""
                  }`}
                >
                  {stage}
                </div>
              ))}
              {stageState.failed && <div className="timeline-step failed">FAILED</div>}
            </div>
          </>
        )}
      </Panel>

      <Panel title="Job Log" description="Latest execution details from backend.">
        {!currentJob?.log && <p className="muted">No log available yet.</p>}
        {currentJob?.log && <pre>{currentJob.log}</pre>}
      </Panel>
    </div>
  );
}

function deriveStageState(status) {
  if (!status) {
    return { completed: [], current: null, failed: false };
  }
  if (status === "PENDING") {
    return { completed: [], current: "PENDING", failed: false };
  }
  if (status === "RUNNING") {
    return { completed: ["PENDING"], current: "RUNNING", failed: false };
  }
  if (status === "SUCCESS") {
    return { completed: ["PENDING", "RUNNING", "SUCCESS"], current: "SUCCESS", failed: false };
  }
  return { completed: ["PENDING", "RUNNING"], current: null, failed: true };
}
