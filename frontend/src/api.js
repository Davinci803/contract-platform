const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "").trim();
const API_USERNAME = (import.meta.env.VITE_API_USERNAME ?? "").trim();
const API_PASSWORD = import.meta.env.VITE_API_PASSWORD ?? "";
let latestCorrelationId = "";

export const missingConfigMessage =
  "Frontend env is not configured. Set VITE_API_BASE_URL, VITE_API_USERNAME and VITE_API_PASSWORD.";

function buildUrl(path) {
  if (!API_BASE_URL) {
    throw new Error(missingConfigMessage);
  }
  return `${API_BASE_URL}${path}`;
}

function getAuthHeader() {
  if (!API_USERNAME || !API_PASSWORD) {
    throw new Error(missingConfigMessage);
  }
  return `Basic ${btoa(`${API_USERNAME}:${API_PASSWORD}`)}`;
}

async function request(path, options = {}) {
  const correlationHeader =
    latestCorrelationId && !(options.headers && "X-Correlation-Id" in options.headers)
      ? { "X-Correlation-Id": latestCorrelationId }
      : {};
  const response = await fetch(buildUrl(path), {
    ...options,
    headers: {
      Authorization: getAuthHeader(),
      ...correlationHeader,
      ...(options.headers ?? {})
    }
  });
  const responseCorrelationId = (response.headers.get("X-Correlation-Id") ?? "").trim();
  if (responseCorrelationId) {
    latestCorrelationId = responseCorrelationId;
  }

  if (!response.ok) {
    const payload = await response.json().catch(() => ({}));
    throw new Error(payload.error || `Request failed: ${response.status}`);
  }

  if (response.status === 204) {
    return null;
  }
  return response.json();
}

export function listContracts() {
  return request("/api/contracts");
}

export function uploadContractVersion(payload) {
  return request("/api/contracts/versions", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export function getContractHistory(contractId) {
  return request(`/api/contracts/${contractId}/versions`);
}

export function createGenerationJob(contractVersionId) {
  return request("/api/generation-jobs", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ contractVersionId })
  });
}

export function analyzeCompatibility(contractVersionId) {
  return request("/api/compatibility-reports/analyze", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ contractVersionId })
  });
}

export function getLatestCompatibilityReport(contractVersionId) {
  return request(`/api/compatibility-reports?contractVersionId=${encodeURIComponent(contractVersionId)}`);
}

export function getGenerationJob(jobId) {
  return request(`/api/generation-jobs/${jobId}`);
}

export function getGenerationJobByCorrelationId(correlationId) {
  return request(`/api/generation-jobs?correlationId=${encodeURIComponent(correlationId)}`);
}

export function listCompatibilityReports() {
  return request("/api/compatibility-reports");
}

export function getReadModelSummary() {
  return request("/api/read-model/summary");
}

export function listArtifacts(limit = 20, correlationId = "") {
  const correlationQuery = correlationId ? `&correlationId=${encodeURIComponent(correlationId)}` : "";
  return request(`/api/read-model/artifacts?limit=${limit}${correlationQuery}`);
}

export function listPublicationLogs(limit = 30, correlationId = "") {
  const correlationQuery = correlationId ? `&correlationId=${encodeURIComponent(correlationId)}` : "";
  return request(`/api/read-model/publication-logs?limit=${limit}${correlationQuery}`);
}

export function getLatestCorrelationId() {
  return latestCorrelationId;
}
