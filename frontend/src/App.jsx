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

  const tabs = useMemo(
    () => [
      { id: "contracts", label: "Contracts & Versions" },
      { id: "jobs", label: "Generation Job" },
      { id: "compatibility", label: "Compatibility" },
      { id: "artifacts", label: "Artifacts & Publications" }
    ],
    []
  );

  return (
    <main>
      <h1>Contract Platform UI Flow</h1>
      <p className="muted">
        End-to-end demo: contracts, version history, compatibility advice, job timeline, and
        publication artifacts.
      </p>
      <nav className="tabs">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            className={activeTab === tab.id ? "tab active" : "tab"}
            onClick={() => setActiveTab(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </nav>

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
      {activeTab === "compatibility" && <CompatibilityPage />}
      {activeTab === "artifacts" && <ArtifactsPage />}
    </main>
  );
}
