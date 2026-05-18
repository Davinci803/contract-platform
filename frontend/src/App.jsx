import { useEffect, useMemo, useState } from "react";
import ArtifactsPage from "./pages/ArtifactsPage";
import CompatibilityPage from "./pages/CompatibilityPage";
import ContractsPage from "./pages/ContractsPage";
import JobsPage from "./pages/JobsPage";

const BrandIcon = () => (
  <svg width="32" height="32" viewBox="0 0 26 26" fill="none" aria-hidden="true">
    <defs>
      <linearGradient id="brandCore" x1="4" y1="4" x2="22" y2="22" gradientUnits="userSpaceOnUse">
        <stop stopColor="#60A5FA" />
        <stop offset="1" stopColor="#FB923C" />
      </linearGradient>
      <linearGradient id="brandNodeBlue" x1="0" y1="0" x2="1" y2="1">
        <stop stopColor="#93C5FD" />
        <stop offset="1" stopColor="#3B82F6" />
      </linearGradient>
      <linearGradient id="brandNodeOrange" x1="0" y1="0" x2="1" y2="1">
        <stop stopColor="#FDBA74" />
        <stop offset="1" stopColor="#F97316" />
      </linearGradient>
    </defs>
    <circle cx="13" cy="13" r="9.2" stroke="url(#brandCore)" strokeWidth="1.7" />
    <circle cx="13" cy="13" r="2.5" fill="url(#brandCore)" />
    <circle cx="13" cy="6.3" r="1.35" fill="url(#brandNodeBlue)" />
    <circle cx="18.8" cy="9.65" r="1.35" fill="url(#brandNodeOrange)" />
    <circle cx="18.8" cy="16.35" r="1.35" fill="url(#brandNodeOrange)" />
    <circle cx="13" cy="19.7" r="1.35" fill="url(#brandNodeBlue)" />
    <circle cx="7.2" cy="16.35" r="1.35" fill="url(#brandNodeBlue)" />
    <circle cx="7.2" cy="9.65" r="1.35" fill="url(#brandNodeOrange)" />
    <path
      d="M13 8.7v2.4M9.7 10.7l2.05 1.2M16.3 10.7l-2.05 1.2M13 17.3v-2.4M9.7 15.3l2.05-1.2M16.3 15.3l-2.05-1.2"
      stroke="url(#brandCore)"
      strokeWidth="1.3"
      strokeLinecap="round"
    />
  </svg>
);

const UploadIcon = () => (
  <svg width="20" height="20" viewBox="0 0 14 14" fill="none" aria-hidden="true">
    <path d="M7 10.5V3.5M7 3.5l-2.5 2.5M7 3.5l2.5 2.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M2.5 11.5h9" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
  </svg>
);

const GenerateIcon = () => (
  <svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true">
    <path
      d="M10.75 2.5a.75.75 0 00-1.5 0v1.02a6.4 6.4 0 00-1.86.77l-.72-.72a.75.75 0 00-1.06 0L4.55 4.61a.75.75 0 000 1.06l.72.72a6.4 6.4 0 00-.77 1.86H3.5a.75.75 0 000 1.5h1.02c.15.66.41 1.29.77 1.86l-.72.72a.75.75 0 000 1.06l1.06 1.06a.75.75 0 001.06 0l.72-.72c.57.36 1.2.62 1.86.77v1.02a.75.75 0 001.5 0v-1.02a6.4 6.4 0 001.86-.77l.72.72a.75.75 0 001.06 0l1.06-1.06a.75.75 0 000-1.06l-.72-.72c.36-.57.62-1.2.77-1.86h1.02a.75.75 0 000-1.5h-1.02a6.4 6.4 0 00-.77-1.86l.72-.72a.75.75 0 000-1.06l-1.06-1.06a.75.75 0 00-1.06 0l-.72.72a6.4 6.4 0 00-1.86-.77V2.5z"
      stroke="currentColor"
      strokeWidth="1.25"
      strokeLinejoin="round"
    />
    <circle cx="10" cy="10" r="2.45" stroke="currentColor" strokeWidth="1.25" />
  </svg>
);

const CompatibilityIcon = () => (
  <svg width="20" height="20" viewBox="0 0 14 14" fill="none" aria-hidden="true">
    <path
      d="M4 1.8h4.9l2.1 2.1v8.3H4V1.8z"
      stroke="currentColor"
      strokeWidth="1.3"
      strokeLinejoin="round"
    />
    <path d="M8.9 1.8V4h2.1" stroke="currentColor" strokeWidth="1.3" strokeLinejoin="round" />
    <path d="M5.5 6h4M5.5 7.9h4M5.5 9.8h2.7" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
  </svg>
);

const ArtifactsIcon = () => (
  <svg width="20" height="20" viewBox="0 0 14 14" fill="none" aria-hidden="true">
    <path d="M2.2 4.5L7 2.2l4.8 2.3L7 6.8 2.2 4.5z" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
    <path d="M2.2 4.5v5L7 11.8l4.8-2.3v-5" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
  </svg>
);

export default function App() {
  const [activeTab, setActiveTab] = useState("contracts");
  const [selectedContractId, setSelectedContractId] = useState("");
  const [selectedContractVersionId, setSelectedContractVersionId] = useState("");
  const [currentJob, setCurrentJob] = useState(null);

  const flowSteps = useMemo(
    () => [
      {
        id: "contracts",
        label: "Upload",
        icon: <UploadIcon />,
        hint: "Upload a contract version and select it for generation.",
      },
      {
        id: "jobs",
        label: "Generate",
        icon: <GenerateIcon />,
        hint: "Run the generation pipeline and wait for SUCCESS status.",
      },
      {
        id: "compatibility",
        label: "Compatibility",
        icon: <CompatibilityIcon />,
        hint: "Load the compatibility report and review the SemVer recommendation.",
      },
      {
        id: "artifacts",
        label: "Artifacts",
        icon: <ArtifactsIcon />,
        hint: "Load the read-model and copy the integration values.",
      }
    ],
    []
  );

  const currentStep = flowSteps.find((step) => step.id === activeTab);

  useEffect(() => {
    setCurrentJob(null);
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
      <nav className="app-stepper" aria-label="Main sections">
        <div className="app-stepper-inner">
          {flowSteps.map((step) => (
            <button
              key={step.id}
              className={`stepper-step${activeTab === step.id ? " active" : ""}`}
              onClick={() => setActiveTab(step.id)}
              aria-current={activeTab === step.id ? "page" : undefined}
              aria-label={step.label}
            >
              <span className="stepper-indicator" aria-hidden="true">
                {step.icon}
              </span>
              <span className="stepper-label">{step.label}</span>
            </button>
          ))}
        </div>
      </nav>

      {/* ── Hint bar ───────────────────────────────────────── */}
      {currentStep && (
        <div className="hint-bar" role="status" aria-live="polite">
          <p className="hint-bar-text">
            <strong>{currentStep.label}:</strong> {currentStep.hint}
          </p>
        </div>
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
          <CompatibilityPage />
        )}
        {activeTab === "artifacts" && (
          <ArtifactsPage currentJob={currentJob} />
        )}
      </main>
    </div>
  );
}
