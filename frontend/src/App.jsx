import { useEffect, useMemo, useState } from "react";
import ArtifactsPage from "./pages/ArtifactsPage";
import CompatibilityPage from "./pages/CompatibilityPage";
import ContractsPage from "./pages/ContractsPage";
import JobsPage from "./pages/JobsPage";

const BrandIcon = () => (
  <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
    <rect x="1" y="1" width="6" height="6" rx="1.5" fill="white" fillOpacity="0.9" />
    <rect x="9" y="1" width="6" height="6" rx="1.5" fill="white" fillOpacity="0.6" />
    <rect x="1" y="9" width="6" height="6" rx="1.5" fill="white" fillOpacity="0.6" />
    <rect x="9" y="9" width="6" height="6" rx="1.5" fill="white" fillOpacity="0.9" />
  </svg>
);

const CheckIcon = () => (
  <svg width="11" height="9" viewBox="0 0 11 9" fill="none" aria-hidden="true">
    <path d="M1 4.5L3.8 7.5L10 1.5" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

const ArrowIcon = () => (
  <svg width="12" height="12" viewBox="0 0 12 12" fill="none" aria-hidden="true">
    <path d="M2.5 6h7M6.5 3l3 3-3 3" stroke="currentColor" strokeWidth="1.5"
      strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

export default function App() {
  const [activeTab, setActiveTab] = useState("contracts");
  const [selectedContractId, setSelectedContractId] = useState("");
  const [selectedContractVersionId, setSelectedContractVersionId] = useState("");
  const [currentJob, setCurrentJob] = useState(null);
  const [compatibilityLoaded, setCompatibilityLoaded] = useState(false);
  const [artifactsLoaded, setArtifactsLoaded] = useState(false);

  const flowSteps = useMemo(
    () => [
      {
        id: "contracts",
        label: "Upload",
        hint: "Upload a contract version and select it for generation.",
        completed: Boolean(selectedContractVersionId)
      },
      {
        id: "jobs",
        label: "Generate",
        hint: "Run the generation pipeline and wait for SUCCESS status.",
        completed: currentJob?.status === "SUCCESS"
      },
      {
        id: "compatibility",
        label: "Compatibility",
        hint: "Load the compatibility report and review the SemVer recommendation.",
        completed: compatibilityLoaded
      },
      {
        id: "artifacts",
        label: "Artifacts",
        hint: "Load the read-model and copy the integration values.",
        completed: artifactsLoaded
      }
    ],
    [artifactsLoaded, compatibilityLoaded, currentJob?.status, selectedContractVersionId]
  );

  const currentStep = flowSteps.find((step) => step.id === activeTab);
  const nextStep = flowSteps.find((step) => !step.completed);
  const currentIndex = flowSteps.findIndex((s) => s.id === activeTab);

  useEffect(() => {
    setCurrentJob(null);
    setCompatibilityLoaded(false);
    setArtifactsLoaded(false);
  }, [selectedContractVersionId]);

  return (
    <div className="app-shell">
      {/* ── Header ─────────────────────────────────────────── */}
      <header className="app-header">
        <div className="app-header-inner">
          <div className="app-brand">
            <span className="app-brand-icon" aria-hidden="true">
              <BrandIcon />
            </span>
            <span className="app-brand-name">Contract Platform</span>
          </div>
        </div>
      </header>

      {/* ── Stepper ────────────────────────────────────────── */}
      <nav className="app-stepper" aria-label="Workflow steps">
        <div className="app-stepper-inner">
          {flowSteps.map((step, index) => (
            <div key={step.id} style={{ display: "contents" }}>
              {index > 0 && (
                <div
                  className={`stepper-connector${flowSteps[index - 1].completed ? " completed" : ""}`}
                />
              )}
              <button
                className={`stepper-step${activeTab === step.id ? " active" : ""}${step.completed ? " completed" : ""}`}
                onClick={() => setActiveTab(step.id)}
                aria-current={activeTab === step.id ? "step" : undefined}
                aria-label={`Step ${index + 1}: ${step.label}${step.completed ? " (completed)" : ""}`}
              >
                <span className="stepper-indicator" aria-hidden="true">
                  {step.completed ? <CheckIcon /> : index + 1}
                </span>
                <span className="stepper-label">{step.label}</span>
              </button>
            </div>
          ))}
        </div>
      </nav>

      {/* ── Hint bar ───────────────────────────────────────── */}
      {currentStep && (
        <div className="hint-bar" role="status" aria-live="polite">
          <p className="hint-bar-text">
            <strong>Step {currentIndex + 1}:</strong> {currentStep.hint}
          </p>
        </div>
      )}

      {/* ── Next step FAB ──────────────────────────────────── */}
      {nextStep && nextStep.id !== activeTab && (
        <button
          className="next-fab"
          onClick={() => setActiveTab(nextStep.id)}
          aria-label={`Go to next step: ${nextStep.label}`}
        >
          Next: {nextStep.label}
          <ArrowIcon />
        </button>
      )}

      {/* ── Page content ───────────────────────────────────── */}
      <main className="app-content" id="main-content">
        {activeTab === "contracts" && (
          <ContractsPage
            selectedContractId={selectedContractId}
            selectedContractVersionId={selectedContractVersionId}
            onSelectContract={setSelectedContractId}
            onSelectVersion={setSelectedContractVersionId}
          />
        )}
        {activeTab === "jobs" && (
          <JobsPage
            selectedContractId={selectedContractId}
            selectedContractVersionId={selectedContractVersionId}
            currentJob={currentJob}
            onJobUpdated={setCurrentJob}
          />
        )}
        {activeTab === "compatibility" && (
          <CompatibilityPage onReportsLoaded={(loaded) => setCompatibilityLoaded(loaded)} />
        )}
        {activeTab === "artifacts" && (
          <ArtifactsPage
            currentJob={currentJob}
            onReadModelLoaded={(loaded) => setArtifactsLoaded(loaded)}
          />
        )}
      </main>
    </div>
  );
}
