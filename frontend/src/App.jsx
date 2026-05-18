import { useMemo, useState } from "react";
import ArtifactsPage from "./pages/ArtifactsPage";
import CompatibilityPage from "./pages/CompatibilityPage";
import ContractsPage from "./pages/ContractsPage";
import JobsPage from "./pages/JobsPage";

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
        hint: "Upload a contract version and choose one for generation.",
        completed: Boolean(selectedContractVersionId)
      },
      {
        id: "jobs",
        label: "Generate",
        hint: "Run generation pipeline and wait for SUCCESS.",
        completed: currentJob?.status === "SUCCESS"
      },
      {
        id: "compatibility",
        label: "Compatibility",
        hint: "Load compatibility report and check SemVer recommendation.",
        completed: compatibilityLoaded
      },
      {
        id: "artifacts",
        label: "Artifacts",
        hint: "Load read-model and copy integration values.",
        completed: artifactsLoaded
      }
    ],
    [artifactsLoaded, compatibilityLoaded, currentJob?.status, selectedContractVersionId]
  );
  const currentStep = flowSteps.find((step) => step.id === activeTab);
  const nextStep = flowSteps.find((step) => !step.completed);

  return (
    <main>
      <h1>Contract Platform Guided Demo</h1>
      <p className="muted">
        Follow the flow from upload to copied artifact values. Each step shows what to do next.
      </p>
      <section className="panel flow-panel">
        <h2>Scenario Progress</h2>
        <div className="flow-steps">
          {flowSteps.map((step, index) => (
            <button
              key={step.id}
              className={`flow-step ${activeTab === step.id ? "active" : ""} ${
                step.completed ? "done" : ""
              }`}
              onClick={() => setActiveTab(step.id)}
            >
              <span>{index + 1}</span>
              <strong>{step.label}</strong>
            </button>
          ))}
        </div>
        <p className="muted flow-hint">
          {currentStep?.hint}
          {nextStep && (
            <>
              {" "}
              Next recommended action: open <strong>{nextStep.label}</strong>.
            </>
          )}
        </p>
        {nextStep && (
          <button className="secondary" onClick={() => setActiveTab(nextStep.id)}>
            Go to next step: {nextStep.label}
          </button>
        )}
      </section>
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
  );
}
