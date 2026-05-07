import { useState } from "react";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "").trim();
const API_USERNAME = (import.meta.env.VITE_API_USERNAME ?? "").trim();
const API_PASSWORD = import.meta.env.VITE_API_PASSWORD ?? "";

const missingConfigMessage =
  "Frontend env is not configured. Set VITE_API_BASE_URL, VITE_API_USERNAME and VITE_API_PASSWORD.";

function getAuthHeader() {
  if (!API_USERNAME || !API_PASSWORD) {
    return null;
  }
  return `Basic ${btoa(`${API_USERNAME}:${API_PASSWORD}`)}`;
}

function buildApiUrl(path) {
  if (!API_BASE_URL) {
    return null;
  }
  return `${API_BASE_URL}${path}`;
}

export default function App() {
  const [name, setName] = useState("Payment Contract");
  const [type, setType] = useState("OPENAPI");
  const [content, setContent] = useState("openapi: 3.0.1\npaths:\n  /api/pay:\n    get: {}");
  const [contractVersionId, setContractVersionId] = useState(null);
  const [jobId, setJobId] = useState(null);
  const [jobStatus, setJobStatus] = useState("N/A");
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState("");

  async function upload() {
    setError("");
    const url = buildApiUrl("/api/contracts/versions");
    const authHeader = getAuthHeader();
    if (!url || !authHeader) {
      setError(missingConfigMessage);
      return;
    }

    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: authHeader
      },
      body: JSON.stringify({ name, type, content, author: "ui-user" })
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      setError(body.error || `Upload failed: ${response.status}`);
      return;
    }
    const body = await response.json();
    setContractVersionId(body.contractVersionId);
  }

  async function generate() {
    if (!contractVersionId) return;
    setError("");
    const url = buildApiUrl("/api/generation-jobs");
    const authHeader = getAuthHeader();
    if (!url || !authHeader) {
      setError(missingConfigMessage);
      return;
    }

    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: authHeader
      },
      body: JSON.stringify({ contractVersionId })
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      setError(body.error || `Generation failed: ${response.status}`);
      return;
    }
    const body = await response.json();
    setJobId(body.jobId);
    setJobStatus(body.status);
  }

  async function refreshJob() {
    if (!jobId) return;
    setError("");
    const url = buildApiUrl(`/api/generation-jobs/${jobId}`);
    const authHeader = getAuthHeader();
    if (!url || !authHeader) {
      setError(missingConfigMessage);
      return;
    }

    const response = await fetch(url, { headers: { Authorization: authHeader } });
    if (!response.ok) return;
    const body = await response.json();
    setJobStatus(body.status);
  }

  async function loadSummary() {
    setError("");
    const url = buildApiUrl("/api/read-model/summary");
    const authHeader = getAuthHeader();
    if (!url || !authHeader) {
      setError(missingConfigMessage);
      return;
    }

    const response = await fetch(url, { headers: { Authorization: authHeader } });
    if (!response.ok) return;
    setSummary(await response.json());
  }

  return (
    <main>
      <h1>Contract Platform Demo</h1>
      <section>
        <h2>Upload Contract Version</h2>
        <label>
          Name
          <input value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <label>
          Type
          <select value={type} onChange={(e) => setType(e.target.value)}>
            <option value="OPENAPI">OPENAPI</option>
            <option value="ASYNCAPI">ASYNCAPI</option>
          </select>
        </label>
        <label>
          Specification
          <textarea value={content} onChange={(e) => setContent(e.target.value)} rows={12} />
        </label>
        <button onClick={upload}>Upload Version</button>
        <p>contractVersionId: {contractVersionId ?? "-"}</p>
      </section>

      <section>
        <h2>Generate & Publish</h2>
        <button onClick={generate} disabled={!contractVersionId}>Generate & Publish</button>
        <button onClick={refreshJob} disabled={!jobId}>Refresh Job Status</button>
        <p>jobId: {jobId ?? "-"}</p>
        <p>jobStatus: {jobStatus}</p>
      </section>

      <section>
        <h2>Artifacts Summary</h2>
        <button onClick={loadSummary}>Load Summary</button>
        <pre>{summary ? JSON.stringify(summary, null, 2) : "No summary loaded"}</pre>
      </section>

      {error && <p className="error">{error}</p>}
    </main>
  );
}
