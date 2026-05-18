import { useEffect, useMemo, useState } from "react";
import Panel from "../components/Panel";
import StatusBadge from "../components/StatusBadge";
import { listCompatibilityReports, listContracts } from "../api";

export default function CompatibilityPage({ onReportsLoaded = () => {} }) {
  const [reports, setReports] = useState([]);
  const [selectedReportId, setSelectedReportId] = useState("");
  const [state, setState] = useState({ loading: false, error: "" });
  const [contractTypeByName, setContractTypeByName] = useState({});

  const sortedReports = useMemo(
    () => [...reports].sort((a, b) => Number(b.id) - Number(a.id)),
    [reports]
  );

  const selectedReport =
    sortedReports.find((r) => String(r.id) === selectedReportId) ?? sortedReports[0] ?? null;
  const findingCodes = useMemo(
    () => extractFindingCodes(selectedReport?.findings),
    [selectedReport?.findings]
  );
  const findingLabels = useMemo(
    () => findingCodes.map((code) => ({ code, label: humanizeFindingCode(code) })),
    [findingCodes]
  );
  const semverDisplay = useMemo(
    () => resolveSemverDisplay(selectedReport?.semverRecommendation, findingCodes),
    [selectedReport?.semverRecommendation, findingCodes]
  );

  async function loadReports() {
    setState({ loading: true, error: "" });
    try {
      const [data, contracts] = await Promise.all([listCompatibilityReports(), listContracts()]);
      const nextContractTypeByName = Object.fromEntries(
        contracts
          .filter((contract) => contract?.contractName && contract?.contractType)
          .map((contract) => [contract.contractName, String(contract.contractType).toLowerCase()])
      );
      setContractTypeByName(nextContractTypeByName);
      setReports(data);
      const next = [...data].sort((a, b) => Number(b.id) - Number(a.id));
      setSelectedReportId(next.length > 0 ? String(next[0].id) : "");
      onReportsLoaded(data.length > 0);
    } catch (error) {
      setState({ loading: false, error: error.message });
      onReportsLoaded(false);
      return;
    }
    setState({ loading: false, error: "" });
  }

  useEffect(() => {
    loadReports();
  }, []);

  return (
    <div className="page">
      <Panel
        title="Compatibility Check"
        description="Load reports to review the SemVer recommendation, risk level, and migration advice."
      >
        {state.error && (
          <div className="error-msg" role="alert">
            {state.error}
          </div>
        )}

        {!state.loading && !state.error && reports.length === 0 && (
          <p className="empty-state">No compatibility reports loaded yet. Click Load Reports.</p>
        )}

        {sortedReports.length > 0 && (
          <div className="report-browser">
            {/* ── List ─────────────────────────────────────── */}
            <aside className="report-list">
              {sortedReports.map((report) => (
                <button
                  key={report.id}
                  className={`report-item report-item-${resolveReportContractType(report, contractTypeByName)}${String(report.id) === String(selectedReport?.id) ? " selected" : ""}`}
                  onClick={() => setSelectedReportId(String(report.id))}
                >
                  <div className="split">
                    <strong
                      style={{
                        fontSize: "var(--text-sm)",
                        maxWidth: "220px",
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        whiteSpace: "nowrap",
                      }}
                      title={report.contractName || "Unknown contract"}
                    >
                      {report.contractName || "Unknown contract"}
                    </strong>
                    <StatusBadge value={report.level} />
                  </div>
                  <span className="meta-label">
                    Available version: {report.availableVersion ? `v${report.availableVersion}` : "—"}
                  </span>
                  <span className="meta-label">Report #{report.id}</span>
                </button>
              ))}
            </aside>

            {/* ── Detail ───────────────────────────────────── */}
            {selectedReport && (
              <article className="card report-details">
                <div className="split" style={{ marginBottom: 12 }}>
                  <h3 style={{ fontSize: "var(--text-md)" }}>Report #{selectedReport.id}</h3>
                  <StatusBadge value={selectedReport.level} className="badge-md" />
                </div>

                <div
                  style={{
                    display: "flex",
                    flexWrap: "wrap",
                    gap: 10,
                    marginBottom: 14,
                  }}
                >
                  <div className="stat-card stat-card-compact">
                    <span className="stat-label">SemVer bump</span>
                    <span
                      className={`stat-value semver-bump ${semverDisplay.className}`}
                      style={{ fontSize: "var(--text-md)" }}
                    >
                      {semverDisplay.label}
                    </span>
                  </div>

                  <div className="stat-card" style={{ flex: 1, minWidth: 220 }}>
                    <span className="stat-label">Типы изменений</span>
                    {findingLabels.length > 0 ? (
                      <div style={{ marginTop: 4, display: "flex", flexDirection: "column", gap: 4 }}>
                        {findingLabels.map((item) => (
                          <span
                            key={item.code}
                            className="stat-value"
                            title={item.code}
                            style={{ fontSize: "var(--text-sm)", lineHeight: 1.35 }}
                          >
                            {item.label}
                          </span>
                        ))}
                      </div>
                    ) : (
                      <span className="stat-value" style={{ fontSize: "var(--text-sm)", lineHeight: 1.4 }}>
                        —
                      </span>
                    )}
                  </div>
                </div>

                {selectedReport.migrationAdvice && (
                  <p
                    style={{
                      fontSize: "var(--text-sm)",
                      color: "var(--c-text-2)",
                      marginBottom: 12,
                      padding: "10px 14px",
                      background: "var(--c-surface-2)",
                      borderRadius: "var(--r-md)",
                      border: "1px solid var(--c-border)",
                    }}
                  >
                    {localizeMigrationAdvice(selectedReport.migrationAdvice)}
                  </p>
                )}

                <details>
                  <summary>
                    <span className="summary-chevron" aria-hidden="true">▸</span>
                    Findings
                  </summary>
                  <pre>{selectedReport.findings}</pre>
                </details>
              </article>
            )}
          </div>
        )}

        <p className="helper-text">
          After reviewing the recommendation, proceed to <strong>Artifacts</strong> to copy integration values.
        </p>
      </Panel>
    </div>
  );
}

function extractFindingCodes(findingsRaw) {
  if (!findingsRaw || typeof findingsRaw !== "string") {
    return [];
  }

  try {
    const parsed = JSON.parse(findingsRaw);
    if (Array.isArray(parsed)) {
      return [...new Set(parsed.map((item) => item?.code).filter(Boolean))];
    }
  } catch {
    const codes = [];
    const regex = /"code"\s*:\s*"([^"]+)"/g;
    let match = regex.exec(findingsRaw);
    while (match) {
      codes.push(match[1]);
      match = regex.exec(findingsRaw);
    }
    if (codes.length > 0) {
      return [...new Set(codes)];
    }
    if (findingsRaw.includes("Initial version")) {
      return ["INITIAL_VERSION"];
    }
    if (findingsRaw.includes("No contract changes")) {
      return ["NO_CHANGES"];
    }
  }

  return [];
}

function humanizeFindingCode(code) {
  const labels = {
    INITIAL_VERSION: "Начальная версия контракта",
    NO_CHANGES: "Изменений не обнаружено",
    OPENAPI_ENDPOINT_REMOVED: "Удален endpoint",
    OPENAPI_ENDPOINT_ADDED: "Добавлен endpoint",
    OPENAPI_SCHEMA_REMOVED: "Удалена схема",
    OPENAPI_SCHEMA_ADDED: "Добавлена схема",
    OPENAPI_REQUIRED_FIELD_ADDED: "Добавлено обязательное поле",
    OPENAPI_OPTIONAL_FIELD_ADDED: "Добавлено необязательное поле",
    OPENAPI_FIELD_REMOVED: "Удалено поле",
    OPENAPI_FIELD_TYPE_CHANGED: "Изменен тип поля",
    ASYNCAPI_CHANNEL_REMOVED: "Удален канал",
    ASYNCAPI_CHANNEL_ADDED: "Добавлен канал",
    ASYNCAPI_MESSAGE_REMOVED: "Удалено сообщение",
    ASYNCAPI_MESSAGE_ADDED: "Добавлено сообщение",
    ASYNCAPI_REQUIRED_FIELD_ADDED: "Добавлено обязательное поле",
    ASYNCAPI_OPTIONAL_FIELD_ADDED: "Добавлено необязательное поле",
    ASYNCAPI_FIELD_REMOVED: "Удалено поле",
    ASYNCAPI_FIELD_TYPE_CHANGED: "Изменен тип поля",
  };
  return labels[code] ?? code;
}

function resolveReportContractType(report, contractTypeByName) {
  const explicitType = String(report?.contractType || "").toLowerCase();
  if (explicitType === "openapi" || explicitType === "asyncapi") {
    return explicitType;
  }
  const typeFromCatalog = String(contractTypeByName?.[report?.contractName] || "").toLowerCase();
  if (typeFromCatalog === "openapi" || typeFromCatalog === "asyncapi") {
    return typeFromCatalog;
  }
  const findings = String(report?.findings || "").toUpperCase();
  if (findings.includes("ASYNCAPI_")) {
    return "asyncapi";
  }
  if (findings.includes("OPENAPI_")) {
    return "openapi";
  }
  return "";
}

function resolveSemverClass(semverRecommendation) {
  const semver = String(semverRecommendation || "").toUpperCase();
  if (semver === "MAJOR") return "semver-major";
  if (semver === "MINOR") return "semver-minor";
  if (semver === "PATCH") return "semver-patch";
  return "semver-default";
}

function resolveSemverDisplay(semverRecommendation, findingCodes) {
  if (Array.isArray(findingCodes) && findingCodes.includes("NO_CHANGES")) {
    return { label: "NO DIFF", className: "semver-no-diff" };
  }
  if (Array.isArray(findingCodes) && findingCodes.includes("INITIAL_VERSION")) {
    return { label: "INIT", className: "semver-init" };
  }
  return {
    label: String(semverRecommendation || "—"),
    className: resolveSemverClass(semverRecommendation),
  };
}

function localizeMigrationAdvice(adviceRaw) {
  const advice = String(adviceRaw || "").trim();
  if (!advice) {
    return "";
  }

  if (
    advice ===
    "Initial release detected. Publish as MINOR, announce baseline, and lock compatibility checks for the next revision."
  ) {
    return "Обнаружен первый релиз. Публикуйте как MINOR, зафиксируйте baseline и включите проверки совместимости для следующей версии.";
  }

  if (advice.startsWith("Non-breaking changes detected. Bump ")) {
    const semver = advice.match(/Bump\s+([A-Z]+)/)?.[1] ?? "MINOR";
    return `Обнаружены не ломающие изменения. Увеличьте версию на ${semver}, обновите changelog и выполните smoke-тесты потребителей перед rollout.`;
  }

  if (advice.startsWith("Breaking REST changes detected in ")) {
    const hotspots = advice
      .replace("Breaking REST changes detected in ", "")
      .replace(
        ". Publish MAJOR, keep deprecated endpoint/field aliases for one transition window, and share migration examples with consumers.",
        ""
      );
    return `Обнаружены ломающие изменения REST в: ${hotspots}. Публикуйте MAJOR, сохраните deprecated endpoint/field aliases на один переходный период и дайте потребителям примеры миграции.`;
  }

  if (advice.startsWith("Breaking event-schema changes detected in ")) {
    const hotspots = advice
      .replace("Breaking event-schema changes detected in ", "")
      .replace(
        ". Publish MAJOR, version topic/subject names, and run dual-consumer mode until all consumers are migrated.",
        ""
      );
    return `Обнаружены ломающие изменения event-schema в: ${hotspots}. Публикуйте MAJOR, версионируйте topic/subject names и используйте dual-consumer режим, пока все потребители не мигрируют.`;
  }

  return advice;
}
