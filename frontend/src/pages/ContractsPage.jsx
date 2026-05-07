import { useCallback, useEffect, useState } from "react";
import Panel from "../components/Panel";
import { getContractHistory, listContracts, uploadContractVersion } from "../api";

const OPENAPI_SAMPLE =
  "openapi: 3.0.1\ninfo:\n  title: Payment API\n  version: 1.0.0\npaths:\n  /api/pay:\n    get:\n      responses:\n        \"200\":\n          description: ok";

const ASYNCAPI_SAMPLE =
  "asyncapi: '2.6.0'\ninfo:\n  title: Payment Events\n  version: 1.0.0\nchannels:\n  payment.created:\n    publish:\n      message:\n        payload:\n          type: object\n          properties:\n            id:\n              type: string";

export default function ContractsPage({
  selectedContractId,
  selectedContractVersionId,
  onSelectContract,
  onSelectVersion
}) {
  const [name, setName] = useState("Payment Contract");
  const [type, setType] = useState("OPENAPI");
  const [content, setContent] = useState(OPENAPI_SAMPLE);

  const [contracts, setContracts] = useState([]);
  const [history, setHistory] = useState([]);
  const [listState, setListState] = useState({ loading: false, error: "" });
  const [historyState, setHistoryState] = useState({ loading: false, error: "" });
  const [uploadState, setUploadState] = useState({ loading: false, error: "" });

  function onTypeChange(nextType) {
    setType(nextType);
    setContent(nextType === "OPENAPI" ? OPENAPI_SAMPLE : ASYNCAPI_SAMPLE);
  }

  const loadContracts = useCallback(async () => {
    setListState({ loading: true, error: "" });
    try {
      const data = await listContracts();
      setContracts(data);
    } catch (error) {
      setListState({ loading: false, error: error.message });
      return;
    }
    setListState({ loading: false, error: "" });
  }, []);

  const loadHistory = useCallback(
    async (contractId) => {
    setHistoryState({ loading: true, error: "" });
    try {
      const data = await getContractHistory(contractId);
      setHistory(data);
      if (data.length > 0) {
        onSelectVersion(String(data[0].contractVersionId));
      }
    } catch (error) {
      setHistoryState({ loading: false, error: error.message });
      return;
    }
    setHistoryState({ loading: false, error: "" });
    },
    [onSelectVersion]
  );

  useEffect(() => {
    loadContracts();
  }, [loadContracts]);

  useEffect(() => {
    if (!selectedContractId) {
      setHistory([]);
      return;
    }
    loadHistory(selectedContractId);
  }, [selectedContractId, loadHistory]);

  async function upload() {
    setUploadState({ loading: true, error: "" });
    try {
      const uploaded = await uploadContractVersion({ name, type, content, author: "ui-user" });
      onSelectContract(String(uploaded.contractId));
      onSelectVersion(String(uploaded.contractVersionId));
      await loadContracts();
      await loadHistory(uploaded.contractId);
    } catch (error) {
      setUploadState({ loading: false, error: error.message });
      return;
    }
    setUploadState({ loading: false, error: "" });
  }

  return (
    <div className="page">
      <Panel
        title="Upload Contract Version"
        description="Create a new contract version directly from UI."
      >
        <label>
          Name
          <input value={name} onChange={(event) => setName(event.target.value)} />
        </label>
        <label>
          Type
          <select value={type} onChange={(event) => onTypeChange(event.target.value)}>
            <option value="OPENAPI">OPENAPI</option>
            <option value="ASYNCAPI">ASYNCAPI</option>
          </select>
        </label>
        <label>
          Specification
          <textarea
            value={content}
            onChange={(event) => setContent(event.target.value)}
            rows={14}
          />
        </label>
        <button disabled={uploadState.loading} onClick={upload}>
          {uploadState.loading ? "Uploading..." : "Upload Version"}
        </button>
        {uploadState.error && <p className="error">{uploadState.error}</p>}
      </Panel>

      <Panel
        title="Contracts"
        description="Select contract to inspect its version history."
        actions={
          <button className="secondary" disabled={listState.loading} onClick={loadContracts}>
            Refresh
          </button>
        }
      >
        {listState.loading && <p className="muted">Loading contracts...</p>}
        {listState.error && <p className="error">{listState.error}</p>}
        {!listState.loading && !listState.error && contracts.length === 0 && (
          <p className="muted">No contracts yet.</p>
        )}
        {contracts.length > 0 && (
          <div className="cards">
            {contracts.map((contract) => (
              <button
                key={contract.contractId}
                className={`card button-card ${
                  String(contract.contractId) === selectedContractId ? "selected" : ""
                }`}
                onClick={() => onSelectContract(String(contract.contractId))}
              >
                <strong>{contract.contractName}</strong>
                <span>{contract.contractType}</span>
                <small>ID: {contract.contractId}</small>
              </button>
            ))}
          </div>
        )}
      </Panel>

      <Panel
        title="Version History"
        description="Latest version is preselected for generation."
      >
        {!selectedContractId && <p className="muted">Select contract to load history.</p>}
        {selectedContractId && historyState.loading && <p className="muted">Loading history...</p>}
        {historyState.error && <p className="error">{historyState.error}</p>}
        {selectedContractId && !historyState.loading && !historyState.error && history.length === 0 && (
          <p className="muted">No versions found.</p>
        )}
        {history.length > 0 && (
          <div className="cards">
            {history.map((item) => (
              <button
                key={item.contractVersionId}
                className={`card button-card ${
                  String(item.contractVersionId) === selectedContractVersionId ? "selected" : ""
                }`}
                onClick={() => onSelectVersion(String(item.contractVersionId))}
              >
                <strong>v{item.version}</strong>
                <span>{item.contractType}</span>
                <small>Version ID: {item.contractVersionId}</small>
              </button>
            ))}
          </div>
        )}
      </Panel>
    </div>
  );
}
