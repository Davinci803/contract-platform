import { useCallback, useEffect, useState } from "react";
import Panel from "../components/Panel";
import { getContractHistory, listContracts, uploadContractVersion } from "../api";

const OPENAPI_SAMPLE =
  "openapi: 3.0.1\ninfo:\n  title: Payment API\n  version: 1.0.0\npaths:\n  /api/pay:\n    get:\n      responses:\n        \"200\":\n          description: ok";

const ASYNCAPI_SAMPLE =
  "asyncapi: '2.6.0'\ninfo:\n  title: Payment Events\n  version: 1.0.0\nchannels:\n  payment.created:\n    publish:\n      message:\n        payload:\n          type: object\n          properties:\n            id:\n              type: string";

const TYPE_STYLE = {
  OPENAPI:  { background: "#dbeafe", color: "#1d4ed8", border: "1px solid #93c5fd" },
  ASYNCAPI: { background: "#ffedd5", color: "#c2410c", border: "1px solid #fdba74" },
};

const ContractTypeIcon = ({ type }) => (
  <span
    style={{
      display: "inline-block",
      fontSize: "10px",
      fontWeight: 700,
      padding: "2px 7px",
      borderRadius: "4px",
      letterSpacing: "0.04em",
      ...(TYPE_STYLE[type] ?? { background: "var(--c-surface-2)", color: "var(--c-text-2)", border: "1px solid var(--c-border)" }),
    }}
  >
    {type}
  </span>
);

const COL_CONFIG = {
  OPENAPI: {
    label: "OpenAPI",
    accent: "#1d4ed8",
    bg: "#dbeafe",
    border: "#93c5fd",
    headerBg: "#eff6ff",
  },
  ASYNCAPI: {
    label: "AsyncAPI",
    accent: "#c2410c",
    bg: "#ffedd5",
    border: "#fdba74",
    headerBg: "#fff7ed",
  },
};

function CatalogColumns({ contracts, selectedContractId, onSelectContract }) {
  const openapi  = contracts.filter((c) => c.contractType === "OPENAPI");
  const asyncapi = contracts.filter((c) => c.contractType === "ASYNCAPI");

  return (
    <div className="catalog-cols" style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
      {[
        { type: "OPENAPI",  items: openapi  },
        { type: "ASYNCAPI", items: asyncapi },
      ].map(({ type, items }) => {
        const cfg = COL_CONFIG[type];
        return (
          <div
            key={type}
            style={{
              border: `1px solid ${cfg.border}`,
              borderRadius: "var(--r-lg)",
              overflow: "hidden",
            }}
          >
            {/* Column header */}
            <div
              style={{
                background: cfg.headerBg,
                borderBottom: `1px solid ${cfg.border}`,
                padding: "8px 14px",
                display: "flex",
                alignItems: "center",
                gap: 8,
              }}
            >
              <span
                style={{
                  width: 8,
                  height: 8,
                  borderRadius: "50%",
                  background: cfg.accent,
                  flexShrink: 0,
                }}
              />
              <span style={{ fontSize: "var(--text-sm)", fontWeight: 600, color: cfg.accent }}>
                {cfg.label}
              </span>
              <span
                style={{
                  marginLeft: "auto",
                  fontSize: "var(--text-xs)",
                  fontWeight: 600,
                  color: cfg.accent,
                  background: cfg.bg,
                  border: `1px solid ${cfg.border}`,
                  borderRadius: "var(--r-full)",
                  padding: "1px 8px",
                }}
              >
                {items.length}
              </span>
            </div>

            {/* Cards */}
            <div
              style={{
                padding: 8,
                display: "flex",
                flexDirection: "column",
                gap: 6,
                maxHeight: 320,
                overflowY: "auto",
                scrollbarWidth: "thin",
                scrollbarColor: "var(--c-border) transparent",
              }}
            >
              {items.length === 0 ? (
                <p
                  style={{
                    textAlign: "center",
                    color: "var(--c-text-3)",
                    fontSize: "var(--text-sm)",
                    padding: "20px 0",
                    margin: 0,
                  }}
                >
                  No {cfg.label} contracts yet
                </p>
              ) : (
                items.map((contract) => {
                  const isSelected = String(contract.contractId) === selectedContractId;
                  return (
                    <button
                      key={contract.contractId}
                      onClick={() => onSelectContract(String(contract.contractId))}
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: 10,
                        width: "100%",
                        textAlign: "left",
                        padding: "10px 12px",
                        border: `1.5px solid ${isSelected ? cfg.accent : "var(--c-border)"}`,
                        borderRadius: "var(--r-md)",
                        background: isSelected ? cfg.bg : "var(--c-surface)",
                        color: "var(--c-text)",
                        cursor: "pointer",
                        transition: "border-color var(--t), background var(--t), box-shadow var(--t)",
                        boxShadow: isSelected ? `0 0 0 3px ${cfg.bg}` : "none",
                        margin: 0,
                        height: "auto",
                      }}
                    >
                      {/* Avatar */}
                      <span
                        style={{
                          width: 32,
                          height: 32,
                          borderRadius: "var(--r-md)",
                          background: isSelected ? cfg.accent : cfg.bg,
                          color: isSelected ? "#fff" : cfg.accent,
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "center",
                          fontWeight: 700,
                          fontSize: "var(--text-sm)",
                          flexShrink: 0,
                          transition: "background var(--t), color var(--t)",
                        }}
                        aria-hidden="true"
                      >
                        {contract.contractName.charAt(0).toUpperCase()}
                      </span>

                      <span style={{ flex: 1, minWidth: 0 }}>
                        <span
                          style={{
                            display: "block",
                            fontWeight: 600,
                            fontSize: "var(--text-sm)",
                            whiteSpace: "nowrap",
                            overflow: "hidden",
                            textOverflow: "ellipsis",
                          }}
                        >
                          {contract.contractName}
                        </span>
                        <span style={{ fontSize: "var(--text-xs)", color: "var(--c-text-3)" }}>
                          ID: {contract.contractId}
                        </span>
                      </span>

                      {isSelected && (
                        <span style={{ color: cfg.accent, fontSize: 16, flexShrink: 0 }}>✓</span>
                      )}
                    </button>
                  );
                })
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}

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
  const [uploadState, setUploadState] = useState({ loading: false, error: "", done: false });

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
    setUploadState({ loading: true, error: "", done: false });
    try {
      const uploaded = await uploadContractVersion({ name, type, content, author: "ui-user" });
      onSelectContract(String(uploaded.contractId));
      onSelectVersion(String(uploaded.contractVersionId));
      await loadContracts();
      await loadHistory(uploaded.contractId);
    } catch (error) {
      setUploadState({ loading: false, error: error.message, done: false });
      return;
    }
    setUploadState({ loading: false, error: "", done: true });
  }

  const uploadButtonTypeClass = type === "ASYNCAPI" ? "asyncapi" : "openapi";

  return (
    <div className="page">
      {/* ── Upload form ─────────────────────────────────────── */}
      <Panel
        title="Upload Contract Version"
        description="Create a new version. The uploaded version is automatically selected for the next step."
      >
        <div style={{ display: "flex", gap: 12, alignItems: "flex-end", flexWrap: "wrap" }}>
          <label style={{ flex: "1 1 240px", minWidth: 0, marginBottom: 0 }}>
            <span className="label-text">Contract name</span>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Payment API"
              style={{ maxWidth: 360 }}
            />
          </label>

          <div style={{ flexShrink: 0, marginBottom: 14 }}>
            <span className="label-text" style={{ display: "block", marginBottom: 5 }}>Type</span>
            <div style={{ display: "flex", gap: 6 }}>
              <button
                type="button"
                onClick={() => onTypeChange("OPENAPI")}
                style={{
                  height: 36,
                  padding: "0 14px",
                  border: "1.5px solid",
                  borderRadius: "var(--r-md)",
                  fontWeight: 600,
                  fontSize: "var(--text-sm)",
                  cursor: "pointer",
                  transition: "background var(--t), color var(--t), border-color var(--t)",
                  background: type === "OPENAPI" ? "#dbeafe" : "var(--c-surface)",
                  borderColor: type === "OPENAPI" ? "#3b82f6" : "var(--c-border)",
                  color: type === "OPENAPI" ? "#1d4ed8" : "var(--c-text-2)",
                }}
              >
                OpenAPI
              </button>
              <button
                type="button"
                onClick={() => onTypeChange("ASYNCAPI")}
                style={{
                  height: 36,
                  padding: "0 14px",
                  border: "1.5px solid",
                  borderRadius: "var(--r-md)",
                  fontWeight: 600,
                  fontSize: "var(--text-sm)",
                  cursor: "pointer",
                  transition: "background var(--t), color var(--t), border-color var(--t)",
                  background: type === "ASYNCAPI" ? "#ffedd5" : "var(--c-surface)",
                  borderColor: type === "ASYNCAPI" ? "#f97316" : "var(--c-border)",
                  color: type === "ASYNCAPI" ? "#c2410c" : "var(--c-text-2)",
                }}
              >
                AsyncAPI
              </button>
            </div>
          </div>
        </div>

        <label>
          <span className="label-text">Specification (YAML)</span>
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            rows={12}
            spellCheck={false}
          />
        </label>

        <div className="row" style={{ marginTop: 4 }}>
          <button
            className={`upload-action-btn ${uploadButtonTypeClass}`}
            disabled={uploadState.loading || !name.trim()}
            onClick={upload}
          >
            {uploadState.loading ? "Uploading…" : "Upload and select version"}
          </button>
        </div>

        {uploadState.error && (
          <div className="error-msg" role="alert">
            {uploadState.error}
          </div>
        )}
        {uploadState.done && !uploadState.error && (
          <div className="success-msg" role="status">
            Version uploaded and selected — proceed to Generate.
          </div>
        )}

        <p className="helper-text">
          After upload, open <strong>Generate</strong> and click <em>Generate and publish</em>.
        </p>
      </Panel>

      {/* ── Catalog ─────────────────────────────────────────── */}
      <Panel
        title="Contracts Catalog"
        description="Select a contract to load its version history."
        actions={
          <button className="secondary" disabled={listState.loading} onClick={loadContracts}>
            {listState.loading ? "Loading…" : "Refresh"}
          </button>
        }
      >
        {listState.loading && <p className="loading-text">Loading contracts…</p>}
        {listState.error && (
          <div className="error-msg" role="alert">
            {listState.error}
          </div>
        )}
        {!listState.loading && !listState.error && contracts.length === 0 && (
          <p className="empty-state">No contracts yet. Upload one above.</p>
        )}
        {contracts.length > 0 && (
          <CatalogColumns
            contracts={contracts}
            selectedContractId={selectedContractId}
            onSelectContract={onSelectContract}
          />
        )}
      </Panel>

      {/* ── Versions ─────────────────────────────────────────── */}
      <Panel
        title="Available Versions"
        description="Select the exact version to use for generation."
      >
        {!selectedContractId && (
          <p className="empty-state">Select a contract above to load its versions.</p>
        )}
        {selectedContractId && historyState.loading && (
          <p className="loading-text">Loading versions…</p>
        )}
        {historyState.error && (
          <div className="error-msg" role="alert">
            {historyState.error}
          </div>
        )}
        {selectedContractId && !historyState.loading && !historyState.error && history.length === 0 && (
          <p className="empty-state">No versions found for this contract.</p>
        )}
        {history.length > 0 && (
          <div className="cards">
            {history.map((item) => (
              <button
                key={item.contractVersionId}
                className={`button-card contract-${String(item.contractType || "").toLowerCase()}${String(item.contractVersionId) === selectedContractVersionId ? " selected" : ""}`}
                onClick={() => onSelectVersion(String(item.contractVersionId))}
              >
                <strong style={{ fontSize: "var(--text-base)" }}>v{item.version}</strong>
                <div className="meta-row">
                  <ContractTypeIcon type={item.contractType} />
                  <span className="meta-label">Version ID: {item.contractVersionId}</span>
                </div>
              </button>
            ))}
          </div>
        )}
      </Panel>
    </div>
  );
}
