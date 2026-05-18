import { useEffect, useState } from "react";
import Panel from "../components/Panel";
import { createGenerationJob, getContractHistory, getGenerationJob, getLatestCompatibilityReport } from "../api";

const STATUS_CONFIG = {
  PENDING: {
    icon: "◌",
    label: "Queued",
    desc: "Job is waiting to be picked up by the worker.",
    bg: "var(--c-surface-2)",
    border: "var(--c-border)",
    color: "var(--c-text-2)",
    iconColor: "var(--c-text-3)",
  },
  RUNNING: {
    icon: "◎",
    label: "Processing",
    desc: "Pipeline is running — refresh in a moment.",
    bg: "var(--c-info-bg)",
    border: "var(--c-info-border)",
    color: "var(--c-info)",
    iconColor: "var(--c-info)",
  },
  SUCCESS: {
    icon: "✓",
    label: "Success",
    desc: "Pipeline completed. Proceed to Compatibility check.",
    bg: "var(--c-success-bg)",
    border: "var(--c-success-border)",
    color: "var(--c-success)",
    iconColor: "var(--c-success)",
  },
  FAILED: {
    icon: "✕",
    label: "Failed",
    desc: "Pipeline failed. Check the execution log below.",
    bg: "var(--c-error-bg)",
    border: "var(--c-error-border)",
    color: "var(--c-error)",
    iconColor: "var(--c-error)",
  },
};

export default function JobsPage({ selectedContractId, selectedContractVersionId, currentJob, onJobUpdated }) {
  const [state, setState] = useState({ loading: false, error: "" });
  const [copyState, setCopyState] = useState("");
  const [notice, setNotice] = useState("");
  const [selectedVersionMeta, setSelectedVersionMeta] = useState(null);

  useEffect(() => {
    if (!selectedContractId || !selectedContractVersionId) {
      setSelectedVersionMeta(null);
      return;
    }

    let cancelled = false;
    getContractHistory(selectedContractId)
      .then((history) => {
        if (cancelled) return;
        const selected = history.find(
          (item) => String(item.contractVersionId) === String(selectedContractVersionId)
        );
        if (!selected) {
          setSelectedVersionMeta(null);
          return;
        }
        setSelectedVersionMeta({
          contractName: selected.contractName,
          contractType: selected.contractType,
          version: selected.version,
          contractVersionId: selected.contractVersionId,
        });
      })
      .catch(() => {
        if (!cancelled) {
          setSelectedVersionMeta(null);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [selectedContractId, selectedContractVersionId]);

  useEffect(() => {
    if (!currentJob?.jobId) {
      return;
    }
    if (currentJob.status !== "PENDING" && currentJob.status !== "RUNNING") {
      return;
    }

    const timerId = window.setTimeout(async () => {
      try {
        const refreshed = await getGenerationJob(currentJob.jobId);
        onJobUpdated(refreshed);
      } catch {
        // Keep silent here; manual refresh keeps explicit error handling.
      }
    }, 1500);

    return () => window.clearTimeout(timerId);
  }, [currentJob, onJobUpdated]);

  async function startJob() {
    if (!selectedContractVersionId) {
      setState({ loading: false, error: "Select a contract version first (step 1)." });
      return;
    }
    setState({ loading: true, error: "" });
    setCopyState("");
    setNotice("");
    try {
      const report = await getLatestCompatibilityReport(Number(selectedContractVersionId));
      let publishInNewMajorSubject = false;
      if (report?.level === "BREAKING") {
        const confirmed = window.confirm(
          `Compatibility report #${report.id} is BREAKING (recommended bump: ${report.semverRecommendation}).\n\n` +
            `Continue and publish this incompatible version?`
        );
        if (!confirmed) {
          setState({ loading: false, error: "" });
          setNotice(
            `Publication cancelled by user based on compatibility report #${report.id}.`
          );
          return;
        }
        const isAsyncApiBreaking = String(report.findings ?? "").toUpperCase().includes("ASYNCAPI_");
        if (isAsyncApiBreaking) {
          publishInNewMajorSubject = true;
          setNotice(
            "AsyncAPI breaking change accepted. Platform will publish to a new major subject version (v2/v3/...)."
          );
        }
      }
      const job = await createGenerationJob(Number(selectedContractVersionId), publishInNewMajorSubject);
      onJobUpdated(job);
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

  const statusKey = currentJob?.status ?? "";
  const cfg = STATUS_CONFIG[statusKey] ?? null;
  const generateButtonTypeClass =
    selectedVersionMeta?.contractType === "ASYNCAPI" ? "asyncapi" : "openapi";

  return (
    <div className="page">
      <div className="jobs-top-grid">
        {/* ── Controls ──────────────────────────────────────────── */}
        <Panel
          title="Generate and Publish"
          description="Run the pipeline for the selected version. Wait for Success before moving on."
        >
        <div className="version-summary">
          <div className="version-summary-top">
            <span className={`contract-type-chip contract-type-chip-${String(selectedVersionMeta?.contractType || "").toLowerCase()}`}>
              {selectedVersionMeta?.contractType === "ASYNCAPI" ? "AsyncAPI" : selectedVersionMeta?.contractType === "OPENAPI" ? "OpenAPI" : "Type not selected"}
            </span>
          </div>
          <div className="version-summary-line">
            <span className="version-summary-label">Contract:</span>
            <strong className="version-summary-value">
              {selectedVersionMeta?.contractName || "Not selected"}
            </strong>
          </div>
          <div className="version-summary-line">
            <span className="version-summary-label">Version:</span>
            <strong className="version-summary-value">
              {selectedVersionMeta?.version ? `v${selectedVersionMeta.version}` : "—"}
            </strong>
          </div>
          <div className="version-summary-line">
            <span className="version-summary-label">Version ID:</span>
            <span className="version-summary-id">
              {selectedVersionMeta?.contractVersionId || selectedContractVersionId || "not selected"}
            </span>
          </div>
        </div>

          <div className="row" style={{ marginTop: 0 }}>
            <button
              className={`generate-action-btn ${generateButtonTypeClass}`}
              disabled={state.loading || !selectedContractVersionId}
              onClick={startJob}
            >
              {state.loading ? "Submitting…" : "Generate and publish"}
            </button>
          </div>

        {state.error && (
          <div className="error-msg" role="alert" style={{ marginTop: 12 }}>
            {state.error}
          </div>
        )}
        {notice && (
          <div className="success-msg" role="status" style={{ marginTop: 12 }}>
            {notice}
          </div>
        )}

          <p className="helper-text">
            Status updates automatically every ~1.5s while job is in <strong>PENDING/RUNNING</strong>.
            When it reaches <strong>Success</strong>, proceed to step 3.
          </p>
        </Panel>

        {/* ── Job Status card ───────────────────────────────────── */}
        <Panel title="Job Status">
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              gap: 16,
            }}
          >
            {!currentJob || !cfg ? (
              <div
                style={{
                  padding: "12px 14px",
                  background: "var(--c-surface-2)",
                  border: "1px dashed var(--c-border)",
                  borderRadius: "var(--r-md)",
                  color: "var(--c-text-2)",
                  fontSize: "var(--text-sm)",
                }}
              >
                Job is not started yet. Click <strong>Generate and publish</strong>.
              </div>
            ) : (
              <>
            {/* Job ID — above the status block */}
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <span style={{ fontSize: "var(--text-xs)", color: "var(--c-text-3)" }}>Job ID</span>
              <span
                style={{
                  fontFamily: "var(--font-mono)",
                  fontSize: "var(--text-sm)",
                  fontWeight: 600,
                  color: "var(--c-text)",
                  padding: "2px 10px",
                  background: "var(--c-surface-2)",
                  border: "1px solid var(--c-border)",
                  borderRadius: "var(--r-full)",
                }}
              >
                {currentJob.jobId}
              </span>
            </div>

            {/* Status hero */}
            <div
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: 16,
                padding: "14px 20px",
                background: cfg.bg,
                border: `1px solid ${cfg.border}`,
                borderRadius: "var(--r-lg)",
                maxWidth: 420,
              }}
            >
              {/* Icon circle */}
              <span
                style={{
                  width: 44,
                  height: 44,
                  borderRadius: "50%",
                  border: `2px solid ${cfg.iconColor}`,
                  color: cfg.iconColor,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  fontSize: 20,
                  fontWeight: 700,
                  flexShrink: 0,
                  background: "rgba(255,255,255,0.6)",
                }}
                aria-hidden="true"
              >
                {cfg.icon}
              </span>

              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: "var(--text-lg)", fontWeight: 700, color: cfg.color }}>
                  {cfg.label}
                </div>
                <div style={{ fontSize: "var(--text-sm)", color: "var(--c-text-2)", marginTop: 2 }}>
                  {cfg.desc}
                </div>
              </div>
            </div>

              </>
            )}
          </div>
        </Panel>
      </div>

      {/* ── Log ───────────────────────────────────────────────── */}
      {currentJob?.log && (
        <Panel title="Execution Log" description="Raw backend log for debugging failures and retries.">
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 10,
          padding: "10px 14px",
          marginBottom: 10,
          background: "var(--c-surface-2)",
          border: "1px solid var(--c-border)",
          borderRadius: "var(--r-md)",
        }}
      >
        <span style={{ fontSize: "var(--text-xs)", color: "var(--c-text-3)", flexShrink: 0 }}>
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
          style={{ flexShrink: 0, height: 30, padding: "0 12px", fontSize: "var(--text-xs)" }}
        >
          Copy
        </button>
      </div>
      {copyState && (
        <p style={{ fontSize: "var(--text-sm)", color: "var(--c-success)", margin: "0 0 10px" }}>
          {copyState}
        </p>
      )}
          <pre>{currentJob.log}</pre>
        </Panel>
      )}
    </div>
  );
}
