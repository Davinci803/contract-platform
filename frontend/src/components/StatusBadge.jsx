const STATUS_CLASS = {
  SUCCESS: "ok",
  RUNNING: "info",
  PENDING: "warn",
  FAILED: "err",
  COMPATIBLE: "ok",
  INCOMPATIBLE: "err"
};

export default function StatusBadge({ value }) {
  if (!value) {
    return <span className="badge">N/A</span>;
  }
  const normalized = String(value).toUpperCase();
  const cls = STATUS_CLASS[normalized] ?? "neutral";
  return <span className={`badge ${cls}`}>{value}</span>;
}
